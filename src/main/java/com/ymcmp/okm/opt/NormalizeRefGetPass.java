package com.ymcmp.okm.opt;

import java.util.List;

import com.ymcmp.okm.tac.Value;
import com.ymcmp.okm.tac.Operation;
import com.ymcmp.okm.tac.Statement;

public final class NormalizeRefGetPass implements Pass {

    @Override
    public void process(final String fname, final List<Statement> block) {
        handleJumpRange(fname, block, this::normalize);
    }

    private void normalize(final String fname, final List<Statement> block) {
        for (int i = 0; i < block.size() - 1; ++i) {
            final Statement stmt = block.get(i);
            final Statement next = block.get(i + 1);

            if (stmt.op == Operation.POINTER_GET && next.op == Operation.REFER_VAR) {
                // 0 POINTER_GET    %T0, $pointer
                // 1 REFER_VAR      %T1, %T0
                // => 1 is really just STORE_VAR    %T1, $pointer
                if (safeEquals(stmt.dst, next.lhs)) {
                    final Statement move = new Statement(Operation.STORE_VAR, stmt.lhs, next.dst);
                    move.setDataSize(64);   // $pointer must be a pointer, which is 64 bits

                    block.set(i + 1, move);
                    --i;
                    continue;
                }
            }

            if (stmt.op == Operation.REFER_VAR && next.op == Operation.POINTER_GET) {
                // 0 REFER_VAR      %T0, $scalar
                // 1 POINTER_GET    %T1, %T0
                // => 1 is really just STORE_VAR    %T1, $scalar
                if (safeEquals(stmt.dst, next.lhs)) {
                    final Statement move = new Statement(Operation.STORE_VAR, stmt.lhs, next.dst);
                    move.setDataSize(next.getDataSize());

                    block.set(i + 1, move);
                    --i;
                    continue;
                }
            }
        }
    }

    private static boolean safeEquals(Value a, Value b) {
        return a == null ? false : a.equals(b);
    }
}