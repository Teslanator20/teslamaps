package com.teslamaps.safari;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

// Ported from critterMod's LogScanner: replays latest.log + *.log.gz chat into past runs.
public final class SafariLogImport {

    private static final Pattern CHAT_LINE = Pattern.compile("^\\[(\\d{2}):(\\d{2}):(\\d{2})].*?\\[CHAT] (.*)$");
    private static final Pattern LOG_DATE = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})-(\\d+)\\.log(?:\\.gz)?$");
    private static final Pattern ENTERED = Pattern.compile("^(?:\\[[^]]+]\\s*)?(\\w{1,16}) entered Critter Safari!$");
    private static final long GAP = 15 * 60_000L;
    private static volatile boolean running = false;

    private SafariLogImport() {}

    private static final class Building {
        final SafariHistory.Run run = new SafariHistory.Run();
        final Set<String> any = new HashSet<>(), self = new HashSet<>();
        long last;

        Building(long t) {
            run.startedAt = t;
            last = t;
            run.catches = new HashMap<>();
            run.ownCatches = new HashMap<>();
            run.attempts = new HashMap<>();
            run.shards = new HashMap<>();
            run.imported = true;
        }

        SafariHistory.Run finish() {
            run.durationMs = last - run.startedAt;
            run.dex = any.size();
            run.own = self.size();
            return run;
        }
    }

    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        if (running) { msg(mc, "§7Import already running"); return; }
        running = true;
        String me = mc.getUser().getName();
        msg(mc, "§7Scanning logs for Safari runs...");
        Thread t = new Thread(() -> {
            List<SafariHistory.Run> found;
            try {
                found = scan(FabricLoader.getInstance().getGameDir().resolve("logs"), me);
            } catch (Exception e) {
                running = false;
                mc.execute(() -> msg(mc, "§cImport failed: " + e.getMessage()));
                return;
            }
            mc.execute(() -> {
                running = false;
                int[] r = SafariHistory.importRuns(found);
                msg(mc, "§7Found §f" + found.size() + " §7runs in the logs: §a" + r[0] + " added§7, §e" + r[1] + " filled in");
            });
        }, "TeslaMaps-SafariImport");
        t.setDaemon(true);
        t.start();
    }

    private static void msg(Minecraft mc, String s) {
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] " + s));
    }

    static List<SafariHistory.Run> scan(Path dir, String me) throws Exception {
        List<Path> files;
        try (var s = Files.list(dir)) {
            files = s.filter(p -> {
                String n = p.getFileName().toString();
                return n.endsWith(".log") || n.endsWith(".log.gz");
            }).sorted(Comparator.comparing(SafariLogImport::sortKey)).toList();
        }
        List<SafariHistory.Run> out = new ArrayList<>();
        Building cur = null;
        for (Path f : files) {
            LocalDate date = dateOf(f);
            long prev = 0;
            int dayShift = 0;
            for (String raw : lines(f)) {
                Matcher c = CHAT_LINE.matcher(raw);
                if (!c.matches()) continue;
                long ts = millis(date.plusDays(dayShift), c.group(1), c.group(2), c.group(3));
                if (prev != 0 && ts < prev - 12 * 3_600_000L) { dayShift++; ts += 86_400_000L; }
                prev = ts;
                for (String part : c.group(4).split("\\\\n")) {
                    String line = SafariTracker.COLORS.matcher(part).replaceAll("").trim();
                    if (line.isEmpty()) continue;
                    Matcher en = ENTERED.matcher(line);
                    boolean entered = en.matches() && en.group(1).equals(me);
                    if (!entered && !isSafari(line)) continue;
                    if (entered || cur == null || ts - cur.last > GAP) {
                        if (cur != null) keep(out, cur);
                        cur = new Building(ts);
                    }
                    cur.last = ts;
                    apply(cur, line, me);
                }
            }
        }
        if (cur != null) keep(out, cur);
        return out;
    }

    private static void keep(List<SafariHistory.Run> out, Building b) {
        SafariHistory.Run r = b.finish();
        if (r.dex > 0 && r.durationMs >= 60_000) out.add(r);
    }

    private static boolean isSafari(String l) {
        return l.startsWith("CAPTURE! ") || l.startsWith("LOOT SHARE! You received") || l.startsWith("You threw a Critter Capsule")
                || l.contains("your Critter Capsule") || l.contains("your critter capsule") || l.startsWith("HOTSPOT! ")
                || l.startsWith("The mound f");
    }

    private static void apply(Building b, String l, String me) {
        SafariHistory.Run r = b.run;
        Matcher m;
        if ((m = SafariTracker.CAUGHT.matcher(l)).matches() || (m = SafariTracker.FOUND.matcher(l)).matches()) {
            SafariData.Critter c = SafariData.byName(m.group(1));
            if (c == null) return;
            r.catches.merge(c.name(), 1, Integer::sum);
            r.ownCatches.merge(c.name(), 1, Integer::sum);
            r.shards.merge(c.name(), SafariTracker.shardAmount(l), Integer::sum);
            b.any.add(c.name());
            b.self.add(c.name());
        } else if ((m = SafariTracker.LOOT_SHARE.matcher(l)).matches()) {
            SafariData.Critter c = SafariData.byName(m.group(2));
            if (c == null) return;
            r.catches.merge(c.name(), 1, Integer::sum);
            r.shards.merge(c.name(), SafariTracker.shardAmount(l), Integer::sum);
            b.any.add(c.name());
        } else if ((m = SafariTracker.THREW.matcher(l)).matches()) {
            r.capsules++;
            SafariData.Critter c = SafariData.byName(m.group(1));
            if (c != null) r.attempts.merge(c.name(), 1, Integer::sum);
        } else if (SafariTracker.ESCAPED.matcher(l).matches() || SafariTracker.DODGED.matcher(l).matches()) {
            r.escapes++;
        } else if ((m = SafariTracker.HOTSPOT.matcher(l)).matches()) {
            for (SafariData.Biome bi : SafariData.Biome.values()) {
                if (bi.label.equalsIgnoreCase(m.group(1))) r.hotspot = bi.label;
            }
        } else if (l.startsWith("The mound falls apart, but nothing is inside")) {
            r.moundsBroken++;
        } else if (l.startsWith("The mound fell apart, revealing")) {
            r.moundsBroken++;
            r.rockmites++;
        }
    }

    private static List<String> lines(Path f) {
        List<String> out = new ArrayList<>();
        boolean gz = f.getFileName().toString().endsWith(".gz");
        try (InputStream in = Files.newInputStream(f);
             InputStream d = gz ? new GZIPInputStream(in) : in;
             BufferedReader r = new BufferedReader(new InputStreamReader(d, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.contains("[CHAT] ")) out.add(line);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static LocalDate dateOf(Path f) {
        Matcher m = LOG_DATE.matcher(f.getFileName().toString());
        if (m.matches()) return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        try {
            return LocalDate.ofInstant(Files.getLastModifiedTime(f).toInstant(), ZoneId.systemDefault());
        } catch (Exception e) {
            return LocalDate.EPOCH;
        }
    }

    private static String sortKey(Path f) {
        String n = f.getFileName().toString();
        Matcher m = LOG_DATE.matcher(n);
        if (!m.matches()) return "9999" + n;
        return m.group(1) + "-" + m.group(2) + "-" + m.group(3) + "-" + String.format("%04d", Integer.parseInt(m.group(4)));
    }

    private static long millis(LocalDate d, String h, String m, String s) {
        return LocalDateTime.of(d, LocalTime.of(Integer.parseInt(h), Integer.parseInt(m), Integer.parseInt(s)))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
