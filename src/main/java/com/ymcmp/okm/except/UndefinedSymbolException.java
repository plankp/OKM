package com.ymcmp.okm.except;

public class UndefinedSymbolException extends RuntimeException {

    public UndefinedSymbolException(final String name) {
        super("Undefined symbol: " + name);
    }
}