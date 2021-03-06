package com.ymcmp.okm.type;

import java.util.Map;
import java.util.Arrays;
import java.util.LinkedHashMap;

import java.util.stream.Collectors;

import com.ymcmp.okm.except.DuplicateSymbolException;
import com.ymcmp.okm.except.UndefinedOperationException;

public final class StructType extends AllocTable {

    private static final long serialVersionUID = 912387547L;

    private final boolean allocated;

    public StructType() {
        this(new LinkedHashMap<>(), false);
    }

    private StructType(LinkedHashMap<String, Type> fields, boolean allocate) {
        super(fields);
        this.allocated = allocate;
    }

    @Override
    public StructType allocate() {
        if (allocated) {
            throw new UndefinedOperationException("Cannot allocate non-struct type");
        }
        return new StructType(fields, true);
    }

    @Override
    public boolean isSameType(Type t) {
        if (t instanceof StructType) {
            // These two types are equivalent:
            //   struct Color4f(r, b, g, a :float)
            //   struct Vector4f(w, x, y, z :float)

            final StructType other = (StructType) t;
            final Type[] expect = this.fields.values().toArray(new Type[0]);
            final Type[] actual = other.fields.values().toArray(new Type[0]);
            return Arrays.equals(expect, actual);
        }
        return false;
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
        return allocated ? accessAttribute(attr) : null;
    }

    @Override
    public String toString() {
        return fields.entrySet().stream()
                .map(e -> e.getKey() + " :" + e.getValue())
                .collect(Collectors.joining(", ", "struct(", ")"));
    }
}