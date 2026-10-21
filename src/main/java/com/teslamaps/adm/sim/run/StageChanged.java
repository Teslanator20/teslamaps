// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;

public final class StageChanged implements RunEvent {
    public final int t;
    public final RunStage from;
    public final RunStage to;

    public StageChanged(int t, RunStage from, RunStage to) {
        this.t = t;
        this.from = from;
        this.to = to;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof StageChanged x && Objects.equals(t, x.t) && Objects.equals(from, x.from) && Objects.equals(to, x.to);
    }
    @Override public int hashCode() { return Objects.hash(t, from, to); }
    @Override public String toString() { return "StageChanged(" + "t=" + t + ", " + "from=" + from + ", " + "to=" + to + ")"; }
}
