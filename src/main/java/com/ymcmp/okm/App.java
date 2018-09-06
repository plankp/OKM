package com.ymcmp.okm;

import java.util.Map;
import java.util.List;

import java.nio.file.Paths;

import com.ymcmp.okm.tac.Statement;

public class App {

    public static void main(String[] args) {
        final Map<String, List<Statement>> result = new LocalVisitor()
                .compile(Paths.get("./sample/demo.okm"));
        result.forEach((k, v) -> {
            System.out.println("Function " + k);
            for (int i = 0; i < v.size(); ++i) {
                System.out.printf("%4d %s", i, v.get(i));
                System.out.println("");
            }
            System.out.println("");
        });
    }
}
