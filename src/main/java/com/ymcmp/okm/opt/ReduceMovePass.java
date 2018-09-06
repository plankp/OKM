package com.ymcmp.okm.opt;

import java.util.List;

import com.ymcmp.okm.tac.Value;
import com.ymcmp.okm.tac.Operation;
import com.ymcmp.okm.tac.Statement;

public final class ReduceMovePass implements Pass {

    @Override
    public void process(final String fname, final List<Statement> block) {
        for (int i = 0; i < block.size() - 1; ++i) {
            final Statement stmt = block.get(i);
            switch (stmt.op) {
                case LOAD_TRUE:
                case LOAD_FALSE:
                case UNARY_ADD:
                case UNARY_SUB:
                case UNARY_NOT:
                case UNARY_TILDA:
                case BINARY_ADD:
                case BINARY_SUB:
                case BINARY_MUL:
                case BINARY_DIV:
                case BINARY_MOD:
                case BINARY_LESSER_THAN:
                case BINARY_GREATER_THAN:
                case BINARY_LESSER_EQUALS:
                case BINARY_GREATER_EQUALS:
                case BINARY_EQUALS:
                case BINARY_NOT_EQUALS:
                case CALL: {
                    final Statement next = block.get(i + 1);
                    if (next.op == Operation.STORE_VAR) {
                        // Remove the STORE_VAR statement
                        block.set(i + 1, new Statement(Operation.NOP));
                        // Convert the CALL statement's dst to STORE_VAR statement's
                        block.set(i--, new Statement(stmt.op, stmt.lhs, stmt.rhs, next.dst));
                        continue;
                    }
                }
            }
        }
    }
}