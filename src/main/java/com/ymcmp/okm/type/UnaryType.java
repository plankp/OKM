package com.ymcmp.okm.type;

import java.util.HashMap;
import java.util.Objects;

public final class UnaryType implements Type {

    private static final long serialVersionUID = 19375477L;

    private static final HashMap<String, Integer> NUM_TYPE_DISTANCE = new HashMap<>();
    private static final HashMap<String, UnaryType> CACHE = new HashMap<>();

    static {
        NUM_TYPE_DISTANCE.put("byte", Byte.SIZE);
        NUM_TYPE_DISTANCE.put("short", Short.SIZE);
        NUM_TYPE_DISTANCE.put("int", Integer.SIZE);
        NUM_TYPE_DISTANCE.put("long", Long.SIZE);

        // See canConvertTo and handlePrimitiveMath for why the (1 + 2 *)
        NUM_TYPE_DISTANCE.put("float",  1 + 2 * Float.SIZE);
        NUM_TYPE_DISTANCE.put("double", 1 + 2 * Double.SIZE);
    }

    public final String name;

    private UnaryType(final String name) {
        this.name = name;
    }

    public static UnaryType getType(String name) {
        UnaryType ret = CACHE.get(name);
        if (ret == null) {
            CACHE.put(name, ret = new UnaryType(name));
        }
        return ret;
    }

    @Override
    public UnaryType allocate() {
        return this;
    }

    @Override
    public int getSize() {
        switch (name) {
            case "unit":    // Unit has a size, because you could pass in unit as a argument!
            case "bool":
            case "byte":    return 8;
            case "short":   return 16;
            case "int":     return 32;
            case "long":    return 64;
            case "float":   return 32;
            case "double":  return 64;
            default:
                throw new AssertionError("Unknown size of type " + name);
        }
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean isSameType(final Type t) {
        if (t instanceof UnaryType) {
            final UnaryType other = (UnaryType) t;
            return Objects.equals(this.name, other.name);
        }
        return false;
    }

    @Override
    public boolean isFloatPoint() {
        switch (name) {
            case "float":
            case "double":
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean canConvertTo(Type t) {
        if (t instanceof UnaryType) {
            final UnaryType other = (UnaryType) t;

            final Integer a = NUM_TYPE_DISTANCE.get(this.name);
            final Integer b = NUM_TYPE_DISTANCE.get(other.name);
            if (a != null && b != null) {
                // long and float actually causes float to be returned!
                return a <= b;
            }
            return Objects.equals(this.name, other.name);
        }
        return Type.super.canConvertTo(t);
    }

    @Override
    public Type tryPerformCall(final Type... args) {
        return null;
    }

    @Override
    public Type tryPerformUnary(UnaryOperator op) {
        switch (op) {
            case ADD:
            case SUB:
                return NUM_TYPE_DISTANCE.get(name) != null ? this : null;
            case TILDA:
                return NUM_TYPE_DISTANCE.get(name) != null
                        && !"float".equals(name)
                        && !"double".equals(name) ? this : null;
        }
        return null;
    }

    private Type handlePrimitiveMath(Type rhs) {
        if (rhs instanceof UnaryType) {
            final UnaryType other = (UnaryType) rhs;

            final Integer a = NUM_TYPE_DISTANCE.get(this.name);
            final Integer b = NUM_TYPE_DISTANCE.get(other.name);
            if (a != null && b != null) {
                return a < b ? rhs : this;
            }
        }
        return null;
    }

    @Override
    public Type tryPerformBinary(BinaryOperator op, Type rhs) {
        switch (op) {
            case ADD:
            case SUB:
            case MUL:
            case DIV:
            case MOD:
                return handlePrimitiveMath(rhs);
            case LESSER_THAN:
            case GREATER_THAN:
            case LESSER_EQUALS:
            case GREATER_EQUALS:
                return handlePrimitiveMath(rhs) == null ? null : getType("bool");
            case EQUALS:
            case NOT_EQUALS:
                return getType("bool");
        }
        return null;
    }

    @Override
    public Type tryAccessAttribute(String attr) {
        return null;
    }
}