// Ported from ADM's p3sim/run/Lag.kt
package com.teslamaps.adm.sim.run;

public final class LagBurst {
    private LagBurst() {}

    public static final double DEFAULT_RATE = 2.0;

    public static double sleepMs(double sinceLastMs, double rate) {
        return Math.max(LagGenerator.TICK_MS / Math.max(rate, 1.0) - sinceLastMs, 0.0);
    }

    public static int ticksFor(int recovered, double rate) {
        return rate <= 1.0001 ? recovered : (int) Math.ceil(recovered * rate / (rate - 1.0));
    }
}
