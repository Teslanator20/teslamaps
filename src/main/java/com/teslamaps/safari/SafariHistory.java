package com.teslamaps.safari;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

// Finished runs on disk, plus the personal bests derived from them.
public class SafariHistory {

    public static class Run {
        public long startedAt;
        public long durationMs;
        public int dex;          // unique critters the party got
        public int own;          // what you caught yourself
        public int capsules;
        public int escapes;
        public int floorDrops;
        public String hotspot;
        public java.util.Map<String, Integer> catches; // party catches per critter, null on old runs
        public java.util.Map<String, Integer> attempts;
        public java.util.Map<String, Integer> failures;
        public java.util.Map<String, Integer> ownCatches;
        public java.util.Map<String, Integer> shards;
        public int moundsBroken;
        public int rockmites;
        public boolean imported;
    }

    public static long totalTime() {
        long t = 0;
        for (Run r : runs()) t += r.durationMs;
        return t;
    }

    public static int fullDexRuns() {
        int n = 0;
        for (Run r : runs()) if (r.dex >= SafariData.ALL.size()) n++;
        return n;
    }

    /** {catches, runs caught in} over the runs that kept a per-critter breakdown. */
    public static int[] statFor(String critter) {
        int total = 0, seen = 0;
        for (Run r : runs()) {
            if (r.catches == null) continue;
            int n = r.catches.getOrDefault(critter, 0);
            total += n;
            if (n > 0) seen++;
        }
        return new int[] {total, seen};
    }

