package com.ymcmp.okm.converter;

import java.util.List;

import com.ymcmp.okm.tac.Statement;

public class IRFormatter implements Converter {

    @Override
    public String convert(final String name, final List<Statement> body) {
        final StringBuilder sb = new StringBuilder();

        sb.append("Function ").append(name).append('\n');
        for (int i = 0; i < body.size(); ++i) {
            sb.append(String.format("%4d %s", i, body.get(i))).append('\n');
        }

        return sb.toString();
    }
}