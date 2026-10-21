// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;

public final class ScoreUpdated implements RunEvent {
    public final int t;
    public final int score;
    public final int clearedPct;

    public ScoreUpdated(int t, int score, int clearedPct) {
        this.t = t;
        this.score = score;
        this.clearedPct = clearedPct;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof ScoreUpdated x && Objects.equals(t, x.t) && Objects.equals(score, x.score) && Objects.equals(clearedPct, x.clearedPct);
    }
    @Override public int hashCode() { return Objects.hash(t, score, clearedPct); }
    @Override public String toString() { return "ScoreUpdated(" + "t=" + t + ", " + "score=" + score + ", " + "clearedPct=" + clearedPct + ")"; }
}