    public static int runsWithBreakdown() {
        int n = 0;
        for (Run r : runs()) if (r.catches != null) n++;
        return n;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<List<Run>>() {}.getType();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("teslamaps").resolve("safari_runs.json");
    private static final int KEEP = 500;

    private static List<Run> runs = new ArrayList<>();
    private static boolean loaded = false;

    public static List<Run> runs() {
        if (!loaded) load();
        return runs;
    }

    public static void add(Run run) {
        if (!loaded) load();
        runs.add(run);
        while (runs.size() > KEEP) runs.remove(0);
        save();
    }

    /** Best dex of any run, 0 if there is no history yet. */
    public static int bestDex() {
        int best = 0;
        for (Run r : runs()) best = Math.max(best, r.dex);
        return best;
    }

    public static int bestOwn() {
        int best = 0;
        for (Run r : runs()) best = Math.max(best, r.own);
        return best;
    }

    /** Fastest run that reached the full 37, 0 if none did. */
    public static long fastestFullDex() {
        long best = 0;
        for (Run r : runs()) {
            if (r.dex < SafariData.ALL.size() || r.durationMs <= 0) continue;
            if (best == 0 || r.durationMs < best) best = r.durationMs;
        }
        return best;
    }

    private static int get(java.util.Map<String, Integer> m, String k) {
        return m == null ? 0 : m.getOrDefault(k, 0);
    }

    /** Per-critter totals over every run: party, own, tries, shards, runs caught in. */
    public static List<String> speciesSummary() {
        List<String> out = new ArrayList<>();
        List<Run> all = runs();
        if (all.isEmpty()) {
            out.add("§7No Safari runs recorded yet. §8/tmap safari import");
            return out;
        }
        int perfect = 0, mounds = 0, mites = 0;
        for (Run r : all) {
            if (r.dex >= SafariData.ALL.size()) perfect++;
            mounds += r.moundsBroken;
            mites += r.rockmites;
        }
        out.add("§b§lSafari species §7- §f" + all.size() + " runs §8| §6" + perfect + " §7perfect");
        out.add("§8name: party §7/ own §8/ tries §8/ shards §8/ runs");
        List<String> never = new ArrayList<>();
        for (SafariData.Critter c : SafariData.ALL) {
            int party = 0, own = 0, tries = 0, sh = 0, seen = 0;
            for (Run r : all) {
                int p = get(r.catches, c.name());
                party += p;
                own += get(r.ownCatches, c.name());
                tries += get(r.attempts, c.name());
                sh += get(r.shards, c.name());
                if (p > 0) seen++;
            }
            if (party == 0) { never.add(c.name()); continue; }
            out.add("§f" + c.name() + "§8: §f" + party + " §8/ §a" + own + " §8/ §7" + tries
                    + " §8/ §d" + sh + " §8/ §7" + seen);
        }
        if (!never.isEmpty()) out.add("§cNever caught: §7" + String.join(", ", never));
        if (mounds > 0) out.add("§7Mounds broken: §f" + mounds + " §8-> §f" + mites + " Rockmite");
        return out;
    }

    /** Adds imported runs, fills per-critter data into matching ones. {added, filled}. */
    public static int[] importRuns(List<Run> imported) {
        if (!loaded) load();
        int added = 0, filled = 0;
        for (Run r : imported) {
            Run same = sameRun(r);
            if (same != null) {
                boolean f = false;
                if (same.catches == null && r.catches != null) { same.catches = r.catches; f = true; }
                if (same.ownCatches == null && r.ownCatches != null) { same.ownCatches = r.ownCatches; f = true; }
                if (same.attempts == null && r.attempts != null) { same.attempts = r.attempts; f = true; }
                if (same.shards == null && r.shards != null) { same.shards = r.shards; f = true; }
                if (same.moundsBroken == 0 && r.moundsBroken > 0) { same.moundsBroken = r.moundsBroken; same.rockmites = r.rockmites; f = true; }
                if (f) filled++;
                continue;
            }
            runs.add(r);
            added++;
        }
        runs.sort((a, b) -> Long.compare(a.startedAt, b.startedAt));
        while (runs.size() > KEEP) runs.remove(0);
        if (added > 0 || filled > 0) save();
        return new int[] {added, filled};
    }

    // same run = half of the shorter one overlaps, runs come back to back
    private static Run sameRun(Run r) {
        Run best = null;
        long bestOverlap = 0;
        for (Run e : runs) {
            long shared = Math.min(e.startedAt + e.durationMs, r.startedAt + r.durationMs) - Math.max(e.startedAt, r.startedAt);
            if (shared <= 0 || shared <= bestOverlap) continue;
            if (shared * 2 < Math.min(e.durationMs, r.durationMs)) continue;
            best = e;
            bestOverlap = shared;
        }
        return best;
    }

    public static List<String> summary(int lastN) {
        List<String> out = new ArrayList<>();
        List<Run> all = runs();
        if (all.isEmpty()) {
            out.add("§7No Safari runs recorded yet.");
            return out;
        }

        int totalDex = 0, totalOwn = 0, totalCapsules = 0, totalEscapes = 0;
        long totalTime = 0;
        for (Run r : all) {
            totalDex += r.dex;
            totalOwn += r.own;
            totalCapsules += r.capsules;
            totalEscapes += r.escapes;
            totalTime += r.durationMs;
        }

        out.add("§b§lCritter Safari §7- §f" + all.size() + " runs");
        out.add("§7Total time: §f" + time(totalTime) + " §8| §7Dex avg: §f"
                + String.format("%.1f", totalDex / (float) all.size())
                + " §8| §7Own avg: §f" + String.format("%.1f", totalOwn / (float) all.size()));
        out.add("§7Capsules: §f" + totalCapsules + " §8| §7Escapes: §c" + totalEscapes
                + " §8(§7" + (totalCapsules == 0 ? "0" : Math.round(100.0 * totalEscapes / totalCapsules)) + "%§8)");

        out.add("§6PB §7Dex: §f" + bestDex() + "§7/" + SafariData.ALL.size()
                + " §8| §6PB §7Own: §f" + bestOwn());
        long full = fastestFullDex();
        out.add("§6PB §7Full dex: " + (full == 0 ? "§8not yet" : "§f" + time(full)));

        int from = Math.max(0, all.size() - lastN);
        out.add("§7Last " + (all.size() - from) + " runs:");
        for (int i = all.size() - 1; i >= from; i--) {
            Run r = all.get(i);
            out.add("§8 - §f" + time(r.durationMs) + " §7dex §f" + r.dex + "§7/" + SafariData.ALL.size()
                    + " §7own §f" + r.own + " §7caps §f" + r.capsules + " §7esc §c" + r.escapes
                    + (r.hotspot == null ? "" : " §8[" + r.hotspot + "]"));
        }
        return out;
    }

    public static String time(long ms) {
        long secs = ms / 1000;
        return String.format("%d:%02d", secs / 60, secs % 60);
    }

    private static void load() {
        loaded = true;
        try {
            if (!Files.exists(PATH)) return;
            List<Run> read = GSON.fromJson(Files.readString(PATH), TYPE);
            if (read != null) runs = read;
        } catch (Exception e) {
            runs = new ArrayList<>();
        }
    }

    private static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(runs, TYPE));
        } catch (IOException ignored) {
        }
    }
}
