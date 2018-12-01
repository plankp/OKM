package com.ymcmp.okm.type;

import java.util.Map;
import java.util.Arrays;
import java.util.LinkedHashMap;

import java.util.stream.Collectors;

import com.ymcmp.okm.except.DuplicateSymbolException;
import com.ymcmp.okm.except.UndefinedOperationException;

public final class ClassType extends AllocTable {

    private static final long serialVersionUID = -3391645467L;

    private final String name;

    private final boolean allocated;

    // to get the offset of vtable pointer, it is
    // BASE + getSize() - 64
    // (essentially tacked on to the end of the struct)

    public ClassType(String name) {
        this(name, new LinkedHashMap<>(), false);
    }

    private ClassType(String name, LinkedHashMap<String, Type> fields, boolean allocate) {
        super(fields);
        this.name = name;
        this.allocated = allocate;
    }

    @Override
    public ClassType allocate() {
        if (allocated) {
            throw new UndefinedOperationException("Cannot allocate non-class type");
        }
        return new ClassType(name, fields, true);
    }

    @Override
    public int getSize() {
        // + 64 because of pointer to vtable
        return super.getSize() + 64;
    }

    @Override
    public boolean isSameType(Type t) {
        if (t instanceof ClassType) {
            return name.equals(((ClassType) t).name);
        }
        return false;
    }

    @Override
    public boolean canConvertTo(Type t) {
        // Could extend to struct types as well
        return isSameType(t);
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
                .collect(Collectors.joining(", ", "class(", ")"));
    }
}