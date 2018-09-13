package com.ymcmp.okm.tac;

import java.util.HashMap;

import java.util.stream.Collectors;

import com.ymcmp.okm.Tuple;

public final class StructFields implements Value {

    private final HashMap<String, Value> fields;

    public StructFields() {
        this.fields = new HashMap<>();
    }

    @Override
    public StructFields duplicate() {
        // Need to recursively duplicate all fields!
        final StructFields newStruct = new StructFields();
        newStruct.fields.putAll(fields.entrySet().stream()
                .map(ent -> new Tuple<>(ent.getKey(), ent.getValue().duplicate()))
                .collect(Collectors.toMap(Tuple::getA, Tuple::getB)));
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