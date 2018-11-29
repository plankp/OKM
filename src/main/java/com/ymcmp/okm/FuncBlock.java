package com.ymcmp.okm;

import java.io.Serializable;

import java.util.List;

import com.ymcmp.okm.tac.Statement;

import com.ymcmp.okm.type.FuncType;

public final class FuncBlock implements Serializable {

    private static final long serialVersionUID = -1233545L;

    public final FuncType signature;
    public final List<Statement> code;

    public FuncBlock(FuncType signature, List<Statement> code) {
        this.signature = signature;
        this.code = code;
    }
}