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

    private final LinkedHashMap<String, FuncType> vtable;

    public ClassType(String name) {
        this(name, new LinkedHashMap<>(), new LinkedHashMap<>(), false);
    }

    private ClassType(String name, LinkedHashMap<String, Type> fields, LinkedHashMap<String, FuncType> vtable, boolean allocate) {
        super(fields);
        this.name = name;
        this.vtable = vtable;
        this.allocated = allocate;
    }

    public String mangleMethodName(String name) {
        return "0_M_" + this.name + "_" + name;
    }

    public String mangleVtableName() {
        return "0_VTABLE_" + this.name;
    }

    public void addMethod(String name, FuncType type) {
        if (vtable.containsKey(name)) {
            throw new DuplicateSymbolException(name);
        }
        vtable.put(name, type);
    }

    public StructType getVtable() {
        final StructType t = new StructType();
        vtable.forEach(t::putField);
        return t.allocate();
    }

    @Override
    public ClassType allocate() {
        if (allocated) {
            throw new UndefinedOperationException("Cannot allocate non-class type");
        }
        return new ClassType(name, fields, vtable, true);
    }

    @Override
    public int getSize() {
        // + 64 because of pointer to vtable
        return 64 + super.getSize();
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

    public FuncType tryAccessMethod(String name) {
        return allocated ? vtable.get(name) : null;
    }

    @Override
    public int getOffsetOfField(String attr) {
        // vtable is a pointer
        return 64 + super.getOffsetOfField(attr);
    }

    public int getVtableOffset() {
        return 0;
    }

    public int getMethodOffsetInVtable(final String name) {
        int offset = 0;
        for (final Map.Entry<String, FuncType> entry : vtable.entrySet()) {
            if (entry.getKey().equals(name)) {
                return offset;
            }
            offset += entry.getValue().getSize();
        }
        return offset;
    }

    @Override
    public String toString() {
        return fields.entrySet().stream()
                .map(e -> e.getKey() + " :" + e.getValue())
                .collect(Collectors.joining(", ", "class(", ")"));
    }
}