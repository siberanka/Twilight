package com.siberanka.twilight.compiler;

import java.io.IOException;
import java.util.List;

public final class ConversionException extends IOException {
    private final List<String> problems;

    public ConversionException(String message, List<String> problems) {
        super(message);
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() { return problems; }
}
