package com.ymcmp.okm.opt;

import java.util.List;

import com.ymcmp.okm.tac.Label;
import com.ymcmp.okm.tac.Value;
import com.ymcmp.okm.tac.Fixnum;
import com.ymcmp.okm.tac.Operation;
import com.ymcmp.okm.tac.Statement;

public final class TailCallPass implements Pass {

    @Override
    public void process(final String name, final List<Statement> block) {
        for (int i = 0; i < block.size() - 1; ++i) {
            final Statement stmt = block.get(i);
            switch (stmt.op) {
                case CALL: {
                    final int nextAddr = getNextNonGotoOpAddress(block, i);
                    if (nextAddr < block.size()) {
                        final Statement next = block.get(nextAddr);
                        if ((next.op == Operation.RETURN_INT || next.op == Operation.RETURN_FLOAT) && safeEquals(stmt.dst, next.dst)) {
                            // Next statement returns the result of this statement
                            block.set(i + 1, new Statement(Operation.NOP));
                            block.set(i--, new Statement(Operation.TAILCALL, stmt.lhs));
                            continue;
                        }
                    }
                    break;
                }
                case CALL_UNIT: {
                    final int nextAddr = getNextNonGotoOpAddress(block, i);
                    if (nextAddr < block.size()) {
                        final Statement next = block.get(nextAddr);
                        if (next.op == Operation.RETURN_UNIT) {
                            // Next statement returns the unit,
                            // which is same as the return value of CALL_UNIT
                            block.set(i + 1, new Statement(Operation.NOP));
                            block.set(i--, new Statement(Operation.TAILCALL, stmt.dst));
                            continue;
                        }
                    }
                    break;
                }
                case JUMP_IF_TRUE:
                    if (stmt.lhs.isNumeric()) {
                        final Fixnum f = (Fixnum) stmt.lhs;
                        final Statement newStmt;
                        if (f.isInt ? Long.parseLong(f.value) == 0 : Double.parseDouble(f.value) == 0) {
                            // This jump never happens, convert to nop
                            newStmt = new Statement(Operation.NOP);
                        } else {
                            // This jump always happens, use goto instead
                            newStmt = new Statement(Operation.GOTO, stmt.dst);
                        }
                        block.set(i--, newStmt);
                        continue;
                    }
                    break;
                case JUMP_IF_FALSE:
                    if (stmt.lhs.isNumeric()) {
                        final Fixnum f = (Fixnum) stmt.lhs;
                        final Statement newStmt;
                        if (f.isInt ? Long.parseLong(f.value) != 0 : Double.parseDouble(f.value) != 0) {
                            // This jump never happens, convert to nop
                            newStmt = new Statement(Operation.NOP);
                        } else {
                            // This jump always happens, use goto instead
                            newStmt = new Statement(Operation.GOTO, stmt.dst);
                        }
                        block.set(i--, newStmt);
                        continue;
                    }
                    break;
                case GOTO: {
                    final int originalDst = ((Label) stmt.dst).getAddress();
                    final int nextAddr = getNextNonGotoOpAddress(block, originalDst - 1);
                    if (nextAddr < block.size()) {
                        final Statement next = block.get(nextAddr);
                        switch (next.op) {
                            case RETURN_UNIT:
                            case RETURN_INT:
                            case RETURN_FLOAT:
                                // Instead of goto a return, just return directly
                                block.set(i--, next);
                                break;
                            default:
                                if (nextAddr != originalDst) {
                                    // Instead of goto a goto, just use a singular goto
                                    block.set(i--, new Statement(Operation.GOTO, new Label(nextAddr)));
                                }
                                break;
                        }
                        continue;
                    }
                    break;
                }
            }
        }
    }

    private static boolean safeEquals(Value a, Value b) {
        return a == null ? false : a.equals(b);
    }
}