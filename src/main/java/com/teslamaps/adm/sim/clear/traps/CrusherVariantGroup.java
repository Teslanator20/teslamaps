package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/TrapTimings.kt

import java.util.List;

/** Crushers whose pace changes per run: one option is drawn for the whole run. */
public class CrusherVariantGroup {
    public final List<CrusherVariantOption> options;
    public final int startSpread;

    public CrusherVariantGroup(List<CrusherVariantOption> options, int startSpread) {
        this.options = options;
        this.startSpread = startSpread;
    }
}
