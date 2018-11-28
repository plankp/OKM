package com.ymcmp.okm.opt;

import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.ArrayList;

import com.ymcmp.okm.tac.Value;
import com.ymcmp.okm.tac.Register;
import com.ymcmp.okm.tac.Operation;
import com.ymcmp.okm.tac.Statement;

public final class TempParamPass implements Pass {

    @Override
    public void process(final String fname, final List<Statement> block) {
        final HashMap<Value, ArrayList<Integer>> affectedOffsets = new HashMap<>();
        for (int i = 0; i < block.size(); ++i) {
            final Statement stmt = block.get(i);
            switch (stmt.op) {
                case POP_PARAM_INT:
                case POP_PARAM_FLOAT:
                    affectedOffsets.put(stmt.dst, new ArrayList<>(Arrays.asList(i)));
                    break;
                default:
                    if (affectedOffsets.containsKey(stmt.dst)) {
                        if (stmt.op.readsFromDst()) {
                            affectedOffsets.get(stmt.dst).add(i);
                        } else {
                            // Parameter slot is mutated later on meaning
                            // it cannot be converted to a temporary
                            affectedOffsets.remove(stmt.dst);
                        }
                    }
                    if (affectedOffsets.containsKey(stmt.lhs)) {
                        affectedOffsets.get(stmt.lhs).add(i);
                    }
                    if (affectedOffsets.containsKey(stmt.rhs)) {
                        affectedOffsets.get(stmt.rhs).add(i);
                    }
                    break;
            }
        }

        // If we introduce the idea of a null register, the output code
        // could be even more efficient (space-wise)
        for (final Map.Entry<Value, ArrayList<Integer>> pair : affectedOffsets.entrySet()) {
            final ArrayList<Integer> offsets = pair.getValue();
            switch (offsets.size()) {
                case 1: {
                    // The offset is just POP_PARAM_*
                    // change it so dst is null
                    final int offset = offsets.get(0);
                    final Statement stmt = block.get(offset);
                    block.set(offset, new Statement(stmt.op, null));
                    break;
                }
                case 2: {
                    // The offsets are POP_PARAM_* and another instruction
                    final Value key = pair.getKey();
                    final Register newLoc = Register.makeTemporary();
                    for (final int offset : offsets) {
                        final Statement stmt = block.get(offset);
                        final Statement conv = new Statement(stmt.op,
                                key.equals(stmt.lhs) ? newLoc : stmt.lhs,
                                key.equals(stmt.rhs) ? newLoc : stmt.rhs,
                                key.equals(stmt.dst) ? newLoc : stmt.dst);
                        conv.setDataSize(stmt.getDataSize());
                        block.set(offset, conv);
                    }
                    break;
                }
            }
        }
    }
}