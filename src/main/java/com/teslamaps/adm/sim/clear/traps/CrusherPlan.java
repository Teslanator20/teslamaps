package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/TrapTimings.kt

import java.util.Map;

/** What a run settled on for its crushers: the pace of each and the tick of its first step. */
public class CrusherPlan {
    private final Map<String, CrusherTiming> timings;
    private final Map<String, Integer> starts;
    private final TrapTimings fallback;

    public CrusherPlan(Map<String, CrusherTiming> timings, Map<String, Integer> starts, TrapTimings fallback) {
        this.timings = timings;
        this.starts = starts;
        this.fallback = fallback;
    }

    public CrusherTiming timing(String id) {
        CrusherTiming t = timings.get(id);
        return t != null ? t : fallback.forCrusher(id);
    }

    /** The room-clock tick of the crusher's first step; Crusher.START_LAG unless its group spread it. */
    public int startAt(String id) {
        Integer s = starts.get(id);
        return s != null ? s : Crusher.START_LAG;
    }
}
