package com.ymcmp.okm.tac;

public interface Mutable extends Value {

    public Value getValue();

    public void setValue(Value value);

    @Override
    public default boolean isNumeric() {
        return getValue().isNumeric();
    }

    @Override
    public default boolean isTemporary() {
        return getValue().isTemporary();
    }
}