package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/TrapSites.kt

import java.util.List;

/** A bank of dispensers that fire an arrow each (perDispenser of them), all in one tick, every period ticks. */
public class ArrowRule {
    public final String design;
    public final int period;
    public final int perDispenser;
    public final List<Emitter> dispensers;

    public ArrowRule(String design, int period, int perDispenser, List<Emitter> dispensers) {
        this.design = design;
        this.period = period;
        this.perDispenser = perDispenser;
        this.dispensers = dispensers;
    }
}
