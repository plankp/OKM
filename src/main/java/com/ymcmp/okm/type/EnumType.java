package com.ymcmp.okm.type;

import java.util.HashMap;

import com.ymcmp.okm.except.DuplicateSymbolException;

public final class EnumType implements Type {

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

    @Override
    public int getSize() {
        // Must be same as EnumKeyType's size
        return 32;
    }

    @Override
    public Type allocate() {
        return this;
    }

    public String[] getKeys() {
        return KEYS.get(name);
    }

    public EnumKeyType createCorrespondingKey() {
        return new EnumKeyType(name);
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
        return this.isSameType(t);
    }

    @Override
    public Type tryPerformCall(Type... args) {
        return null;
    }

    @Override
    public Type tryPerformUnary(UnaryOperator op) {
        return null;
    }

    @Override
    public Type tryPerformBinary(BinaryOperator op, Type rhs) {
        return null;
    }

    @Override
    public Type tryAccessAttribute(String attr) {
        // <Enum type>.<key name>
        final String[] keys = getKeys();
        for (final String key : keys) {
            if (key.equals(attr)) {
                return createCorrespondingKey();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "enum " + name;
    }
}