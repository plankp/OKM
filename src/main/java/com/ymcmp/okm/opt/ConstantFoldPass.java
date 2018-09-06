package com.ymcmp.okm.opt;

import java.util.List;
import java.util.HashMap;

import com.ymcmp.okm.tac.Value;
import com.ymcmp.okm.tac.Fixnum;
import com.ymcmp.okm.tac.Operation;
import com.ymcmp.okm.tac.Statement;

public final class ConstantFoldPass implements Pass {

    @Override
    public void process(final String fname, final List<Statement> block) {
        final HashMap<String, Value> replacement = new HashMap<>();
        for (int i = 0; i < block.size(); ++i) {
            final Statement stmt = block.get(i);

            if (stmt.op == Operation.LOAD_NUMERAL && stmt.dst.isTemporary()) {
                // Remove temporaries
                replacement.put(stmt.dst.toString(), stmt.lhs);
                block.set(i--, new Statement(Operation.NOP));
                continue;
            }

            if (stmt.op == Operation.LOAD_NUMERAL) {
                if (safeIsNumeric(stmt.lhs)) {
                    // Save these results, can perform substitution
                    replacement.put(stmt.dst.toString(), stmt.lhs);
                } else {
                    replacement.remove(stmt.dst.toString());
                }
            }

            final Value newLhs = replacement.getOrDefault(safeToString(stmt.lhs), stmt.lhs);
            final Value newRhs = replacement.getOrDefault(safeToString(stmt.rhs), stmt.rhs);
            if (newLhs != stmt.lhs || newRhs != stmt.rhs) {
                // Insert the removed temporaries as constants
                block.set(i--, new Statement(stmt.op, newLhs, newRhs, stmt.dst));
                continue;
            }

            switch (stmt.op) {
                case PUSH_PARAM:
                case RETURN_VALUE: {
                    final Value newDst = replacement.getOrDefault(safeToString(stmt.dst), stmt.dst);
                    if (newDst != stmt.dst) {
                        block.set(i--, new Statement(stmt.op, stmt.lhs, stmt.rhs, newDst));
                        continue;
                    }
                }
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
                        case BINARY_ADD: result = a + b; break;
                        case BINARY_SUB: result = a - b; break;
                        case BINARY_MUL: result = a * b; break;
                        case BINARY_DIV: result = a / b; break;
                        case BINARY_MOD: result = a % b; break;
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
                        case UNARY_ADD: result = +a; break;
                        case UNARY_SUB: result = -a; break;
                        case UNARY_TILDA: result = ~a; break;
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
}