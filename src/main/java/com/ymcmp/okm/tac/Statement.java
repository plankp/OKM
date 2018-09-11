package com.ymcmp.okm.tac;

import java.io.Serializable;

public final class Statement implements Serializable {

    private static final long serialVersionUID = 47297568346L;

    public final Operation op;
    public final Value lhs;
    public final Value rhs;
    public final Value dst;

    public Statement(Operation op) {
        this(op, null, null, null);
    }

    public Statement(Operation op, Value dst) {
        this(op, null, null, dst);
    }

    public Statement(Operation op, Value value, Value dst) {
        this(op, value, null, dst);
    }

    public Statement(Operation op, Value lhs, Value rhs, Value dst) {
        this.op = op;
        this.lhs = lhs;
        this.rhs = rhs;
        this.dst = dst;
    }

    @Override
    public String toString() {
        return dst == null ? op.toString()
             : lhs == null ? op + " " + dst
             : rhs == null ? op + " " + dst + ", " + lhs
             : op + " " + dst + ", " + lhs + ", " + rhs;
    }
}