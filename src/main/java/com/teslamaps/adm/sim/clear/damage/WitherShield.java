package com.teslamaps.adm.sim.clear.damage;

// Ported from ADM's p3sim/clear/damage/WitherShield.kt

public class WitherShield {
    private final double initialPool;
    private int until = Integer.MIN_VALUE;
    private double pool = 0.0;

    public WitherShield(double initialPool) { this.initialPool = initialPool; }

    public boolean active(int t) { return t < until && pool > 0.0; }

    public boolean start(int t) {
        if (t < until) return false;
        until = t + DURATION_TICKS;
        pool = initialPool;
        return true;
    }

    public double absorb(int t, double percent) {
        if (!active(t)) return percent;
        double soaked = Math.min(pool, percent);
        pool -= soaked;
        return percent - soaked;
    }

    public double absorption(int t) { return active(t) && initialPool > 0.0 ? FULL_ABSORPTION * pool / initialPool : 0.0; }

    public double poolPercent(int t) { return active(t) ? pool : 0.0; }

    public static final int DURATION_TICKS = 100;
    public static final double FULL_ABSORPTION = 16.0;
    public static final double DEFAULT_POOL_PERCENT = 93.0;
}
