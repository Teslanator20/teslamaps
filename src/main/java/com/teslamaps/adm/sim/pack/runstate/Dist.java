// Ported from ADM's p3sim/pack/runstate/Dist.kt
package com.teslamaps.adm.sim.pack.runstate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.pack.JsonKt;

/**
 * A percentile vector: {@code q[i]} is the value at percent {@code pct[i]}. Durations are server ticks with lag removed.
 */
public class Dist {
    public final int n;
    public final double[] pct;
    public final double[] q;
    public final Double min;
    public final Double max;

    public Dist(int n, double[] pct, double[] q, Double min, Double max) {
        this.n = n;
        this.pct = pct;
        this.q = q;
        this.min = min;
        this.max = max;
        if (!(pct.length == q.length && q.length > 0)) throw new IllegalArgumentException("a distribution needs as many values as percents");
    }

    /** A distribution with fewer than 8 samples is advisory only: prefer the parent table. */
    public boolean advisory() { return n < 8; }

    /** The median (the value at 50 percent). */
    public double median() { return at(50.0); }

    /** The value at percent [p] (0..100). */
    public double at(double p) {
        int last = q.length - 1;
        if (p <= pct[0]) {
            if (min == null) return q[0];
            double lo = min;
            if (pct[0] <= 0.0) return q[0];
            return lo + (q[0] - lo) * (Math.max(p, 0.0) / pct[0]);
        }
        if (p >= pct[last]) {
            if (max == null) return q[last];
            double hi = max;
            if (pct[last] >= 100.0) return q[last];
            return q[last] + (hi - q[last]) * ((Math.min(p, 100.0) - pct[last]) / (100.0 - pct[last]));
        }
        for (int i = 1; i <= last; i++) {
            if (p <= pct[i]) {
                double span = pct[i] - pct[i - 1];
                return span <= 0.0 ? q[i] : q[i - 1] + (q[i] - q[i - 1]) * ((p - pct[i - 1]) / span);
            }
        }
        return q[last];
    }

    /** One sample: [u] uniform in [0,1], piecewise linear over the points. The caller rounds to a tick. */
    public double sample(double u) { return at(100.0 * Math.clamp(u, 0.0, 1.0)); }

    /** The default percents of a {@code Dist} when the object carries no {@code pct} of its own. */
    public static final double[] DEFAULT_PCT = new double[]{5.0, 10.0, 25.0, 50.0, 75.0, 90.0, 95.0};

    public static Dist parse(JsonElement e) { return parse(e, DEFAULT_PCT); }

    /** Reads {@code {n, q, [pct], [min], [max]}}. Null when [e] is null, not an object, has no {@code q}, or the lengths disagree. */
    public static Dist parse(JsonElement e, double[] globalPct) {
        JsonObject o = JsonKt.asObj(e);
        if (o == null) return null;
        double[] q = JsonKt.doubles(JsonKt.arr(o, "q"));
        if (q.length == 0) return null;
        JsonArray pa = JsonKt.arr(o, "pct");
        double[] pct = null;
        if (pa != null) {
            double[] d = JsonKt.doubles(pa);
            if (d.length > 0) pct = d;
        }
        if (pct == null) pct = globalPct;
        if (pct.length != q.length) return null;
        Integer n = JsonKt.int_(o, "n");
        return new Dist(n != null ? n : 0, pct, q, JsonKt.dbl(o, "min"), JsonKt.dbl(o, "max"));
    }
}
