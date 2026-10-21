// Ported from ADM's p3sim/clear/bots/PlanMath.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.pack.runstate.Dist;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.List;

/** The small pure maths of the ClearPlan: correlated draws and distribution helpers. */
public final class PlanMath {
    private PlanMath() {}

    /** The standard normal CDF (Abramowitz and Stegun 26.2.17). */
    public static double phi(double z) {
        double t = 1.0 / (1.0 + 0.2316419 * Math.abs(z));
        double d = 0.3989423 * Math.exp(-z * z / 2.0);
        double p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
        return z >= 0 ? 1.0 - p : p;
    }

    /** One standard normal draw (Box-Muller). */
    public static double gauss(KRandom r) {
        return Math.sqrt(-2.0 * Math.log(1.0 - r.nextDouble())) * Math.cos(2.0 * Math.PI * r.nextDouble());
    }

    /** The percent (0..100) at which [d] reaches [value]. */
    public static double percentOf(Dist d, double value) {
        if (value <= d.q[0]) {
            Double lo = d.min;
            if (lo == null) return d.pct[0];
            if (d.q[0] <= lo) return d.pct[0];
            return Math.clamp(d.pct[0] * ((value - lo) / (d.q[0] - lo)), 0.0, d.pct[0]);
        }
        int last = d.q.length - 1;
        if (value >= d.q[last]) {
            Double hi = d.max;
            if (hi == null) return d.pct[last];
            if (hi <= d.q[last]) return d.pct[last];
            return Math.clamp(d.pct[last] + (100.0 - d.pct[last]) * ((value - d.q[last]) / (hi - d.q[last])), d.pct[last], 100.0);
        }
        for (int i = 1; i <= last; i++) if (value <= d.q[i]) {
            double span = d.q[i] - d.q[i - 1];
            return span <= 0.0 ? d.pct[i] : d.pct[i - 1] + (d.pct[i] - d.pct[i - 1]) * ((value - d.q[i - 1]) / span);
        }
        return d.pct[last];
    }

    /** A draw from [d] at uniform [u] given that the value is at least [floor]. */
    public static double sampleAtLeast(Dist d, double u, double floor) {
        double p0 = percentOf(d, floor);
        if (p0 >= 99.999) return Math.max(floor, d.max != null ? d.max : d.q[d.q.length - 1]);
        return Math.max(floor, d.at(p0 + (100.0 - p0) * Math.clamp(u, 0.0, 1.0)));
    }

    /** The value at share [u] (0..1) of a curve given as ascending (share, value) points. */
    public static double curve(List<Pair<Double, Double>> points, double u) {
        if (points.isEmpty()) return 0.0;
        if (u <= points.get(0).first) return points.get(0).second;
        for (int i = 1; i < points.size(); i++) {
            double p1 = points.get(i).first, v1 = points.get(i).second;
            if (u <= p1) {
                double p0 = points.get(i - 1).first, v0 = points.get(i - 1).second;
                return p1 <= p0 ? v1 : v0 + (v1 - v0) * (u - p0) / (p1 - p0);
            }
        }
        return points.get(points.size() - 1).second;
    }

    /** A distribution that is just a shape around a median. [shape] are the multipliers at 5,10,25,50,75,90,95 percent. */
    public static Dist around(double median, double[] shape) {
        double[] q = new double[shape.length];
        for (int i = 0; i < shape.length; i++) q[i] = median * shape[i];
        return new Dist(0, Dist.DEFAULT_PCT, q, null, null);
    }

    public static Dist around(double median) { return around(median, DEFAULT_SHAPE); }

    /** A right-skewed shape typical of the measured room and door times (median 1). */
    public static final double[] DEFAULT_SHAPE = new double[]{0.15, 0.3, 0.6, 1.0, 1.6, 2.4, 3.1};

    /** The "pace of this party" draw. */
    public static class Pace {
        private final KRandom r;
        private final double rho;
        private final double z0;

        public Pace(KRandom r, double rho) {
            this.r = r;
            this.rho = rho;
            this.z0 = gauss(r);
        }

        /** The next uniform in (0,1), correlated with the others by [rho]. */
        public double u() { return phi(rho * z0 + Math.sqrt(1.0 - rho * rho) * gauss(r)); }
    }

    static List<Pair<Double, Double>> pts(double... v) {
        java.util.ArrayList<Pair<Double, Double>> out = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < v.length; i += 2) out.add(Pair.of(v[i], v[i + 1]));
        return out;
    }
}
