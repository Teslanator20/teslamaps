package com.teslamaps.adm.misc;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

// Ported from ADM's rotation/EcLog.kt: one session log under logs/adm, `HH:mm:ss.SSS <TAG> <payload>`
public final class EcLog {
    private static final int KEEP = 15;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static PrintWriter out;
    private static Path path;

    private EcLog() {}

    public static Path path() {
        return path;
    }

    public static synchronized void open(Path gameDir, List<String> header) {
        close();
        try {
            Path dir = gameDir.resolve("logs").resolve("adm");
            Files.createDirectories(dir);
            Path file = dir.resolve("ec-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".log");
            out = new PrintWriter(Files.newBufferedWriter(file), true);
            path = file;
            for (String h : header) log("SESSION", h);
            prune(dir);
        } catch (Throwable t) {
            out = null;
            path = null;
        }
    }

    public static synchronized void log(String tag, String message) {
        if (out == null) return;
        out.println(LocalTime.now().format(STAMP) + "\t" + tag + "\t" + message.replace('\n', ' '));
    }

    public static synchronized void close() {
        if (out == null) return;
        out.flush();
        out.close();
        out = null;
    }

    private static void prune(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().startsWith("ec-") && p.getFileName().toString().endsWith(".log"))
                    .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                    .skip(KEEP)
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
        } catch (Throwable ignored) {
        }
    }
}
