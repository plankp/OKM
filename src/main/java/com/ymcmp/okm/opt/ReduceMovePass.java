package com.ymcmp.okm.opt;

import java.util.List;

import com.ymcmp.okm.tac.Value;
import com.ymcmp.okm.tac.Operation;
import com.ymcmp.okm.tac.Statement;

public final class ReduceMovePass implements Pass {

    @Override
    public void process(final String fname, final List<Statement> block) {
        handleJumpRange(fname, block, this::reduceMoves);
    }

    private void reduceMoves(final String fname, final List<Statement> block) {
        for (int i = 0; i < block.size() - 1; ++i) {
            final Statement stmt = block.get(i);
            if (stmt.op == Operation.STORE_VAR) {
                // Remove this statement only if source and dest is the same
                if (safeEquals(stmt.dst, stmt.lhs)) {
                    block.set(i--, new Statement(Operation.NOP));
                    continue;
                }
            }

            final Statement next = block.get(i + 1);
            // Remove the STORE_VAR statement if using the same register
            if (next.op == Operation.STORE_VAR && safeEquals(stmt.dst, next.lhs)) {
                if (stmt.dst.isTemporary()) {
                    block.set(i + 1, new Statement(Operation.NOP));
                    // Convert the CALL statement's dst to STORE_VAR statement's
                    final Statement repl = new Statement(stmt.op, stmt.lhs, stmt.rhs, next.dst);
                    repl.setDataSize(stmt.getDataSize());
                    block.set(i--, repl);
                    continue;
                }
            }
        }
    }

    private static boolean safeEquals(Value a, Value b) {
        return a == null ? false : a.equals(b);
    }
}