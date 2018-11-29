package com.ymcmp.okm.opt;

import java.util.List;

import com.ymcmp.okm.tac.Value;
import com.ymcmp.okm.tac.Operation;
import com.ymcmp.okm.tac.Statement;

public final class SquashCmpPass implements Pass {

    @Override
    public void process(final String fname, final List<Statement> block) {
        handleJumpRange(fname, block, this::squashComparisons);
    }

    private void squashComparisons(final String fname, final List<Statement> block) {
        for (int i = 0; i < block.size() - 1; ++i) {
            final Statement stmt = block.get(i);
            final Statement next = block.get(i + 1);

            Operation synthOp = getEquivalentCmpJmp(stmt.op);
            if (synthOp == null) {
                // Not eligible, skip
                continue;
            }

            switch (next.op) {
                case JUMP_IF_TRUE:
                    break;
                case JUMP_IF_FALSE:
                    synthOp = synthOp.getCommutativePair();
                    break;
                default:
                    continue;
            }

            block.set(i, new Statement(Operation.NOP));
            block.set(i + 1, new Statement(synthOp, stmt.lhs, stmt.rhs, next.dst));
        }
    }

    private static boolean safeEquals(Value a, Value b) {
        return a == null ? false : a.equals(b);
    }

    private static Operation getEquivalentCmpJmp(final Operation op) {
        switch (op) {
            case INT_LT:
                return Operation.JUMP_INT_LT;
            case INT_GT:
                return Operation.JUMP_INT_GT;
            case INT_LE:
                return Operation.JUMP_INT_LE;
            case INT_GE:
                return Operation.JUMP_INT_GE;
            case INT_EQ:
                return Operation.JUMP_INT_EQ;
            case INT_NE:
                return Operation.JUMP_INT_NE;
            default:
                return null;
        }
    }
}