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

    CONV_FLOAT_INT,
    CONV_FLOAT_LONG,

    CONV_INT_DOUBLE,
    CONV_LONG_DOUBLE,
    CONV_FLOAT_DOUBLE,

    CONV_DOUBLE_FLOAT,
    CONV_DOUBLE_LONG,
    CONV_DOUBLE_INT,

    INT_LT,
    INT_GT,
    INT_LE,
    INT_GE,
    INT_EQ,
    INT_NE,

    INT_CMP,

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
    LOAD_FUNC,

    STORE_VAR,

    REFER_VAR,
    REFER_ATTR,

    POINTER_GET,
    POINTER_PUT,

    DEREF_GET_ATTR,
    DEREF_PUT_ATTR,

    ALLOC_LOCAL,
    ALLOC_GLOBAL,

    GET_ATTR,
    PUT_ATTR,

    RETURN_UNIT,
    RETURN_INT,
    RETURN_FLOAT,

    GOTO,

    JUMP_INT_LT,
    JUMP_INT_GT,
    JUMP_INT_LE,
    JUMP_INT_GE,
    JUMP_INT_EQ,
    JUMP_INT_NE,

    JUMP_IF_TRUE,
    JUMP_IF_FALSE,

    POP_PARAM_INT,
    POP_PARAM_FLOAT,

    PUSH_PARAM_INT,
    PUSH_PARAM_FLOAT,

    CALL_NATIVE,

    CALL_INT,
    CALL_FLOAT,
    CALL_UNIT,
    TAILCALL;

    public boolean isCmp() {
        switch (this) {
            case INT_CMP:
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
            case POINTER_GET:
            case POINTER_PUT:
            case DEREF_PUT_ATTR:
            case PUSH_PARAM_INT:
            case PUSH_PARAM_FLOAT:
            case POP_PARAM_INT:
            case POP_PARAM_FLOAT:
            case PUT_ATTR:
            case CALL_NATIVE:
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

    public Operation getCommutativePair() {
        switch (this) {
            case INT_ADD:
            case INT_MUL:
            case LONG_ADD:
            case LONG_MUL:
            case FLOAT_ADD:
            case FLOAT_MUL:
            case DOUBLE_ADD:
            case DOUBLE_MUL:
            case INT_EQ:
            case INT_NE:
            case JUMP_INT_EQ:
            case JUMP_INT_NE:
                // these opcodes are commutative themselves
                return this;
            case INT_LT:
                return INT_GT;
            case INT_GT:
                return INT_LT;
            case INT_LE:
                return INT_GE;
            case INT_GE:
                return INT_LE;
            case JUMP_INT_LT:
                return JUMP_INT_GT;
            case JUMP_INT_GT:
                return JUMP_INT_LT;
            case JUMP_INT_LE:
                return JUMP_INT_GE;
            case JUMP_INT_GE:
                return JUMP_INT_LE;
            default:
                return null;
        }
    }
}