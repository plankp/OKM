package com.ymcmp.okm.tac;

import java.io.Serializable;

public final class Statement implements Serializable {

    private static final long serialVersionUID = 47297568346L;

    public final Operation op;
    public final Value lhs;
    public final Value rhs;
    public final Value dst;

    private int dataSize;

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
        this.lhs = safeUnwrapMutable(lhs);
        this.rhs = safeUnwrapMutable(rhs);
        this.dst = safeUnwrapMutable(dst);
    }

    private static Value safeUnwrapMutable(final Value val) {
        if (val instanceof Mutable) {
            return ((Mutable) val).getValue();
        }
        return val;
    }

    public void setDataSize(int size) {
        this.dataSize = size;
    }

    public int getDataSize() {
        return this.dataSize;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder().append(op);
        if (dataSize > 0)   sb.append(':').append(dataSize / Byte.SIZE);
        if (dst != null)    sb.append(' ').append(dst);
        if (lhs != null)    sb.append(',').append(lhs);
        if (rhs != null)    sb.append(',').append(rhs);
        return sb.toString();
    }
}