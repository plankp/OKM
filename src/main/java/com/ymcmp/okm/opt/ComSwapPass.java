package com.ymcmp.okm.opt;

import java.util.List;

import com.ymcmp.okm.tac.Value;
import com.ymcmp.okm.tac.Operation;
import com.ymcmp.okm.tac.Statement;

public final class ComSwapPass implements Pass {

    @Override
    public void process(final String fname, final List<Statement> block) {
        handleJumpRange(fname, block, this::commutativeSwap);
    }

    private void commutativeSwap(final String fname, final List<Statement> block) {
        // Only perform swap if
        // - it makes a temporary closer to it
        // - if there are no temporaries but makes a variable closer to it
        Value lastDst = null;
        for (int i = 0; i < block.size(); ++i) {
            final Statement stmt = block.get(i);
            if (stmt.op.isCommutative() && stmt.rhs.equals(lastDst)) {
                // op1 -> a
                // op2 -> b
                // op3 a, b -> c
                // If op3 is commutative, we swap a and b
                final Statement reorg = new Statement(stmt.op, stmt.rhs, stmt.lhs, stmt.dst);
                reorg.setDataSize(stmt.getDataSize());
                block.set(i, reorg);
            }
            if (!stmt.op.readsFromDst()) {
                lastDst = stmt.dst;
            }
        }
    }
}