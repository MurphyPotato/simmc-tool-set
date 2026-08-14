package com.murphypotato.simmctoolset.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;

/** Bounded local diagnostic log. Export only happens after a player click. */
public final class DiagnosticLog {
    private static final int LIMIT = 120;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();

    private DiagnosticLog() {
    }

    public static void initialize() {
        info("Diagnostic log initialized");
    }

    public static synchronized void info(String message) {
        append("INFO", message, null);
    }

    public static synchronized void error(String message, Throwable error) {
        append("ERROR", message, error);
    }

    public static synchronized List<String> snapshot() {
        return List.copyOf(LINES);
    }

    public static synchronized Path export() throws IOException {
        Path directory = FabricLoader.getInstance().getConfigDir().resolve("simmc-tool-set").resolve("diagnostics");
        Files.createDirectories(directory);
        Path file = directory.resolve("diagnostic-" + Instant.now().toEpochMilli() + ".log");
        Files.write(file, LINES, StandardCharsets.UTF_8);
        return file;
    }

    private static void append(String level, String message, Throwable error) {
        String value = Instant.now() + " [" + level + "] " + message;
        if (error != null) value += " (" + error.getClass().getSimpleName() + ": " + safe(error.getMessage()) + ")";
        LINES.addLast(value);
        while (LINES.size() > LIMIT) LINES.removeFirst();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "no message" : value.replace('\n', ' ');
    }
}
