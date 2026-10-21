package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/TrapSites.kt

import java.util.List;

/** A floor that falls: design id, trigger (`stand` or `string` with hook), the cluster cell to keep, and extra boxes that go with it. */
public class FallRule {
    public final String design;
    public final String trigger;
    public final int[] hook;
    public final int[] cluster;
    public final List<int[]> also;

    public FallRule(String design, String trigger, int[] hook, int[] cluster, List<int[]> also) {
        this.design = design;
        this.trigger = trigger;
        this.hook = hook;
        this.cluster = cluster;
        this.also = also;
    }
}
