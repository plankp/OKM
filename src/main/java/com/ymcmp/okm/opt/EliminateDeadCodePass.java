package com.ymcmp.okm.opt;

import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import com.ymcmp.okm.tac.Value;
import com.ymcmp.okm.tac.Register;
import com.ymcmp.okm.tac.Operation;
import com.ymcmp.okm.tac.Statement;

public final class EliminateDeadCodePass implements Pass {

    @Override
    public void process(final String fname, final List<Statement> block) {
        // Gather all local registers used as dst
        final HashMap<Register, ArrayList<Integer>> temps = new HashMap<>();
        for (int i = 0; i < block.size(); ++i) {
            final Statement stmt = block.get(i);
            if (!stmt.op.hasPotentialSideEffects() && safeIsRegister(stmt.dst)) {
                final Register dst = (Register) stmt.dst;
                if (dst.toString().charAt(0) == '@') {
                    // Global registers definitely cause side effects
                    // they do not qualify as dead code
                    continue;
                }
                ArrayList<Integer> addrs = temps.get(dst);
                if (addrs == null) {
                    temps.put(dst, addrs = new ArrayList<>());
                }
                addrs.add(i);
            }
        }

        // Check if those registers are referenced as lhs or rhs (or dst for return and push)
        // Remove them from the map if they are referenced
        for (int i = 0; i < block.size(); ++i) {
            final Statement stmt = block.get(i);
            if (temps.containsKey(stmt.lhs)) temps.remove(stmt.lhs);
            if (temps.containsKey(stmt.rhs)) temps.remove(stmt.rhs);
            if (stmt.op.readsFromDst() && temps.containsKey(stmt.dst)) temps.remove(stmt.dst);
        }

        // The ones remaining in the map are useless, set them as NOPs in code block
        temps.values().stream()
                .flatMap(ArrayList::stream)
                .mapToInt(e -> e)
                .forEach(addr -> block.set(addr, new Statement(Operation.NOP)));
    }

    private static boolean safeIsRegister(final Value val) {
        return val == null ? false : val.getClass() == Register.class;
    }
}