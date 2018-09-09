package com.ymcmp.okm.type;

import java.util.HashMap;

import com.ymcmp.okm.except.DuplicateSymbolException;

public final class EnumKeyType implements Type {

    private static final UnaryType TYPE_INT = UnaryType.getType("int");

    public final String name;

    public EnumKeyType(String name) {
        this.name = name;
    }

    @Override
    public boolean isSameType(Type t) {
        if (t instanceof EnumType) {
            return name.equals(((EnumType) t).name);
        }
        if (t instanceof EnumKeyType) {
            return name.equals(((EnumKeyType) t).name);
        }
        return false;
    }

    @Override
    public boolean canConvertTo(Type t) {
        // It acts like an int, ints do not act like enums though!
        return this.isSameType(t) || TYPE_INT.canConvertTo(t);
    }

    @Override
    public Type tryPerformCall(Type... args) {
        // Act like an int
        return TYPE_INT.tryPerformCall(args);
    }

    @Override
    public Type tryPerformUnary(UnaryOperator op) {
        // Act like an int
        return TYPE_INT.tryPerformUnary(op);
    }

    @Override
    public Type tryPerformBinary(BinaryOperator op, Type rhs) {
        // Act like an int
        return TYPE_INT.tryPerformBinary(op, rhs);
    }

    @Override
    public Type tryAccessAttribute(String attr) {
        // Act like an int
        return TYPE_INT.tryAccessAttribute(attr);
    }

    @Override
    public String toString() {
        return "enum value " + name;
    }
}