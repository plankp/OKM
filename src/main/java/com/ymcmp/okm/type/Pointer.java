package com.ymcmp.okm.type;

public final class Pointer implements Type {

    public final Type inner;

    public Pointer(Type inner) {
        this.inner = inner;
    }

    @Override
    public int getSize() {
        // Must be same as funcType's
        return 64;
    }

    @Override
    public boolean isSameType(Type t) {
        if (t instanceof Pointer) {
            return inner.isSameType(((Pointer) t).inner);
        }
        return false;
    }

    @Override
    public boolean canConvertTo(Type t) {
        // If type a is nested by N pointers,
        // it can convert to a
        return isSameType(t) || inner.canConvertTo(t);
    }

    @Override
    public Pointer allocate() {
        return new Pointer(inner.allocate());
    }

    @Override
    public Type tryPerformCall(Type... args) {
        return null;
    }

    @Override
    public Type tryPerformUnary(UnaryOperator op) {
        // Unlike in C, no pointer arithemetic
        return null;
    }

    @Override
    public Type tryPerformBinary(BinaryOperator op, Type rhs) {
        // Unlike in C, no pointer arithemetic
        return null;
    }

    @Override
    public Type tryAccessAttribute(String attr) {
        return null;
    }

    @Override
    public String toString() {
        return "pointer to " + inner;
    }
}