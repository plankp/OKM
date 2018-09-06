package com.ymcmp.okm.type;

public interface Type {

    public boolean isSameType(Type t);

    public default boolean canConvertTo(Type t) {
        // If types are same, guarantee convertible
        return isSameType(t);
    }

    // Return null if call cannot be done
    public Type tryPerformCall(Type... args);

    // Return null if op cannot be done
    public Type tryPerformUnary(UnaryOperator op);

    // Return null if op cannot be done
    public Type tryPerformBinary(BinaryOperator op, Type rhs);
}