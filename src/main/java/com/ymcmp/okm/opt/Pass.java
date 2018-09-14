package com.ymcmp.okm.opt;

import java.util.List;

import java.util.function.BiConsumer;

import com.ymcmp.okm.tac.Label;
import com.ymcmp.okm.tac.Operation;
import com.ymcmp.okm.tac.Statement;

public interface Pass {

    public void process(String funcName, List<Statement> block);

    public default void reset() {
        // Do nothing
    }

    public default int getNextNonGotoOpAddress(final List<Statement> block, final int currentAddr) {
        int addr = currentAddr + 1;
        while (addr < block.size()) {
            final Statement stmt = block.get(addr);
            if (stmt.op == Operation.GOTO) {
                addr = ((Label) stmt.dst).getAddress();
                continue;
            }
            break;
        }
        return addr;
    }

    public default void handleJumpRange(final String fname, final List<Statement> block, final BiConsumer<String, List<Statement>> bicons) {
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
                bicons.accept(fname, block.subList(startIdx, endIdx));
                startIdx = endIdx;

                if (stmt.op == Operation.GOTO) {
                    final int brAddr = ((Label) stmt.dst).getAddress();
                    if (brAddr >= endIdx) {
                        // Analyze on inclusive range startIdx and brAddr
                        bicons.accept(fname, block.subList(startIdx, brAddr));
                        i = (startIdx = brAddr) - 1;
                    }
                }
            }
        }

        if (startIdx == 0) {
            // That means there were not jumps, analyze the entire block
            bicons.accept(fname, block);
        }
    }
}