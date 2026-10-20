package com.teslamaps.adm.leap.splits;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

// Ported from ADM's splits/SplitPace.kt
public final class SplitPace {

    private SplitPace() {}

    public record Clocks(long ms, long ticks) {
        public Clocks plus(Clocks o) { return new Clocks(ms + o.ms, ticks + o.ticks); }
    }

    public record Sub(String id, Split split) {}

    public static final List<String> ORDER = List.of(SplitTracker.OPEN, SplitTracker.BLOOD, SplitTracker.PORTAL, SplitTracker.MAXOR,
            SplitTracker.STORM, SplitTracker.TERMS, SplitTracker.GOLDOR, SplitTracker.NECRON);

    private static final Set<String> REAL_SPLITS = Set.of(SplitTracker.TERMS);

    public static final Map<String, String> SPLIT_IDS = new HashMap<>();
    public static final List<String> ALL;

    private static final Map<String, String> SUB_PREFIX = Map.of(
            SplitTracker.BLOOD, "watcher.", SplitTracker.MAXOR, "maxor.", SplitTracker.STORM, "storm.",
            SplitTracker.TERMS, "terms.", SplitTracker.GOLDOR, "goldor.", SplitTracker.NECRON, "necron.");

    private static final Set<String> FIXED_SUBS = Set.of("maxor.animation", "storm.opening", "storm.animation", "necron.intro", "necron.lock1");

    private static final Map<String, Clocks> SPLIT_REFS = new HashMap<>();
    private static final Map<String, Clocks> SUB_REFS = new LinkedHashMap<>();

    static {
        SPLIT_IDS.put(SplitTracker.OPEN, "split.open");
        SPLIT_IDS.put(SplitTracker.BLOOD, "split.blood");
        SPLIT_IDS.put(SplitTracker.PORTAL, "split.portal");
        SPLIT_IDS.put(SplitTracker.MAXOR, "split.maxor");
        SPLIT_IDS.put(SplitTracker.STORM, "split.storm");
        SPLIT_IDS.put(SplitTracker.TERMS, "split.terms");
        SPLIT_IDS.put(SplitTracker.GOLDOR, "split.goldor");
        SPLIT_IDS.put(SplitTracker.NECRON, "split.necron");
        SPLIT_IDS.put(SplitTracker.DRAGONS, "split.dragons");
        List<String> all = new ArrayList<>(ORDER);
        all.add(SplitTracker.DRAGONS);
        ALL = List.copyOf(all);

        SPLIT_REFS.put(SplitTracker.OPEN, ticks(160));
        SPLIT_REFS.put(SplitTracker.BLOOD, ticks(920));
        SPLIT_REFS.put(SplitTracker.PORTAL, ticks(76));
        SPLIT_REFS.put(SplitTracker.MAXOR, ticks(272));
        SPLIT_REFS.put(SplitTracker.STORM, ticks(824));
        SPLIT_REFS.put(SplitTracker.TERMS, real(28_000));
        SPLIT_REFS.put(SplitTracker.GOLDOR, ticks(100));
        SPLIT_REFS.put(SplitTracker.NECRON, ticks(425));

        SUB_REFS.put("watcher.dialogue", ticks(255)); SUB_REFS.put("watcher.wait", ticks(63));
        SUB_REFS.put("watcher.camp", ticks(520)); SUB_REFS.put("watcher.clear", ticks(2));
        SUB_REFS.put("maxor.crystals", ticks(110)); SUB_REFS.put("maxor.lure", ticks(18));
        SUB_REFS.put("maxor.cooldown", ticks(85)); SUB_REFS.put("maxor.kill", ticks(1));
        SUB_REFS.put("maxor.animation", ticks(62));
        SUB_REFS.put("storm.opening", ticks(647)); SUB_REFS.put("storm.crush1", ticks(13));
        SUB_REFS.put("storm.pin", ticks(1)); SUB_REFS.put("storm.flight", ticks(86));
        SUB_REFS.put("storm.crush2", ticks(20)); SUB_REFS.put("storm.kill", ticks(2));
        SUB_REFS.put("storm.animation", ticks(62));
        SUB_REFS.put("terms.s1", real(8_700)); SUB_REFS.put("terms.s2", real(7_700));
        SUB_REFS.put("terms.s3", real(7_800)); SUB_REFS.put("terms.s4", real(5_000));
        SUB_REFS.put("goldor.leaps", ticks(9)); SUB_REFS.put("goldor.kill", ticks(85));
        SUB_REFS.put("necron.intro", ticks(82)); SUB_REFS.put("necron.trip1", ticks(12));
        SUB_REFS.put("necron.lock1", ticks(176)); SUB_REFS.put("necron.kill", ticks(155));
    }

