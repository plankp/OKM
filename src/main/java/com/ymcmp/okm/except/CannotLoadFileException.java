package com.ymcmp.okm.except;

import java.nio.file.Path;

public class CannotLoadFileException extends RuntimeException {

    public CannotLoadFileException(final Path p) {
        super("Cannot load file: " + p);
    }

    public CannotLoadFileException(final Path p, Throwable stacktrace) {
        super("Cannot load file: " + p, stacktrace);
    }
}