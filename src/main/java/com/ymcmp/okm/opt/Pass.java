package com.ymcmp.okm.opt;

import java.util.List;

import com.ymcmp.okm.tac.Statement;

public interface Pass {

    public void process(String funcName, List<Statement> block);
}