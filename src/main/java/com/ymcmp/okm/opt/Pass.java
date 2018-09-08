package com.ymcmp.okm.opt;

import java.util.List;

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
}