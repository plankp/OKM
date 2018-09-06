package com.ymcmp.okm.except;

import com.ymcmp.okm.type.Type;

public class IncompatibleTypeException extends RuntimeException {

    public IncompatibleTypeException(Type value, Type conforming) {
        super("Attempt to convert type " + value + " to unrelated type " + conforming);
    }
}