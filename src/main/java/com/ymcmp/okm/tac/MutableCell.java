package com.ymcmp.okm.tac;

import java.io.Serializable;

public final class MutableCell implements Serializable, Mutable {

    private static final long serialVersionUID = 1092347246356L;

    private Value mutable;

    public MutableCell() {
        this(null);
    }

    public MutableCell(Value val) {
        this.mutable = val;
    }

    @Override
    public Value getValue() {
        return mutable;
    }

    @Override
    public void setValue(Value value) {
        mutable = value;
    }

    @Override
    public MutableCell duplicate() {
        // Note: the object it points to is not duplicated!
        return new MutableCell(mutable);
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