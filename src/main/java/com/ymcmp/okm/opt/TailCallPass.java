package com.ymcmp.okm.opt;

import java.util.List;

import com.ymcmp.okm.tac.Value;
import com.ymcmp.okm.tac.Operation;
import com.ymcmp.okm.tac.Statement;

public final class TailCallPass implements Pass {

    @Override
    public void process(final String name, final List<Statement> block) {
        for (int i = 0; i < block.size() - 1; ++i) {
            final Statement stmt = block.get(i);
            if (stmt.op == Operation.CALL) {
                final int nextAddr = getNextNonGotoOpAddress(block, i);
                if (nextAddr < block.size()) {
                    final Statement next = block.get(nextAddr);
                    if (next.op == Operation.RETURN_VALUE && safeEquals(stmt.dst, next.dst)) {
                        // Next statement returns the result of this statement
                        block.set(i + 1, new Statement(Operation.NOP));
                        block.set(i--, new Statement(Operation.TAILCALL, stmt.lhs));
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