    public static Clocks ref(String label) { return SPLIT_REFS.get(label); }
    public static Clocks subRef(String id) { return SUB_REFS.get(id); }

    private static Clocks ticks(long t) { return new Clocks(t * 50, t); }
    private static Clocks real(long ms) { return new Clocks(ms, ms / 50); }

    public static Clocks clocks(String id, long value) {
        return SubSplitGrades.clock(id) == SubSplitGrades.Clock.REAL ? real(value) : ticks(value);
    }

    public static Clocks pace(List<Split> splits, Function<String, List<Sub>> subs, Stamp now, Function<String, Clocks> target) {
        Clocks total = new Clocks(0, 0);
        for (String label : ORDER) {
            Clocks t = target.apply(label);
            Clocks ref = t != null ? t : SPLIT_REFS.get(label);
            if (ref == null) continue;
            Split split = null;
            for (Split s : splits) if (s.label().equals(label)) { split = s; break; }
            if (split == null) total = total.plus(ref);
            else if (split.stop() != null) total = total.plus(length(split, now));
            else total = total.plus(running(label, split, ref, subs.apply(label), now));
        }
        return total;
    }

    private static Clocks running(String label, Split split, Clocks ref, List<Sub> subs, Stamp now) {
        String prefix = SUB_PREFIX.get(label);
        if (prefix == null) return atLeast(ref, length(split, now));
        Clocks fixed = new Clocks(0, 0), varying = new Clocks(0, 0);
        for (Map.Entry<String, Clocks> e : SUB_REFS.entrySet()) {
            if (!e.getKey().startsWith(prefix)) continue;
            if (FIXED_SUBS.contains(e.getKey())) fixed = fixed.plus(e.getValue());
            else varying = varying.plus(e.getValue());
        }
        double kMs = varying.ms > 0 ? Math.max(ref.ms - fixed.ms, 0) / (double) varying.ms : 1.0;
        double kTicks = varying.ticks > 0 ? Math.max(ref.ticks - fixed.ticks, 0) / (double) varying.ticks : 1.0;
        Clocks est = ref;
        for (Sub s : subs) {
            Clocks subRef = SUB_REFS.get(s.id());
            if (subRef == null) continue;
            Clocks share = FIXED_SUBS.contains(s.id()) ? subRef : new Clocks(Math.round(subRef.ms * kMs), Math.round(subRef.ticks * kTicks));
            Clocks len = length(s.split(), now);
            Clocks diff = new Clocks(len.ms - share.ms, len.ticks - share.ticks);
            est = est.plus(s.split().stop() != null ? diff : new Clocks(Math.max(diff.ms, 0), Math.max(diff.ticks, 0)));
        }
        return atLeast(est, length(split, now));
    }

    public static long lag(List<Split> splits, Stamp now) {
        long sum = 0;
        for (Split s : splits) {
            if (REAL_SPLITS.contains(s.label())) continue;
            Clocks c = length(s, now);
            sum += c.ms - c.ticks * 50;
        }
        return Math.max(sum, 0);
    }

    private static Clocks length(Split s, Stamp now) {
        Stamp stop = s.stop() != null ? s.stop() : now;
        return new Clocks(stop.realMs() - s.start().realMs(), stop.tick() - s.start().tick());
    }

    private static Clocks atLeast(Clocks a, Clocks b) { return new Clocks(Math.max(a.ms, b.ms), Math.max(a.ticks, b.ticks)); }

    public static String mss(long ms) {
        long s = Math.max(ms, 0) / 1000;
        long sec = s % 60;
        return (s / 60) + ":" + (sec < 10 ? "0" : "") + sec;
    }
}
