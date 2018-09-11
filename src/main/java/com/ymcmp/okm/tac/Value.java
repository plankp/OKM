package com.ymcmp.okm.tac;

public interface Value {

    public Value duplicate();

    public boolean isNumeric();

    public boolean isTemporary();
}