package com.ymcmp.okm.opt;

import java.util.List;
import java.util.HashMap;

import com.ymcmp.okm.tac.Label;
import com.ymcmp.okm.tac.Value;
import com.ymcmp.okm.tac.Fixnum;
import com.ymcmp.okm.tac.Register;
import com.ymcmp.okm.tac.Operation;
import com.ymcmp.okm.tac.Statement;

public final class ConstantFoldPass implements Pass {

    private final HashMap<String, Value> replacement = new HashMap<>();

    @Override
    public void reset() {
        replacement.clear();
    }

    @Override
    public void process(final String fname, final List<Statement> block) {
        populateReplacement(block);
        handleJumpRange(fname, block, this::unfoldConstants);
    }

    private void populateReplacement(final List<Statement> block) {
        for (int i = 0; i < block.size(); ++i) {
            final Statement stmt = block.get(i);

            if (stmt.dst instanceof Register) {
                switch (stmt.op) {
                    case CONV_BYTE_INT:
                    case CONV_SHORT_INT:
                    case CONV_LONG_INT:
                    case CONV_INT_BYTE:
                    case CONV_INT_SHORT:
                    case CONV_INT_LONG:
                    case CONV_FLOAT_DOUBLE:
                        if (safeIsNumeric(stmt.lhs)) {
                            final int newSize = getResultSize(stmt.op);
                            final Value newValue = ((Fixnum) stmt.lhs).changeSize(newSize);
                            replacement.put(stmt.dst.toString(), newValue);

                            final Statement newStmt = new Statement(Operation.LOAD_NUMERAL, newValue, stmt.dst);
                            newStmt.setDataSize(newSize);
                            block.set(i--, newStmt);
                            continue;
                        }
                        break;
                    case CONV_INT_FLOAT:
                    case CONV_LONG_FLOAT:
                    case CONV_INT_DOUBLE:
                    case CONV_LONG_DOUBLE:
                        if (safeIsNumeric(stmt.lhs)) {
                            final int newSize = getResultSize(stmt.op);
                            final Value newValue = new Fixnum(((Fixnum) stmt.lhs).value + ".0", newSize);
                            replacement.put(stmt.dst.toString(), newValue);

                            final Statement newStmt = new Statement(Operation.LOAD_NUMERAL, newValue, stmt.dst);
                            newStmt.setDataSize(newSize);
                            block.set(i--, newStmt);
                            continue;
                        }
                        break;
                }
            }

            // Attempt to perform substitution
            switch (stmt.op) {
                case LOAD_NUMERAL:
                    if (safeIsNumeric(stmt.lhs)) replacement.put(stmt.dst.toString(), stmt.lhs);
                    break;
                case LOAD_TRUE:
                    replacement.put(stmt.dst.toString(), Fixnum.TRUE);
                    break;
                case LOAD_FALSE:
                    replacement.put(stmt.dst.toString(), Fixnum.FALSE);
                    break;
                default:
                    if (replacement.containsKey(safeToString(stmt.dst))) {
                        if (stmt.op.readsFromDst()) {
                            final Value newDst = replacement.get(stmt.dst.toString());
                            final Statement repl = new Statement(stmt.op, stmt.lhs, stmt.rhs, newDst);
                            repl.setDataSize(stmt.getDataSize());
                            block.set(i--, repl);
                            continue;
                        }
                        // This block will undo the previous substitution
                        // because register is modified and cached value is wrong
                        replacement.remove(stmt.dst.toString());
                    } else if (stmt.op == Operation.REFER_VAR && replacement.containsKey(safeToString(stmt.lhs))) {
                        // If folding was allowed, it will be creating a pointer out
                        // of a non-memory-address (which is wrong!)
                        //
                        // Disable folding completely in this case since pointers can
                        // be iffy when it comes to predicting values.
                        replacement.remove(stmt.lhs.toString());
                    }
            }
        }
    }

