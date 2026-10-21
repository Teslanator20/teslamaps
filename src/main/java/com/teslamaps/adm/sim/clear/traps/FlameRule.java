package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/TrapSites.kt

import java.util.List;

/** The flamethrowers of one part of a room; region (x0, y0, z0, x1, y1, z1) or null, perBeat range, optional pickRange. */
public class FlameRule {
    public final String design;
    public final List<Emitter> emitters;
    public final int[] region;
    public final int perBeatMin;
    public final int perBeatMax;
    public final Double pickRange;

    public FlameRule(String design, List<Emitter> emitters, int[] region, int perBeatMin, int perBeatMax, Double pickRange) {
        this.design = design;
        this.emitters = emitters;
        this.region = region;
        this.perBeatMin = perBeatMin;
        this.perBeatMax = perBeatMax;
        this.pickRange = pickRange;
    }
}
