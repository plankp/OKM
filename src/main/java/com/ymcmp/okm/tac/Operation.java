package com.ymcmp.okm.tac;

public enum Operation {
    NOP,

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
    LOAD_ENUM,
    LOAD_ENUM_KEY,
    LOAD_STRUCT,

    STORE_VAR,

    REFER_VAR,
    REFER_ATTR,

    ALLOC_STRUCT,

    GET_ATTR,
    PUT_ATTR,

    RETURN_UNIT,
    RETURN_VALUE,

    GOTO,

    JUMP_IF_TRUE,
    JUMP_IF_FALSE,

    POP_PARAM,

    PUSH_PARAM,

    CALL,
    TAILCALL;

    public boolean readsFromDst() {
        switch (this) {
            case PUSH_PARAM:
            case PUT_ATTR:
            case RETURN_VALUE:
                return true;
            default:
                return false;
        }
    }

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

    public boolean hasPotentialSideEffects() {
        switch (this) {
            case BINARY_LESSER_THAN:
            case BINARY_GREATER_THAN:
            case BINARY_LESSER_EQUALS:
            case BINARY_GREATER_EQUALS:
            case BINARY_EQUALS:
            case BINARY_NOT_EQUALS:
            case STORE_VAR:
            case REFER_VAR:
            case REFER_ATTR:
            case PUSH_PARAM:
            case POP_PARAM:
            case PUT_ATTR:
            case CALL:
            case TAILCALL:
                return true;
            default:
                return false;
        }
    }
}