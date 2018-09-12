package com.ymcmp.okm.type;

import java.util.Arrays;

public final class FuncType implements Type {

    public Type ret;
    public Type[] params;

    public FuncType(Type ret, Type... params) {
        this.ret = ret;
        this.params = params;
    }

    @Override
    public int getSize() {
        // This is very wrong and non-portable.
        // But that is what the C compiler on
        // my local machine tells me :-)
        return 64;
    }

    @Override
    public String toString() {
        return Arrays.toString(params) + " -> " + ret;
    }

    @Override
    public boolean isSameType(Type t) {
        // Only true if type signature is exactly the same
        if (t instanceof FuncType) {
            final FuncType other = (FuncType) t;
            return ret.isSameType(other.ret)
                    && Arrays.equals(params, other.params);
        }
        return false;
    }

    @Override
    public boolean canConvertTo(Type t) {
        if (t instanceof FuncType) {
            final FuncType other = (FuncType) t;

            // X11 -> X12 -> Y1 convert to X21 -> X22 -> Y2
            // X11 <: X21
            // X12 <: X22
            // Y1  :>  Y2
            //
            // this.params must be convertible to t.params
            // t.ret must be convertible to this.ret
            if (params.length != other.params.length) {
                return false;
            }
            for (int i = 0; i < params.length; ++i) {
                if (!params[i].canConvertTo(other.params[i])) {
                    return false;
                }
            }
            return other.ret.canConvertTo(ret);
        }
        return Type.super.canConvertTo(t);
    }

    @Override
    public Type tryPerformCall(final Type... args) {
        // Call only succeeds if args are convertible to params
        if (params.length != args.length) {
            return null;
        }
        for (int i = 0; i < args.length; ++i) {
            if (!args[i].canConvertTo(params[i])) {
                return null;
            }
        }
        return ret;
    }

    @Override
    public Type tryPerformUnary(UnaryOperator op) {
        return null;
    }

    @Override
    public Type tryPerformBinary(BinaryOperator op, Type rhs) {
        return null;
    }

    @Override
    public Type tryAccessAttribute(String attr) {
        return null;
    }
}