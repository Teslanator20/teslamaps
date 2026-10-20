package com.teslamaps.adm.misc;

import com.teslamaps.TeslaMaps;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

// Ported from ADM's debug/MemReport.kt: /adm memreport, LWJGL's live native allocations by call site
public final class MemReport {
    private static final int FRAMES = 14;

    private MemReport() {}

    public static void write(Consumer<String> say) {
        if (!Configuration.DEBUG_MEMORY_ALLOCATOR.get(false)) {
            say.accept("§cnot tracking native memory: start the game with §f-Dorg.lwjgl.util.DebugAllocator=true§c (and §f-Dorg.lwjgl.util.DebugAllocator.internal=true§c)");
            return;
        }
        say.accept("§7collecting LWJGL's live allocations...");
        Thread t = new Thread(() -> {
            try {
                Map<String, long[]> sites = new HashMap<>();
                Map<String, Long> threads = new HashMap<>();
                long[] total = {0, 0};
                MemoryUtil.memReport((address, memory, threadId, threadName, stacktrace) -> {
                    total[0] += memory;
                    total[1]++;
                    threads.merge(threadName == null ? "?" : threadName, memory, Long::sum);
                    List<String> frames = new ArrayList<>();
                    if (stacktrace != null) {
                        boolean skipping = true;
                        for (StackTraceElement e : stacktrace) {
                            if (skipping && e.getClassName().startsWith("org.lwjgl.")) continue;
                            skipping = false;
                            frames.add("    at " + e);
                            if (frames.size() >= FRAMES) break;
                        }
                    }
                    String key = frames.isEmpty() ? "(no stack trace)" : String.join("\n", frames);
                    long[] a = sites.computeIfAbsent(key, k -> new long[2]);
                    a[0] += memory;
                    a[1]++;
                });
                List<Map.Entry<String, long[]>> ranked = new ArrayList<>(sites.entrySet());
                ranked.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
                String anon = null;
                try {
                    for (String l : Files.readAllLines(Path.of("/proc/self/status")))
                        if (l.startsWith("RssAnon")) { anon = l.trim(); break; }
                } catch (Exception ignored) {}
                Runtime rt = Runtime.getRuntime();
                StringBuilder out = new StringBuilder();
                out.append("LWJGL native memory still allocated: ").append(mb(total[0])).append(" in ").append(total[1])
                        .append(" allocations, ").append(sites.size()).append(" call sites\n");
                out.append("process ").append(anon == null ? "RssAnon ?" : anon).append("; Java heap used ")
                        .append(mb(rt.totalMemory() - rt.freeMemory())).append(" of ").append(mb(rt.maxMemory())).append("\n");
                List<Map.Entry<String, Long>> th = new ArrayList<>(threads.entrySet());
                th.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
                List<String> thr = new ArrayList<>();
                for (int i = 0; i < Math.min(8, th.size()); i++) thr.add(th.get(i).getKey() + " " + mb(th.get(i).getValue()));
                out.append("by thread: ").append(String.join(", ", thr)).append("\n\n");
                for (Map.Entry<String, long[]> e : ranked) {
                    out.append(mb(e.getValue()[0])).append(" in ").append(e.getValue()[1]).append(" allocations\n")
                            .append(e.getKey()).append("\n\n");
                }
                Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("logs").resolve("adm");
                Files.createDirectories(dir);
                Path file = dir.resolve("memreport-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt");
                Files.writeString(file, out);
                say.accept("§7LWJGL native memory: §f" + mb(total[0]) + "§7 in " + total[1] + " allocations. Top call sites:");
                for (int i = 0; i < Math.min(3, ranked.size()); i++) {
                    String first = ranked.get(i).getKey().split("\n", 2)[0].trim();
                    if (first.startsWith("at ")) first = first.substring(3);
                    say.accept("§f " + mb(ranked.get(i).getValue()[0]) + " §7(" + ranked.get(i).getValue()[1] + ") §8" + first);
                }
                say.accept("§7full report: §f" + file);
            } catch (Throwable e) {
                TeslaMaps.LOGGER.error("[adm] memreport failed", e);
            }
        }, "adm-memreport");
        t.setDaemon(true);
        t.start();
    }

    private static String mb(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
    }
}
