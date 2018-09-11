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
    public Mutable<T> duplicate() {
        // Note: the object it points to is not duplicated!
        final Mutable<T> t = new Mutable<>();
        t.setValue(mutable);
        return t;
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

    @Override
    public int hashCode() {
        return mutable.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        return mutable == null && obj == null || mutable.equals(obj);
    }
}