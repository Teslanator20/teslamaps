package com.teslamaps.adm.adr;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * ADR run files: gzip JSON Lines, one event per line, same record format as ADM.
 * Finished runs: {@code <runsDir>/yyyy-MM-dd_HH-mm-ss_<floor>.jsonl.gz}; while recording
 * {@code recording-<stamp>.jsonl.gz.part}. First line is {@code {"k":"meta",...}}.
 */
public final class AdrRunFiles {
    private AdrRunFiles() {}

    public static final Pattern PART_NAME = Pattern.compile("^recording-(.+)\\.jsonl\\.gz\\.part$");

    public static Path baseDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("adm").resolve("adr");
    }

    /** Hypixel runs. */
    public static Path runsDir() {
        return baseDir().resolve("runs");
    }

    /** ADS (simulator) runs, Record ADS Runs. */
    public static Path simDir() {
        return baseDir().resolve("sim");
    }

    /** Finished runs in a folder, newest first. */
    public static List<Path> list(Path dir) {
        if (!Files.isDirectory(dir)) return new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".jsonl.gz"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public static BufferedReader reader(Path file) throws IOException {
        return new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8), 1 << 16);
    }

    public static BufferedWriter writer(Path file) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8), 1 << 16);
    }

    /** Every line of a run as JSON, in order. A cut-off end is ignored. */
    public static void forEach(Path file, Consumer<JsonObject> each) throws IOException {
        try (BufferedReader r = reader(file)) {
            String line;
            while (true) {
                try {
                    line = r.readLine();
                } catch (IOException cut) {
                    break;
                }
                if (line == null) break;
                if (line.isEmpty()) continue;
                each.accept(JsonParser.parseString(line).getAsJsonObject());
            }
        }
    }

    /** The meta line, or null. */
    public static JsonObject meta(Path file) {
        try (BufferedReader r = reader(file)) {
            String line = r.readLine();
            if (line == null) return null;
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            return o.has("k") && "meta".equals(o.get("k").getAsString()) ? o : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String safeFloor(String floor) {
        return (floor == null ? "unknown" : floor).replaceAll("[^A-Za-z0-9]+", "");
    }

    /** A .part a crash left behind: its whole lines saved as a finished run, then the .part goes. */
    public static void salvage(Path part) throws IOException {
        Matcher pm = PART_NAME.matcher(part.getFileName().toString());
        if (!pm.matches()) return;
        String stamp = pm.group(1);
        Path temp = part.resolveSibling("salvage-" + stamp + ".tmp");
        int lines = 0;
        String floor = "unknown";
        boolean meta = false;
        try (BufferedWriter w = writer(temp)) {
            try (BufferedReader r = reader(part)) {
                while (true) {
                    String line = r.readLine();
                    if (line == null) break;
                    if (lines == 0) meta = line.startsWith("{\"k\":\"meta\"");
                    if (line.startsWith("{\"k\":\"floor\"")) {
                        try { floor = JsonParser.parseString(line).getAsJsonObject().get("floor").getAsString(); } catch (Exception ignored) {}
                    }
                    w.write(line);
                    w.newLine();
                    lines++;
                }
            } catch (IOException cut) {
                // where the crash cut it off
            }
        }
        if (!meta || lines < 2) {
            Files.deleteIfExists(temp);
            Files.deleteIfExists(part);
            return;
        }
        Path target = part.resolveSibling(stamp + "_" + safeFloor(floor) + ".jsonl.gz");
        if (Files.exists(target)) {
            Files.deleteIfExists(temp);
            return;
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(part);
        com.teslamaps.TeslaMaps.LOGGER.info("[adr] saved {} lines of an unfinished recording as {}", lines, target.getFileName());
    }
}
