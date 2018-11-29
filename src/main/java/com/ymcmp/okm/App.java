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

import com.ymcmp.okm.opt.*;

import com.ymcmp.okm.tac.Statement;

import com.ymcmp.okm.runtime.Machine;

import com.ymcmp.okm.converter.Converter;
import com.ymcmp.okm.converter.IRFormatter;
import com.ymcmp.okm.converter.AMD64Converter;

public class App {

    public static class Args {

        @Parameter(converter=PathConverter.class)
        private List<Path> inputPaths = new ArrayList<>();

        @Parameter(names={"--import-path", "-i"}, description="Add directory to import search path", converter=PathConverter.class)
        private List<Path> importPath = new ArrayList<>();

        @Parameter(names={"--debug"}, description="Run compiler in debug mode")
        private boolean debug = false;

        @Parameter(names={"--show-ir"}, description="Shows intermediate representation after compilation")
        private boolean showIR = false;

        @Parameter(names={"--exec-ir"}, description="Executes intermediate representation after compilation")
        private boolean execIR = false;

        @Parameter(names={"--to-amd64"}, description="Converts IR to x86-64 Intel syntax assembly (use with NASM)")
        private boolean toAMD64 = false;

        @Parameter(names={"--help", "-h"}, description="Displays help")
        private boolean help = false;
    }

    public static class PathConverter implements IStringConverter<Path> {
        @Override
        public Path convert(String value) {
            return Paths.get(value);
        }
    }

    private static final List<Pass> OPT_PASSES = new ArrayList<>();

    static {
        OPT_PASSES.add(new ReduceMovePass());
        OPT_PASSES.add(new TailCallPass());
        OPT_PASSES.add(new SquashCmpPass());
        OPT_PASSES.add(new ConstantFoldPass());
        OPT_PASSES.add(new EliminateDeadCodePass());
        OPT_PASSES.add(new TempParamPass());
        OPT_PASSES.add(new ComSwapPass());
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

        final EliminateNopPass eliminateNop = new EliminateNopPass();
        result.forEach((name, funcIR) -> {
            int sizeBeforePass = 0;
            do {
                sizeBeforePass = funcIR.size();
                for (final Pass pass : OPT_PASSES) {
                    pass.process(name, funcIR);
                    pass.reset();
                    eliminateNop.process(name, funcIR);
                    eliminateNop.reset();
                }
            } while (sizeBeforePass != funcIR.size());
        });

        if (argData.showIR) {
            final IRFormatter conv = new IRFormatter();
            result.forEach(conv::convert);
            System.out.println(conv.getResult());
        }

        if (argData.toAMD64) {
            final AMD64Converter conv = new AMD64Converter();
            result.forEach(conv::convert);
            System.out.println(conv.getResult());
        }

        if (argData.execIR) {
            final Machine machine = new Machine();
            machine.execute(result);
        }
    }
}
