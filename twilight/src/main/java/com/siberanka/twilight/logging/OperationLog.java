package com.siberanka.twilight.logging;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Per-operation append-only diagnostic log. Never contains resource-pack file contents. */
public final class OperationLog implements AutoCloseable {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private final Path path;
    private final BufferedWriter writer;

    private OperationLog(Path path, BufferedWriter writer) {
        this.path = path;
        this.writer = writer;
    }

    public static OperationLog create(Path dataDirectory, String operation) throws IOException {
        String safe = operation.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        Path directory = dataDirectory.toAbsolutePath().normalize().resolve("logs");
        Files.createDirectories(directory);
        Path path = directory.resolve(safe + "-log-" + FILE_TIME.format(LocalDateTime.now()) + '-' +
                UUID.randomUUID().toString().substring(0, 8) + ".txt");
        BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        OperationLog log = new OperationLog(path, writer);
        log.info("operation", operation);
        return log;
    }

    public Path path() { return path; }

    public synchronized void info(String phase, Object value) {
        write("INFO", phase, String.valueOf(value));
    }

    public synchronized void warn(String phase, Object value) {
        write("WARN", phase, String.valueOf(value));
    }

    public synchronized void failure(String phase, Throwable failure) {
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        write("ERROR", phase, trace.toString());
    }

    private void write(String level, String phase, String value) {
        try {
            writer.write(Instant.now() + " [" + level + "] [" + Thread.currentThread().getName() + "] " + phase + " | " + value);
            writer.newLine();
            writer.flush();
        } catch (IOException ignored) {
            // The primary operation must still fail or complete independently of secondary diagnostic I/O.
        }
    }

    @Override
    public synchronized void close() throws IOException {
        writer.close();
    }
}
