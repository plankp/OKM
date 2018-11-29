package com.ymcmp.okm.converter;

import java.util.List;
import java.util.ArrayList;

import java.util.stream.Collectors;

import com.ymcmp.okm.FuncBlock;

import com.ymcmp.okm.tac.Statement;

public class IRFormatter implements Converter {

    private final List<String> list = new ArrayList<>();

    @Override
    public void convert(final String name, final FuncBlock block) {
        final StringBuilder sb = new StringBuilder();
        final List<Statement> body = block.code;

        sb.append("Function ").append(name).append(" :").append(block.signature).append('\n');
        for (int i = 0; i < body.size(); ++i) {
            sb.append(String.format("%4d %s", i, body.get(i))).append('\n');
        }

        list.add(sb.toString());
    }

    @Override
    public void reset() {
        list.clear();
    }

    @Override
    public String getResult() {
        return list.stream().collect(Collectors.joining("\n"));
    }
}