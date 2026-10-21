// Ported from ADM's p3sim/pack/mobs/MobPack.kt
package com.teslamaps.adm.sim.pack.mobs;

import java.util.Objects;

/** Percentile summary of one archetype's hits. */
public final class HitQuantiles {
    public final int n;
    public final double p10;
    public final double p50;
    public final double p90;

    public HitQuantiles(int n, double p10, double p50, double p90) { this.n = n; this.p10 = p10; this.p50 = p50; this.p90 = p90; }

    @Override public boolean equals(Object o) {
        return o instanceof HitQuantiles h && n == h.n && Double.compare(p10, h.p10) == 0 && Double.compare(p50, h.p50) == 0 && Double.compare(p90, h.p90) == 0;
    }
    @Override public int hashCode() { return Objects.hash(n, p10, p50, p90); }
    @Override public String toString() { return "HitQuantiles(n=" + n + ", p10=" + p10 + ", p50=" + p50 + ", p90=" + p90 + ")"; }
}
