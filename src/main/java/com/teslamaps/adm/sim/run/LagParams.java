// Ported from ADM's p3sim/run/Lag.kt
package com.teslamaps.adm.sim.run;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LagParams {
    public final List<Pair<Double, Double>> exceedPerHour;
    public final List<Pair<Double, Double>> recovery;
    public final double within5sFrac;
    public final double randomWithin5s;
    public final double maxGapMs;

    public LagParams(List<Pair<Double, Double>> exceedPerHour, List<Pair<Double, Double>> recovery, double within5sFrac, double randomWithin5s, double maxGapMs) {
        this.exceedPerHour = exceedPerHour;
        this.recovery = recovery;
        this.within5sFrac = within5sFrac;
        this.randomWithin5s = randomWithin5s;
        this.maxGapMs = maxGapMs;
    }

    public LagParams(List<Pair<Double, Double>> exceedPerHour, List<Pair<Double, Double>> recovery, double within5sFrac, double randomWithin5s) {
        this(exceedPerHour, recovery, within5sFrac, randomWithin5s, 12_000.0);
    }

    public double recovered(double missed) {
        if (missed <= 0) return 0.0;
        List<Pair<Double, Double>> pts = recovery;
        if (pts.isEmpty()) return 0.0;
        Pair<Double, Double> first = pts.get(0);
        if (missed <= first.first) return first.second * missed / Math.max(first.first, 1e-9);
        for (int i = 1; i < pts.size(); i++) {
            double x0 = pts.get(i - 1).first, y0 = pts.get(i - 1).second;
            double x1 = pts.get(i).first, y1 = pts.get(i).second;
            if (missed <= x1) return Math.min(y0 + (y1 - y0) * (missed - x0) / Math.max(x1 - x0, 1e-9), missed);
        }
        return Math.min(pts.get(pts.size() - 1).second, missed);
    }

    public static final LagParams DEFAULTS = new LagParams(
            List.of(Pair.of(100.0, 1230.25), Pair.of(250.0, 111.24), Pair.of(500.0, 36.09), Pair.of(1000.0, 23.1), Pair.of(2000.0, 16.33), Pair.of(5000.0, 0.72)),
            List.of(Pair.of(5.6, 0.0), Pair.of(12.2, 6.4), Pair.of(38.6, 9.9), Pair.of(78.2, 8.6)),
            0.313, 0.167);

    public static LagParams fromPack(JsonObject lag) {
        if (lag == null) return DEFAULTS;
        JsonElement fe = lag.get("freezes");
        if (fe == null || !fe.isJsonObject()) return DEFAULTS;
        JsonObject f = fe.getAsJsonObject();

        List<Pair<Double, Double>> exceed = null;
        JsonElement ee = f.get("exceedPerHour");
        if (ee != null && ee.isJsonArray()) {
            List<Pair<Double, Double>> list = new ArrayList<>();
            for (JsonElement e : ee.getAsJsonArray()) {
                if (!e.isJsonObject()) continue;
                JsonObject o = e.getAsJsonObject();
                Double g = num(o, "gapMs");
                if (g == null) continue;
                Double h = num(o, "perHour");
                if (h == null) continue;
                list.add(Pair.of(g, h));
            }
            list.sort(Comparator.comparingDouble(p -> p.first));
            if (list.size() >= 2) exceed = list;
        }
        if (exceed == null) exceed = DEFAULTS.exceedPerHour;

        List<Pair<Double, Double>> by = null;
        JsonElement ce = f.get("catchup");
        if (ce != null && ce.isJsonObject()) {
            JsonElement be = ce.getAsJsonObject().get("byGap");
            if (be != null && be.isJsonArray()) {
                JsonArray arr = be.getAsJsonArray();
                List<Pair<Double, Double>> list = new ArrayList<>();
                for (JsonElement e : arr) {
                    if (!e.isJsonObject()) continue;
                    JsonObject o = e.getAsJsonObject();
                    Double m = num(o, "missedTicks");
                    if (m == null) continue;
                    Double r = num(o, "recoveredWithin5s");
                    if (r == null) continue;
                    list.add(Pair.of(m, Math.max(r, 0.0)));
                }
                list.sort(Comparator.comparingDouble(p -> p.first));
                if (!list.isEmpty()) by = list;
            }
        }
        if (by == null) by = DEFAULTS.recovery;

        JsonElement cle = f.get("clustering");
        JsonObject c = cle != null && cle.isJsonObject() ? cle.getAsJsonObject() : null;
        Double w = c != null ? num(c, "within5sFrac") : null;
        Double r = c != null ? num(c, "randomExpected") : null;
        return new LagParams(exceed, by, w != null ? w : DEFAULTS.within5sFrac, r != null ? r : DEFAULTS.randomWithin5s);
    }

    private static Double num(JsonObject o, String k) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;
        return e.getAsDouble();
    }
}
