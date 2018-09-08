package com.ymcmp.okm.type;

import java.util.HashMap;

import com.ymcmp.okm.except.DuplicateSymbolException;

public final class EnumType implements Type {

    private static final UnaryType TYPE_INT = UnaryType.getType("int");
    private static final HashMap<String, String[]> KEYS = new HashMap<>();

    public final String name;

    public EnumType(String name) {
        this.name = name;
    }

    public static EnumType makeEnum(String name, String... keys) {
        if (KEYS.containsKey(name)) {
            throw new DuplicateSymbolException("enum " + name);
        }
        KEYS.put(name, keys);
        return new EnumType(name);
    }

    public String[] getKeys() {
        return KEYS.get(name);
    }

    @Override
    public boolean isSameType(Type t) {
        if (t instanceof EnumType) {
            return name.equals(((EnumType) t).name);
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
        // <Enum type>.<key name>
        final String[] keys = getKeys();
        for (final String key : keys) {
            if (key.equals(attr)) {
                return this;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return name;
    }
}