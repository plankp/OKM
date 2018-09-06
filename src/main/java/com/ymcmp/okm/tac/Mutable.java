package com.ymcmp.okm.tac;

import java.io.Serializable;

public final class Mutable<T extends Value> implements Serializable, Value {

    private static final long serialVersionUID = 1092347246356L;

    private T mutable;

    public T getValue() {
        return mutable;
    }

    public void setValue(T value) {
        mutable = value;
    }

    @Override
    public boolean isNumeric() {
        return mutable.isNumeric();
    }

    @Override
    public boolean isTemporary() {
        return mutable.isTemporary();
    }

    @Override
    public String toString() {
        return mutable.toString();
    }
}