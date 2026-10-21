// Ported from ADM's p3sim/pack/mobs/MobPack.kt
package com.teslamaps.adm.sim.pack.mobs;

import java.util.Map;
import java.util.Objects;

/** Count statistics of mobs per recorded visit. */
public final class CountStats {
    public final double mean;
    public final double p50;
    public final double p90;
    public final double max;
    public final Map<Integer, Integer> hist;

    public CountStats(double mean, double p50, double p90, double max, Map<Integer, Integer> hist) {
        this.mean = mean; this.p50 = p50; this.p90 = p90; this.max = max; this.hist = hist;
    }

    @Override public boolean equals(Object o) {
        return o instanceof CountStats c && Double.compare(mean, c.mean) == 0 && Double.compare(p50, c.p50) == 0 && Double.compare(p90, c.p90) == 0
                && Double.compare(max, c.max) == 0 && Objects.equals(hist, c.hist);
    }
    @Override public int hashCode() { return Objects.hash(mean, p50, p90, max, hist); }
    @Override public String toString() { return "CountStats(mean=" + mean + ", p50=" + p50 + ", p90=" + p90 + ", max=" + max + ", hist=" + hist + ")"; }
}
