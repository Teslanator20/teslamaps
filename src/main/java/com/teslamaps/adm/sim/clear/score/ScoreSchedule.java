// Ported from ADM's p3sim/clear/score/ScoreFormula.kt
package com.teslamaps.adm.sim.clear.score;

public class ScoreSchedule {
    private final int origin;
    private final int period;
    private int nextK = 1;

    public ScoreSchedule(int origin, int period) {
        this.origin = origin;
        this.period = period;
    }

    public int nextT() { return origin + period * nextK; }

    public boolean poll(int t) {
        if (t < nextT()) return false;
        nextK = Math.floorDiv(t - origin, period) + 1;
        return true;
    }

    public void resetTo(int t) { nextK = t < origin + period ? 1 : Math.floorDiv(t - origin, period) + 1; }
}
