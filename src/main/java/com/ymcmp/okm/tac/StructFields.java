package com.ymcmp.okm.tac;

import java.util.HashMap;

public final class StructFields implements Value {

    private final HashMap<String, Value> fields;

    public StructFields() {
        this.fields = new HashMap<>();
    }

    public StructFields duplicate() {
        final StructFields newStruct = new StructFields();
        newStruct.fields.putAll(fields);
        return newStruct;
    }

    public Value get(String attr) {
        return fields.get(attr);
    }

    public void put(String attr, Value value) {
        fields.put(attr, value);
    }

    @Override
    public boolean isNumeric() {
        return false;
    }

    @Override
    public boolean isTemporary() {
        return false;
    }

    @Override
    public String toString() {
        return fields.toString();
    }
}