package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/TrapTimings.kt

import java.util.Map;

/** One pace option of a crusher group; sharedStart = its crushers begin on the same tick. */
public class CrusherVariantOption {
    public final Map<String, CrusherTiming> crushers;
    public final boolean sharedStart;

    public CrusherVariantOption(Map<String, CrusherTiming> crushers, boolean sharedStart) {
        this.crushers = crushers;
        this.sharedStart = sharedStart;
    }
}
