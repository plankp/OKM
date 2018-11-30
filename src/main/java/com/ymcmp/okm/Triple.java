package com.ymcmp.okm;

import java.io.Serializable;

public final class Triple<A, B, C> implements Serializable {

    private static final long serialVersionUID = 2304827465L;

    private final A a;
    private final B b;
    private final C c;

    public Triple(A a, B b, C c) {
        this.a = a;
        this.b = b;
        this.c = c;
    }

    public A getA() {
        return a;
    }

    public B getB() {
        return b;
    }

    public C getC() {
        return c;
    }
}