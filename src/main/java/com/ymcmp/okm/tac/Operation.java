package com.ymcmp.okm.tac;

public enum Operation {
    NOP,

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

    INT_LT,
    INT_GT,
    INT_LE,
    INT_GE,
    INT_EQ,
    INT_NE,

    INT_NEG,
    INT_CPL,

    INT_ADD,
    INT_SUB,
    INT_MUL,
    INT_DIV,
    INT_MOD,

    LONG_CMP,

    LONG_NEG,
    LONG_CPL,

    LONG_ADD,
    LONG_SUB,
    LONG_MUL,
    LONG_DIV,
    LONG_MOD,

    FLOAT_CMP,

    FLOAT_NEG,

    FLOAT_ADD,
    FLOAT_SUB,
    FLOAT_MUL,
    FLOAT_DIV,
    FLOAT_MOD,

    DOUBLE_CMP,

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

    REFER_VAR,
    REFER_ATTR,

    POINTER_GET,
    POINTER_PUT,

    DEREF_GET_ATTR,
    DEREF_PUT_ATTR,

    ALLOC_STRUCT,

    GET_ATTR,
    PUT_ATTR,

    RETURN_UNIT,
    RETURN_INT,
    RETURN_FLOAT,

    GOTO,

    JUMP_IF_TRUE,
    JUMP_IF_FALSE,

    POP_PARAM_INT,
    POP_PARAM_FLOAT,

    PUSH_PARAM_INT,
    PUSH_PARAM_FLOAT,

    CALL_INT,
    CALL_FLOAT,
    CALL_UNIT,
    TAILCALL;

    public boolean isCmp() {
        switch (this) {
            case LONG_CMP:
            case FLOAT_CMP:
            case DOUBLE_CMP:
                return true;
            default:
                return false;
        }
    }

    public boolean readsFromDst() {
        switch (this) {
            case PUSH_PARAM_INT:
            case PUSH_PARAM_FLOAT:
            case PUT_ATTR:
            case DEREF_PUT_ATTR:
            case CALL_UNIT:
            case RETURN_INT:
            case RETURN_FLOAT:
            case TAILCALL:
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
            case RETURN_INT:
            case RETURN_FLOAT:
            case JUMP_IF_TRUE:
            case JUMP_IF_FALSE:
                return true;
            default:
                return false;
        }
    }

    public boolean branchesToAddress() {
        switch (this) {
            case GOTO:
            case JUMP_IF_TRUE:
            case JUMP_IF_FALSE:
                return true;
            default:
                return false;
        }
    }

    public boolean hasPotentialSideEffects() {
        switch (this) {
            case STORE_VAR:
            case REFER_VAR:
            case REFER_ATTR:
            case POINTER_PUT:
            case DEREF_PUT_ATTR:
            case PUSH_PARAM_INT:
            case PUSH_PARAM_FLOAT:
            case POP_PARAM_INT:
            case POP_PARAM_FLOAT:
            case PUT_ATTR:
            case CALL_INT:
            case CALL_FLOAT:
            case CALL_UNIT:
            case TAILCALL:
                return true;
            default:
                return false;
        }
    }

    public Operation getMatchingReturn() {
        switch (this) {
            case CALL_INT:
                return RETURN_INT;
            case CALL_FLOAT:
                return RETURN_FLOAT;
            case CALL_UNIT:
                return RETURN_UNIT;
            default:
                return null;
        }
    }
}