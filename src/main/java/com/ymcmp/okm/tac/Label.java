package com.ymcmp.okm.tac;

import java.io.Serializable;

public final class Label implements Serializable, Value {

    private static final long serialVersionUID = 2348254334682L;

    private int address;

    public Label() {
    }

    public Label(int addr) {
        this.address = addr;
    }

    public int getAddress() {
        return this.address;
    }

    public void setAddress(final int address) {
        this.address = address;
    }

    @Override
    public Label duplicate() {
        return new Label(address);
    }

    @Override
    public boolean isNumeric() {
        // For now...
        return false;
    }

    @Override
    public boolean isTemporary() {
        return false;
    }

    @Override
    public String toString() {
        return "(" + address + ")";
    }
}