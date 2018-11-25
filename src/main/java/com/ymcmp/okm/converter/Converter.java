package com.ymcmp.okm.converter;

import java.util.List;

import com.ymcmp.okm.tac.Statement;

public interface Converter {

    public void convert(String name, List<Statement> body);

    public String getResult();

    public void reset();
}