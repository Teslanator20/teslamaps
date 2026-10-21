package com.teslamaps.adm.sim.clear.damage;

// Ported from ADM's p3sim/clear/damage/MeleeSampler.kt

import com.teslamaps.adm.sim.pack.mobs.HitQuantiles;
import com.teslamaps.adm.sim.pack.mobs.MeleeTable;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;

public final class MeleeSampler {
    private MeleeSampler() {}

    public static final HitQuantiles FALLBACK = new HitQuantiles(2271, 0.52, 2.06, 10.0);

    public static double sample(MeleeTable table, KRandom random) {
        double atomTotal = 0.0;
        for (MeleeTable.Atom a : table.atoms) atomTotal += a.p;
        double r = random.nextDouble();
        if (atomTotal > 0.0 && r < atomTotal) {
            double acc = 0.0;
            for (MeleeTable.Atom a : table.atoms) {
                acc += a.p;
                if (r < acc) return a.value;
            }
            return table.atoms.get(table.atoms.size() - 1).value;
        }
        return fromQuantiles(table.quantiles, random.nextDouble() * 100.0);
    }

    public static double fromQuantiles(SortedMap<Double, Double> q, double u) {
        if (q.isEmpty()) return FALLBACK.p50;
        List<Double> keys = new ArrayList<>(q.keySet());
        double first = keys.get(0);
        double last = keys.get(keys.size() - 1);
        if (u <= first) return q.get(keys.get(0));
        if (u >= last) return q.get(keys.get(keys.size() - 1));
        Double hi = null;
        for (Double k : keys) if (k >= u) { hi = k; break; }
        Double lo = null;
        for (int i = keys.size() - 1; i >= 0; i--) if (keys.get(i) <= u) { lo = keys.get(i); break; }
        if (hi == null || lo == null) throw new java.util.NoSuchElementException("Collection contains no element matching the predicate.");
        if (hi.doubleValue() == lo.doubleValue()) return q.get(lo);
        double a = lg(q.get(lo));
        double b = lg(q.get(hi));
        return Math.exp(a + (b - a) * ((u - lo) / (hi - lo)));
    }

    public static double fromArchetype(HitQuantiles h, KRandom random) {
        double mu = lg(h.p50);
        double z = gaussian(random);
        double sigma = z >= 0 ? (lg(h.p90) - mu) / 1.2816 : (mu - lg(h.p10)) / 1.2816;
        return Math.clamp(Math.exp(mu + Math.max(sigma, 0.0) * z), 0.01, 100.0);
    }

    private static double lg(double v) { return Math.log(v <= 0.0 ? 1e-3 : v); }

    private static double gaussian(KRandom random) {
        double u1 = Math.max(random.nextDouble(), 1e-12);
        double u2 = random.nextDouble();
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }
}
