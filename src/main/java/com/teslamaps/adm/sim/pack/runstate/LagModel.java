// Ported from ADM's p3sim/pack/runstate/RunStatePack.kt
package com.teslamaps.adm.sim.pack.runstate;

import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.util.Pair;

import java.util.List;
import java.util.Map;

public class LagModel {
    public final double typicalLossPerMin;
    public final Map<String, LagTier> tiers;
    public final Double runLevelMedian, runLevelSigma, runLevelP10, runLevelP90;
    /** {@code [gapMs, perHour]}: freezes at least this long per hour. */
    public final List<Pair<Double, Double>> freezeExceedPerHour;
    public final double[] tickIntervalMsPct, tickIntervalMsQ;
    public final Double recoverFracWithin5s, burstRateTicksPerSec;
    public final Double rttMedianMs;
    public final JsonObject raw;

    public LagModel(double typicalLossPerMin, Map<String, LagTier> tiers, Double runLevelMedian, Double runLevelSigma, Double runLevelP10,
                    Double runLevelP90, List<Pair<Double, Double>> freezeExceedPerHour, double[] tickIntervalMsPct, double[] tickIntervalMsQ,
                    Double recoverFracWithin5s, Double burstRateTicksPerSec, Double rttMedianMs, JsonObject raw) {
        this.typicalLossPerMin = typicalLossPerMin;
        this.tiers = tiers;
        this.runLevelMedian = runLevelMedian;
        this.runLevelSigma = runLevelSigma;
        this.runLevelP10 = runLevelP10;
        this.runLevelP90 = runLevelP90;
        this.freezeExceedPerHour = freezeExceedPerHour;
        this.tickIntervalMsPct = tickIntervalMsPct;
        this.tickIntervalMsQ = tickIntervalMsQ;
        this.recoverFracWithin5s = recoverFracWithin5s;
        this.burstRateTicksPerSec = burstRateTicksPerSec;
        this.rttMedianMs = rttMedianMs;
        this.raw = raw;
    }
}
