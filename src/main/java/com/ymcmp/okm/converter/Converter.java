package com.ymcmp.okm.converter;

import com.ymcmp.okm.FuncBlock;

import com.ymcmp.okm.tac.Statement;

public interface Converter {

    public void convert(String name, FuncBlock body);

    public String getResult();

    public void reset();
}