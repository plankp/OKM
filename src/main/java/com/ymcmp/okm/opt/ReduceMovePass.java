package com.ymcmp.okm.opt;

import java.util.List;

import com.ymcmp.okm.tac.Value;
import com.ymcmp.okm.tac.Operation;
import com.ymcmp.okm.tac.Statement;

public final class ReduceMovePass implements Pass {

    @Override
    public void process(final String fname, final List<Statement> block) {
        for (int i = 0; i < block.size() - 1; ++i) {
            final Statement stmt = block.get(i);
            switch (stmt.op) {
                case CALL: {
                    // CALL is often followed by POP_PARAM before reaching a STORE_VAR
                    final int stmtLoc = i;
                    Statement next;
                    do {
                        next = block.get(++i);
                    } while (next.op == Operation.POP_PARAM);

                    if (next.op == Operation.STORE_VAR) {
                        // Remove the STORE_VAR statement
                        block.set(i, new Statement(Operation.NOP));
                        // Convert the CALL statement's dst to STORE_VAR statement's
                        block.set(stmtLoc, new Statement(stmt.op, stmt.lhs, stmt.rhs, next.dst));
                        // Restore $i
                        i = stmtLoc - 1;
                        continue;
                    }
                    break;
                }
                case STORE_VAR: {
                    // Remove this statement only if source and dest is the same
                    if (safeEquals(stmt.dst, stmt.lhs)) {
                        block.set(i--, new Statement(Operation.NOP));
                        continue;
                    }
                    break;
                }
                default: {
                    final Statement next = block.get(i + 1);
                    // Remove the STORE_VAR statement if using the same register
                    if (next.op == Operation.STORE_VAR && safeEquals(stmt.dst, next.lhs)) {
                        block.set(i + 1, new Statement(Operation.NOP));
                        // Convert the CALL statement's dst to STORE_VAR statement's
                        block.set(i--, new Statement(stmt.op, stmt.lhs, stmt.rhs, next.dst));
                        continue;
                    }
                }
            }
        }
    }

    private static boolean safeEquals(Value a, Value b) {
        return a == null ? false : a.equals(b);
    }
}