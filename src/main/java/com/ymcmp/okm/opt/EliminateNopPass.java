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

        // Eliminate NOPs and update label address
        for (int i = 0; i < block.size(); ++i) {
            final Statement stmt = block.get(i);
            if (stmt.op == Operation.NOP) {
                for (final Label label : labels) {
                    final int addr = label.getAddress();
                    if (addr > i) label.setAddress(addr - 1);
                }
                block.remove(i--);
                continue;
            }
        }
    }

    private Label toLabel(final Value val) {
        return val == null ? null : val.getClass() == Label.class ? (Label) val : null;
    }
}