package com.ymcmp.okm.opt;

import java.util.List;
import java.util.ArrayList;

import com.ymcmp.okm.tac.Label;
import com.ymcmp.okm.tac.Value;
import com.ymcmp.okm.tac.Operation;
import com.ymcmp.okm.tac.Statement;

public final class EliminateNopPass implements Pass {

    @Override
    public void process(final String fname, final List<Statement> block) {
        final ArrayList<Label> labels = new ArrayList<>();

        // Acquire all the labels
        for (int i = 0; i < block.size(); ++i) {
            final Statement stmt = block.get(i);

            final Label dst = toLabel(stmt.dst);
            final Label lhs = toLabel(stmt.lhs);
            final Label rhs = toLabel(stmt.rhs);

            if (dst != null) labels.add(dst);
            if (lhs != null) labels.add(lhs);
            if (rhs != null) labels.add(rhs);
        }

        // Eliminate unreachable code (convert them to NOPs)
        // In the mean time, eliminate NOPs and update label addresses
        outer:
        for (int i = 0; i < block.size(); ++i) {
            final Statement stmt = block.get(i);
            switch (stmt.op) {
                case GOTO: {
                    // Example:
                    // 0 %T0 <- STORE_VAR $fptrA_0
                    // 1 GOTO (3)
                    // 2 %T0 <- STORE_VAR $fptrB_0
                    // 3 $callsite_1 <- STORE_VAR %T0
                    // 4 %T1 <- CALL $callsite_1
                    // Inclusive (1, 2) is unreachable given no code jumps to (2)
                    final int dst = ((Label) stmt.dst).getAddress();
                    if (dst == i + 1) {
                        // Special case, just eliminate the GOTO statement
                        block.set(i--, new Statement(Operation.NOP));
                    } else if (dst > i) {
                        purgeUnreachedCode(block, labels, i, dst);
                    }
                    continue;
                }
                case RETURN_UNIT:
                case RETURN_INT:
                case RETURN_FLOAT:
                    // Special case of GOTO's example: everything after this
                    // instruction can be purged
                    purgeUnreachedCode(block, labels, i, block.size());
                    continue;
                case NOP: {
                    for (final Label label : labels) {
                        final int addr = label.getAddress();
                        if (addr > i) label.setAddress(addr - 1);
                    }
                    block.remove(i--);
                    continue;
                }
            }
        }
    }

    private void purgeUnreachedCode(final List<Statement> block, final List<Label> labels, final int start, final int maxWipeRange) {
        int wipeOut = maxWipeRange;
        for (final Label label : labels) {
            final int addr = label.getAddress();
            if (start < addr && addr < wipeOut) {
                // Code is still reachable beyond addr,
                // discard everything up until that point
                wipeOut = addr;
            }
        }
        // Code range is unreachable. Wipe them
        wipeRange(block, start + 1, wipeOut);
    }

    private void wipeRange(final List<Statement> block, final int start, final int end) {
        for (int i = start; i < end; ++i) {
            block.set(i, new Statement(Operation.NOP));
        }
    }

    private Label toLabel(final Value val) {
        return val == null ? null : val.getClass() == Label.class ? (Label) val : null;
    }
}