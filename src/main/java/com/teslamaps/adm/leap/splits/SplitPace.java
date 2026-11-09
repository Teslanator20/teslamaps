package com.teslamaps.adm.leap.splits;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Ported from ADM's splits/SplitPace.kt
public final class SplitPace {

    private SplitPace() {}

    public record Clocks(long ms, long ticks) {
        public Clocks plus(Clocks o) { return new Clocks(ms + o.ms, ticks + o.ticks); }
    }

    public static final List<String> ORDER = List.of(SplitTracker.OPEN, SplitTracker.BLOOD, SplitTracker.PORTAL, SplitTracker.MAXOR,
            SplitTracker.STORM, SplitTracker.TERMS, SplitTracker.GOLDOR, SplitTracker.NECRON);

    public static final Map<String, String> SPLIT_IDS = new HashMap<>();
    public static final List<String> ALL;

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
}
