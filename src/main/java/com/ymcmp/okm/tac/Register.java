package com.ymcmp.okm.tac;

import java.io.Serializable;

public final class Register implements Serializable, Value {

    private static final long serialVersionUID = 2348254334682L;

    private static final String PREFIX_TEMPORARY = "%T";

    private static long counter = 0;

    private final String name;

    private Register(final String name) {
        this.name = name;
    }

    public static Register makeNamed(String name) {
        return new Register(name);
    }

    public static Register makeTemporary() {
        return new Register(PREFIX_TEMPORARY + counter++);
    }

    public static void resetCounter() {
        counter = 0;
    }

    @Override
    public Register duplicate() {
        return this;
    }

    @Override
    public boolean isNumeric() {
        return false;
    }

    @Override
    public boolean isTemporary() {
        return name.startsWith(PREFIX_TEMPORARY);
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        return obj.getClass() == this.getClass() && name.equals(((Register) obj).name);
    }
}