package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/ArrowBank.kt

import java.util.List;

/** A bank of dispensers that fires its volley on a fixed beat (every rule.period ticks of its clock, shifted by phase). Pure. */
public class ArrowBank implements Hazard {
    public final ArrowRule rule;
    public final int phase;

    public ArrowBank(ArrowRule rule, int phase) {
        this.rule = rule;
        this.phase = phase;
    }

    @Override
    public void tick(int clock, List<PlayerPos> players, HazardOut out) {
        if (Math.floorMod(clock + phase, rule.period) == 0) out.volleys.add(rule);
    }
}
