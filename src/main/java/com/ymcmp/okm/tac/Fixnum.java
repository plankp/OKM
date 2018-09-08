package com.ymcmp.okm.tac;

import java.io.Serializable;

import java.math.BigDecimal;

import java.util.Objects;

public final class Fixnum implements Serializable, Value, Comparable<Fixnum> {

    private static final long serialVersionUID = 62374902123L;

    public static final Fixnum TRUE  = new Fixnum(1, Byte.SIZE);
    public static final Fixnum FALSE = new Fixnum(0, Byte.SIZE);

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

    public Fixnum(double value) {
        this.value = Double.toString(value);
        this.size = Double.SIZE;
        this.isInt = false;
    }

    public Fixnum(double value, int size) {
        this.value = Double.toString(value);
        this.size = size;
        this.isInt = false;
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

    @Override
    public int compareTo(final Fixnum other) {
        if (value.equals("NaN")) {
            return other.value.equals("NaN") ? 0 : 1;
        }
        if (other.value.equals("NaN")) {
            return -1;
        }
        final BigDecimal a = new BigDecimal(value);
        final BigDecimal b = new BigDecimal(other.value);
        return a.compareTo(b);
    }

    @Override
    public int hashCode() {
        return Objects.hash(size, value, isInt);
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (obj.getClass() == this.getClass()) {
            final Fixnum f = (Fixnum) obj;
            if (value.equals("NaN") || f.value.equals("NaN")) {
                // Handle special case which is NaN == NaN returns false
                return false;
            }
            // Two are equal if they have same value, so 1.equals(1.0)
            return compareTo(f) == 0;
        }
        return false;
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