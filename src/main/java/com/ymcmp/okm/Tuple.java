package com.ymcmp.okm;

import java.io.Serializable;

public final class Tuple<A, B> implements Serializable {

    private static final long serialVersionUID = 2304827465L;

    private final A a;
    private final B b;

    public Tuple() {
        this(null, null);
    }

    public Tuple(A a, B b) {
        this.a = a;
        this.b = b;
    }

    public boolean isEmpty() {
        return a == null && b == null;
    }

    public A getA() {
        return a;
    }

    public B getB() {
        return b;
    }
}