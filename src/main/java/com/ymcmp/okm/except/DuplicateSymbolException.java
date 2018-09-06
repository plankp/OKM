package com.ymcmp.okm.except;

public class DuplicateSymbolException extends RuntimeException {

    public DuplicateSymbolException(String name) {
        super("Duplicate symbol: " + name);
    }
}