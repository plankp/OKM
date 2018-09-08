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

    @Override
    public void process(final String fname, final List<Statement> block) {
        int startIdx = 0;
        for (int i = 0; i < block.size(); ++i) {
            // Consider follow code fragment
            //   0 POP_PARAM $a_0
            //   1 (4) <- JUMP_IF_TRUE $a_0
            //   2 %T0 <- LOAD_NUMERAL 10
            //   3 GOTO (5)
            //   4 %T0 <- LOAD_NUMERAL 11
            //   5 RETURN_VALUE %T0
            // Realize the jumps have to taken into account when folding constants.
            // If ignore jumps, RETURN_VALUE will be fixed with either 10 or 11,
            // which is incorrect (since it ignores $a_0 JUMP_IF_TRUE judgement).
            //
            // Correct handling will be to process in (inclusive) (0, 1), (1, 3),
            // (3, 5) and (5, 5). When processing, these will use different
            // replacement maps. Also, that means LOAD* cannot eagerly dispose
            // themselves. That has to be done separately.

            final Statement stmt = block.get(i);
            if (stmt.op.branches()) {
                // Analyze on inclusive range startIdx and stmt's instruction
                final int endIdx = i + 1;
                unfoldConstants(fname, block.subList(startIdx, endIdx));
                startIdx = endIdx;

                if (stmt.op == Operation.GOTO) {
                    final int brAddr = ((Label) stmt.dst).getAddress();
                    if (brAddr >= endIdx) {
                        // Analyze on inclusive range startIdx and brAddr
                        unfoldConstants(fname, block.subList(startIdx, brAddr));
                        i = (startIdx = brAddr) - 1;
                    }
                }
            }
        }
    }

    private void unfoldConstants(final String fname, final List<Statement> block) {
        final HashMap<String, Value> replacement = new HashMap<>();
        for (int i = 0; i < block.size(); ++i) {
            final Statement stmt = block.get(i);

            // Try substitute temporaries
            if (safeIsTemporary(stmt.dst)) {
                switch (stmt.op) {
                    case LOAD_NUMERAL:
                        replacement.put(stmt.dst.toString(), stmt.lhs);
                        continue;
                    case LOAD_TRUE:
                        replacement.put(stmt.dst.toString(), Fixnum.TRUE);
                        continue;
                    case LOAD_FALSE:
                        replacement.put(stmt.dst.toString(), Fixnum.FALSE);
                        continue;
                }
            }

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
                            final Value newValue = ((Fixnum) stmt.lhs).changeSize(getResultSize(stmt.op));
                            replacement.put(stmt.dst.toString(), newValue);
                            final Statement newStmt;
                            if (stmt.dst.isTemporary()) {
                                newStmt = new Statement(Operation.NOP);
                            } else {
                                newStmt = new Statement(Operation.LOAD_NUMERAL, newValue, stmt.dst);
                            }
                            block.set(i--, newStmt);
                            continue;
                        }
                        break;
                    case CONV_INT_FLOAT:
                    case CONV_LONG_FLOAT:
                    case CONV_INT_DOUBLE:
                    case CONV_LONG_DOUBLE:
                        if (safeIsNumeric(stmt.lhs)) {
                            final Value newValue = new Fixnum(((Fixnum) stmt.lhs).value + ".0", getResultSize(stmt.op));
                            replacement.put(stmt.dst.toString(), newValue);
                            final Statement newStmt;
                            if (stmt.dst.isTemporary()) {
                                newStmt = new Statement(Operation.NOP);
                            } else {
                                newStmt = new Statement(Operation.LOAD_NUMERAL, newValue, stmt.dst);
                            }
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
                            block.set(i--, new Statement(stmt.op, stmt.lhs, stmt.rhs, newDst));
                            continue;
                        }
                        // This block will undo the previous substitution
                        // because register is modified and cached value is wrong
                        replacement.remove(stmt.dst.toString());
                    }
            }

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
                block.set(i--, new Statement(op, newLhs, newRhs, stmt.dst));
                continue;
            }

            // Ignore all float pointer optimizations!

            if (safeIsNumeric(stmt.lhs) && safeIsNumeric(stmt.rhs)) {
                final Fixnum lhs = (Fixnum) stmt.lhs;
                final Fixnum rhs = (Fixnum) stmt.rhs;
                if (lhs.isInt && rhs.isInt) {
                    final long a = Long.parseLong(lhs.value);
                    final long b = Long.parseLong(rhs.value);
                    final long result;
                    switch (stmt.op) {
                        case INT_ADD: case LONG_ADD: result = a + b; break;
                        case INT_SUB: case LONG_SUB: result = a - b; break;
                        case INT_MUL: case LONG_MUL: result = a * b; break;
                        case INT_DIV: case LONG_DIV: result = a / b; break;
                        case INT_MOD: case LONG_MOD: result = a % b; break;
                        default:
                            // Not optimizable, not an error, just ignore
                            continue;
                    }
                    final int newSize = lhs.size < rhs.size ? rhs.size : lhs.size;
                    block.set(i--, new Statement(Operation.LOAD_NUMERAL, new Fixnum(result, newSize), stmt.dst));
                }
                continue;
            }

            if (safeIsNumeric(stmt.lhs)) {
                final Fixnum lhs = (Fixnum) stmt.lhs;
                if (lhs.isInt) {
                    final long a = Long.parseLong(lhs.value);
                    final long result;
                    switch (stmt.op) {
                        case INT_NEG: case LONG_NEG: result = -a; break;
                        case INT_CPL: case LONG_CPL: result = ~a; break;
                        default:
                            // Not optimizable, not an error, just ignore
                            continue;
                    }
                    block.set(i--, new Statement(Operation.LOAD_NUMERAL, new Fixnum(result, lhs.size), stmt.dst));
                }
                continue;
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