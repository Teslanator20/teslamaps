// Ported from ADM's p3sim/run/Lag.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.List;

public final class LagGenerator {
    public static final double TICK_MS = 50.0;
    public static final int TICKS_PER_SECOND = 20;
    public static final double CLUSTER_FROM_MS = 250.0;

    public static int missedFor(double gapMs) { return Math.max((int) Math.round(gapMs / TICK_MS - 1), 1); }

    private static final class Bin {
        final double lo, hi, weight;
        Bin(double lo, double hi, double weight) { this.lo = lo; this.hi = hi; this.weight = weight; }
    }

    public final LagParams params;
    public final double lossPerMinute;
    private final KRandom random;

    private final List<Bin> bins;
    private final List<Bin> small;
    private final List<Bin> big;
    private final double totalPerHour;

    private final double meanLost;
    public final double meanMissed;

    public final double naturalLossPerMinute;
    public final double freezesPerMinute;
    private final double bigShare;
    private final double follow;
    private final double pSmall;
    private final double pBig;

    private int chainIn = -1;
    public long ticks = 0L;
    public long lost = 0L;
    public int events = 0;

    public LagGenerator(LagParams params, double lossPerMinute) { this(params, lossPerMinute, KRandom.DEFAULT); }

    public LagGenerator(LagParams params, double lossPerMinute, KRandom random) {
        this.params = params;
        this.lossPerMinute = lossPerMinute;
        this.random = random;

        List<Pair<Double, Double>> e = params.exceedPerHour;
        List<Bin> b = new ArrayList<>();
        for (int i = 0; i < e.size(); i++) {
            double hi = i + 1 < e.size() ? e.get(i + 1).first : params.maxGapMs;
            b.add(new Bin(e.get(i).first, hi, Math.max(e.get(i).second - (i + 1 < e.size() ? e.get(i + 1).second : 0.0), 0.0)));
        }
        bins = b;
        small = new ArrayList<>();
        big = new ArrayList<>();
        for (Bin x : bins) if (x.lo < CLUSTER_FROM_MS) small.add(x);
        for (Bin x : bins) if (x.lo >= CLUSTER_FROM_MS) big.add(x);
        double tph = 0.0;
        for (Bin x : bins) tph += x.weight;
        totalPerHour = tph;

        double lostSum = 0.0, missedSum = 0.0;
        for (Bin bin : bins) {
            int n = 400;
            double l = 0.0, m = 0.0;
            for (int k = 0; k < n; k++) {
                double gap = Math.exp(Math.log(bin.lo) + (Math.log(bin.hi) - Math.log(bin.lo)) * (k + 0.5) / n);
                int miss = missedFor(gap);
                l += miss - Math.clamp(Math.round(params.recovered((double) miss)), 0L, (long) miss);
                m += miss;
            }
            lostSum += bin.weight * l / n;
            missedSum += bin.weight * m / n;
        }
        meanLost = totalPerHour > 0 ? lostSum / totalPerHour : 0.0;
        meanMissed = totalPerHour > 0 ? missedSum / totalPerHour : 0.0;

        naturalLossPerMinute = totalPerHour * meanLost / 60.0;
        freezesPerMinute = meanLost > 0 ? lossPerMinute / meanLost : 0.0;
        double bigW = 0.0;
        for (Bin x : big) bigW += x.weight;
        bigShare = totalPerHour > 0 ? bigW / totalPerHour : 0.0;

        double perMinute = freezesPerMinute * bigShare;
        double r = 1.0 - Math.exp(-perMinute * 5.0 / 60.0);
        follow = Math.clamp((params.within5sFrac - r) / (1.0 - r), 0.0, 0.9);

        pSmall = freezesPerMinute * (1.0 - bigShare) / (60.0 * TICKS_PER_SECOND);
        pBig = freezesPerMinute * bigShare * (1.0 - follow) / (60.0 * TICKS_PER_SECOND);
    }

    public LagEvent nextTick() {
        ticks++;
        LagEvent ev = null;
        if (chainIn > 0) chainIn--;
        else if (chainIn == 0) { chainIn = -1; ev = draw(big); }
        if (ev == null && pBig > 0 && random.nextDouble() < pBig) ev = draw(big);
        if (ev != null) {
            if (chainIn < 0 && random.nextDouble() < follow) chainIn = Math.max(1 + random.nextInt(5 * TICKS_PER_SECOND) - ev.missedTicks, 0);
            return count(ev);
        }
        if (pSmall > 0 && random.nextDouble() < pSmall) return count(draw(small));
        return null;
    }

    private LagEvent count(LagEvent ev) { events++; lost += ev.lostTicks(); return ev; }

    private LagEvent draw(List<Bin> from) {
        List<Bin> pool = from.isEmpty() ? bins : from;
        double sum = 0.0;
        for (Bin x : pool) sum += x.weight;
        double u = random.nextDouble() * sum;
        Bin bin = pool.get(pool.size() - 1);
        for (Bin b : pool) { if (u < b.weight) { bin = b; break; } u -= b.weight; }
        double gap = Math.exp(Math.log(bin.lo) + (Math.log(bin.hi) - Math.log(bin.lo)) * random.nextDouble());
        int missed = missedFor(gap);
        return new LagEvent(gap, missed, Math.max(0, Math.min((int) Math.round(params.recovered((double) missed)), missed)));
    }
}
