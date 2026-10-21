// Ported from ADM's p3sim/run/RunConfig.kt
package com.teslamaps.adm.sim.run;

public enum LagPreset {
    OFF("Off", 0.0), GOOD("Good", 14.5), TYPICAL("Typical", 30.3), BAD("Bad", 56.6),
    /** 1.8 times the typical loss. */
    HIGH_SLOT("High slot", 54.6);

    public final String label;
    public final double lossPerMinute;

    LagPreset(String label, double lossPerMinute) {
        this.label = label;
        this.lossPerMinute = lossPerMinute;
    }
}
