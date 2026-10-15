package com.siberanka.twilight.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationLogTest {
    @TempDir Path root;

    @Test
    void recordsPhasesThreadsAndCompleteFailureTrace() throws Exception {
        Path path;
        try (OperationLog log = OperationLog.create(root, "convert")) {
            path = log.path();
            log.info("discovery", "3 sources");
            log.warn("conversion-problem", "missing model");
            log.failure("failed", new IllegalStateException("outer", new IllegalArgumentException("inner")));
        }
        String text = Files.readString(path);
        assertTrue(path.getFileName().toString().startsWith("convert-log-"));
        assertTrue(text.contains("[INFO]"));
        assertTrue(text.contains("[WARN]"));
        assertTrue(text.contains("[ERROR]"));
        assertTrue(text.contains("IllegalStateException: outer"));
        assertTrue(text.contains("Caused by: java.lang.IllegalArgumentException: inner"));
        assertTrue(text.contains(Thread.currentThread().getName()));
    }
}
