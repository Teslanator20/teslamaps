// Ported from ADM's p3sim/run/Lag.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.pack.runstate.LagTier;
import java.util.Map;

public final class LagPresets {
    private LagPresets() {}

    public static double lossPerMinute(LagPreset preset, Map<String, LagTier> tiers) {
        if (preset == LagPreset.OFF) return 0.0;
        LagTier t = tiers != null ? tiers.get("F7") : null;
        if (t == null) return preset.lossPerMinute;
        return switch (preset) {
            case GOOD -> t.good;
            case TYPICAL -> t.typical;
            case BAD -> t.bad;
            case HIGH_SLOT -> t.slotOver200 != null ? t.slotOver200 : preset.lossPerMinute;
            case OFF -> 0.0;
        };
    }
}
