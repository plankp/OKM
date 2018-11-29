package com.ymcmp.okm.type;

import java.util.Map;
import java.util.LinkedHashMap;

import com.ymcmp.okm.except.DuplicateSymbolException;
import com.ymcmp.okm.except.UndefinedOperationException;

public final class StructType implements Type {

    private static final long serialVersionUID = 912387547L;

    public final String name;

    private final boolean allocated;
    private final LinkedHashMap<String, Type> fields;

    public StructType(String name) {
        this(name, new LinkedHashMap<>(), false);
    }

    private StructType(String name, LinkedHashMap<String, Type> fields, boolean allocate) {
        this.name = name;
        this.fields = fields;
        this.allocated = allocate;
    }

    @Override
    public StructType allocate() {
        if (allocated) {
            throw new UndefinedOperationException("Cannot allocate non-struct type " + name);
        }
        return new StructType(name, fields, true);
    }

    @Override
    public int getSize() {
        return fields.values().stream()
                .mapToInt(Type::getSize)
                .sum();
    }

    public void putField(String name, Type type) {
        if (fields.containsKey(name)) {
            throw new DuplicateSymbolException(name);
        }
        fields.put(name, type);
    }

    @Override
    public boolean isSameType(Type t) {
        if (t instanceof StructType) {
            return name.equals(((StructType) t).name);
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
        return allocated ? fields.get(attr) : null;
    }

    public int getOffsetOfField(String attr) {
        int offset = 0;
        for (final Map.Entry<String, Type> pair : fields.entrySet()) {
            if (pair.getKey().equals(attr)) {
                break;
            }
            offset += pair.getValue().getSize();
        }
        return offset;
    }

    @Override
    public String toString() {
        return (allocated ? "allocated " : "") + "struct " + name;
    }
}