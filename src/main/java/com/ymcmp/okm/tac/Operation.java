package com.ymcmp.okm.tac;

public enum Operation {
    NOP,

    UNARY_ADD,
    UNARY_SUB,
    UNARY_NOT,
    UNARY_TILDA,

    BINARY_ADD,
    BINARY_SUB,
    BINARY_MUL,
    BINARY_DIV,
    BINARY_MOD,

    BINARY_LESSER_THAN,
    BINARY_GREATER_THAN,
    BINARY_LESSER_EQUALS,
    BINARY_GREATER_EQUALS,
    BINARY_EQUALS,
    BINARY_NOT_EQUALS,

    CONV_BYTE_INT,
    CONV_SHORT_INT,
    CONV_LONG_INT,

    CONV_INT_BYTE,
    CONV_INT_SHORT,
    CONV_INT_LONG,

    CONV_INT_FLOAT,
    CONV_LONG_FLOAT,

    CONV_INT_DOUBLE,
    CONV_LONG_DOUBLE,
    CONV_FLOAT_DOUBLE,

    INT_NEG,
    INT_CPL,

    INT_ADD,
    INT_SUB,
    INT_MUL,
    INT_DIV,
    INT_MOD,

    LONG_NEG,
    LONG_CPL,

    LONG_ADD,
    LONG_SUB,
    LONG_MUL,
    LONG_DIV,
    LONG_MOD,

    FLOAT_NEG,

    FLOAT_ADD,
    FLOAT_SUB,
    FLOAT_MUL,
    FLOAT_DIV,
    FLOAT_MOD,

    DOUBLE_NEG,

    DOUBLE_ADD,
    DOUBLE_SUB,
    DOUBLE_MUL,
    DOUBLE_DIV,
    DOUBLE_MOD,

    LOAD_TRUE,
    LOAD_FALSE,
    LOAD_NUMERAL,

    STORE_VAR,

    RETURN_UNIT,
    RETURN_VALUE,

    GOTO,

    JUMP_IF_TRUE,
    JUMP_IF_FALSE,

    POP_PARAM,

    PUSH_PARAM,

    CALL,
    TAILCALL;

    public boolean branches() {
        switch (this) {
            case GOTO:
            case TAILCALL:
            case RETURN_UNIT:
            case RETURN_VALUE:
            case JUMP_IF_TRUE:
            case JUMP_IF_FALSE:
                return true;
            default:
                return false;
        }
    }
}