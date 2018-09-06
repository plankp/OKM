package com.ymcmp.okm.tac;

import java.io.Serializable;

public final class Fixnum implements Serializable, Value {

    private static final long serialVersionUID = 62374902123L;

    public final int size;
    public final String value;

    public final boolean isInt;

    public Fixnum(String value) {
        this(value, Integer.SIZE);
    }

    public Fixnum(String value, int size) {
        String conv;
        boolean isInt;
        try {
            conv = truncateValue(Long.parseLong(value), size);
            isInt = true;
        } catch (NumberFormatException ex) {
            // If it is not a int, it is a float
            Double.parseDouble(value);
            conv = value;
            isInt = false;
        }
        this.value = conv;
        this.size = size;
        this.isInt = isInt;
    }

    public Fixnum(long value, int size) {
        this.value = truncateValue(value, size);
        this.size = size;
        this.isInt = true;
    }

    public Fixnum(long value) {
        this.value = Long.toString(value);
        this.size = Long.SIZE;
        this.isInt = true;
    }

    public Fixnum changeSize(int size) {
        return new Fixnum(this.value, size);
    }

    @Override
    public boolean isNumeric() {
        return true;
    }

    @Override
    public boolean isTemporary() {
        return false;
    }

    @Override
    public String toString() {
        return value + "_" + size;
    }

    private static String truncateValue(final long value, final int size) {
        final int bytes = size / Byte.SIZE * 2;
        final String toHex = Long.toString(value, 16);
        // Only keep last $bytes characters if string is longer than that
        final int len = toHex.length();
        final String truncated = len > bytes ? toHex.substring(len - bytes, len) : toHex;
        // Convert back to base 10
        return Long.toString(Long.parseLong(truncated, 16));
    }
}