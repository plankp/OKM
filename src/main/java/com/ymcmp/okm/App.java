package com.ymcmp.okm;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import java.util.logging.Level;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;

import com.ymcmp.okm.tac.Statement;

import com.ymcmp.okm.runtime.Machine;

import com.ymcmp.okm.converter.IRFormatter;
import com.ymcmp.okm.converter.X86Converter;

public class App {

    public static class Args {

        @Parameter(converter=PathConverter.class)
        private List<Path> inputPaths = new ArrayList<>();

        @Parameter(names={"--import-path", "-i"}, description="Add directory to import search path", converter=PathConverter.class)
        private List<Path> importPath = new ArrayList<>();

        @Parameter(names={"--debug"}, description="Run compiler in debug mode")
        private boolean debug = false;

        @Parameter(names={"--show-ir"}, description="Shows intermediate representation after compilation", arity=1)
        private boolean showIR = true;

        @Parameter(names={"--exec-ir"}, description="Executes intermediate representation after compilation", arity=1)
        private boolean execIR = false;

        @Parameter(names={"--to-x86"}, description="Converts IR to x86 Intel syntax assembly")
        private boolean toX86 = true;

        @Parameter(names={"--help", "-h"}, description="Displays help")
        private boolean help = false;
    }

    public static class PathConverter implements IStringConverter<Path> {
        @Override
        public Path convert(String value) {
            return Paths.get(value);
        }
    }

    public static void main(String[] args) {
        final Args argData = new Args();
        final JCommander instance = JCommander.newBuilder()
                .addObject(argData)
                .build();
        try {
            instance.parse(args);
        } catch (ParameterException ex) {
            System.err.println(ex.getMessage());
            instance.usage();
            return;
        }

        if (argData.help) {
            instance.usage();
            return;
        }

        if (argData.inputPaths.isEmpty()) {
            System.err.println("Missing input file");
            return;
        }

        LocalVisitor.LOGGER.setLevel(argData.debug ? Level.INFO : Level.OFF);

        final Map<String, List<Statement>> result = new LocalVisitor(argData.importPath)
                .compile(argData.inputPaths);

        if (argData.showIR) {
            final IRFormatter conv = new IRFormatter();
            result.forEach((k, v) -> {
                System.out.println(conv.convert(k, v));
                System.out.println("");
            });
        }

        if (argData.toX86) {
            final X86Converter conv = new X86Converter();
            result.forEach((k, v) -> {
                System.out.println(conv.convert(k, v));
                System.out.println("");
            });
        }

        if (argData.execIR) {
            final Machine machine = new Machine();
            machine.execute(result);
        }
    }
}