    private void unfoldConstants(final String fname, final List<Statement> block) {
        for (int i = 0; i < block.size(); ++i) {
            final Statement stmt = block.get(i);

            final Value newLhs = replacement.getOrDefault(safeToString(stmt.lhs), stmt.lhs);
            final Value newRhs = replacement.getOrDefault(safeToString(stmt.rhs), stmt.rhs);
            if (newLhs != stmt.lhs || newRhs != stmt.rhs) {
                // Insert the removed temporaries as constants
                Operation op = stmt.op;
                if (op == Operation.STORE_VAR) {
                    // STORE_VAR works with two registers
                    // Convert to LOAD_NUMERAL
                    op = Operation.LOAD_NUMERAL;
                }
                final Statement repl = new Statement(op, newLhs, newRhs, stmt.dst);
                repl.setDataSize(stmt.getDataSize());
                block.set(i--, repl);
                continue;
            }

            // Ignore all float pointer optimizations!

            if (safeIsNumeric(stmt.lhs) && safeIsNumeric(stmt.rhs)) {
                final Fixnum lhs = (Fixnum) stmt.lhs;
                final Fixnum rhs = (Fixnum) stmt.rhs;
                if (lhs.isInt && rhs.isInt) {
                    final long a = Long.parseLong(lhs.value);
                    final long b = Long.parseLong(rhs.value);
                    int newSize = lhs.size < rhs.size ? rhs.size : lhs.size;

                    final long result;
                    switch (stmt.op) {
                        case INT_ADD: case LONG_ADD: result = a + b; break;
                        case INT_SUB: case LONG_SUB: result = a - b; break;
                        case INT_MUL: case LONG_MUL: result = a * b; break;
                        case INT_DIV: case LONG_DIV: result = a / b; break;
                        case INT_MOD: case LONG_MOD: result = a % b; break;
                        case INT_LT:   newSize = Byte.SIZE; result = a < b ? 1 : 0; break;
                        case INT_GT:   newSize = Byte.SIZE; result = a > b ? 1 : 0; break;
                        case INT_LE:   newSize = Byte.SIZE; result = a <= b ? 1 : 0; break;
                        case INT_GE:   newSize = Byte.SIZE; result = a <= b ? 1 : 0; break;
                        case INT_EQ:   newSize = Byte.SIZE; result = a == b ? 1 : 0; break;
                        case INT_NE:   newSize = Byte.SIZE; result = a != b ? 1 : 0; break;
                        case INT_CMP:  newSize = Byte.SIZE; result = Integer.compare((int) a, (int) b); break;
                        case LONG_CMP: newSize = Byte.SIZE; result = Long.compare(a, b); break;
                        default:
                            // Not optimizable, not an error, just ignore
                            continue;
                    }
                    final Statement repl = new Statement(Operation.LOAD_NUMERAL, new Fixnum(result, newSize), stmt.dst);
                    repl.setDataSize(newSize);
                    block.set(i--, repl);
                }
                continue;
            }

            if (safeIsNumeric(stmt.lhs)) {
                final Fixnum lhs = (Fixnum) stmt.lhs;
                if (lhs.isInt) {
                    final long a = Long.parseLong(lhs.value);
                    Statement subst = null;
                    switch (stmt.op) {
                        case INT_ADD: case LONG_ADD:
                            if (a == 0) {
                                subst = new Statement(Operation.STORE_VAR, stmt.rhs, stmt.dst);
                            }
                            break;
                        case INT_SUB:
                            if (a == 0) {
                                subst = new Statement(Operation.INT_NEG, stmt.rhs, stmt.dst);
                            }
                            break;
                        case LONG_SUB:
                            if (a == 0) {
                                subst = new Statement(Operation.LONG_NEG, stmt.rhs, stmt.dst);
                            }
                            break;
                        case INT_MUL: case LONG_MUL:
                            if (a == 0) {
                                subst = new Statement(Operation.LOAD_NUMERAL, new Fixnum(0, lhs.size), stmt.dst);
                            }
                            break;
                        case INT_DIV: case LONG_DIV:
                            if (a == 0) {
                                subst = new Statement(Operation.LOAD_NUMERAL, new Fixnum(0, lhs.size), stmt.dst);
                            }
                            break;
                        case INT_MOD: case LONG_MOD:
                            if (a == 0) {
                                subst = new Statement(Operation.LOAD_NUMERAL, new Fixnum(0, lhs.size), stmt.dst);
                            }
                            break;
                        case INT_NEG: case LONG_NEG:
                            subst = new Statement(Operation.LOAD_NUMERAL, new Fixnum(-a, lhs.size), stmt.dst);
                            break;
                        case INT_CPL: case LONG_CPL:
                            subst = new Statement(Operation.LOAD_NUMERAL, new Fixnum(~a, lhs.size), stmt.dst);
                            break;
                    }

                    if (subst != null) {
                        subst.setDataSize(stmt.getDataSize());
                        block.set(i--, subst);
                    }
                }
                continue;
            }

            if (safeIsNumeric(stmt.rhs)) {
                final Fixnum rhs = (Fixnum) stmt.rhs;
                if (rhs.isInt) {
                    final long a = Long.parseLong(rhs.value);
                    Statement subst = null;
                    if (a == 0) {
                        switch (stmt.op) {
                            case INT_ADD: case LONG_ADD:
                                subst = new Statement(Operation.STORE_VAR, stmt.lhs, stmt.dst);
                                break;
                            case INT_SUB: case LONG_SUB:
                                subst = new Statement(Operation.STORE_VAR, stmt.lhs, stmt.dst);
                                break;
                            case INT_MUL: case LONG_MUL:
                                subst = new Statement(Operation.LOAD_NUMERAL, new Fixnum(0, rhs.size), stmt.dst);
                                break;
                            case INT_DIV: case LONG_DIV:
                            case INT_MOD: case LONG_MOD:
                                throw new RuntimeException("Divide by zero on " + stmt);
                        }
                    } else if (a == 1) {
                        switch (stmt.op) {
                            case INT_MUL: case LONG_MUL:
                                subst = new Statement(Operation.STORE_VAR, stmt.lhs, stmt.dst);
                                break;
                            case INT_DIV: case LONG_DIV:
                                subst = new Statement(Operation.STORE_VAR, stmt.lhs, stmt.dst);
                                break;
                            case INT_MOD: case LONG_MOD:
                                subst = new Statement(Operation.LOAD_NUMERAL, new Fixnum(0, rhs.size), stmt.dst);
                                break;
                        }
                    }

                    if (subst != null) {
                        subst.setDataSize(stmt.getDataSize());
                        block.set(i--, subst);
                    }
                }
            }
        }
    }

    private static String safeToString(final Object obj) {
        return obj == null ? null : obj.toString();
    }

    private static boolean safeIsNumeric(final Value val) {
        return val == null ? false : val.isNumeric();
    }

    private static boolean safeIsTemporary(final Value val) {
        return val == null ? false : val.isTemporary();
    }

    private static int getResultSize(final Operation op) {
        switch (op) {
            case CONV_BYTE_INT:     return Integer.SIZE;
            case CONV_SHORT_INT:    return Integer.SIZE;
            case CONV_LONG_INT:     return Integer.SIZE;
            case CONV_INT_BYTE:     return Byte.SIZE;
            case CONV_INT_SHORT:    return Short.SIZE;
            case CONV_INT_LONG:     return Long.SIZE;
            case CONV_INT_FLOAT:    return Float.SIZE;
            case CONV_LONG_FLOAT:   return Float.SIZE;
            case CONV_INT_DOUBLE:   return Double.SIZE;
            case CONV_LONG_DOUBLE:  return Double.SIZE;
            case CONV_FLOAT_DOUBLE: return Double.SIZE;
            default:
                throw new AssertionError("Invalid casting operation " + op);
        }
    }
}