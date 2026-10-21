// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;

public final class RunEnded implements RunEvent {
    public final int t;
    public final EndReason reason;

    public RunEnded(int t, EndReason reason) {
        this.t = t;
        this.reason = reason;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof RunEnded x && Objects.equals(t, x.t) && Objects.equals(reason, x.reason);
    }
    @Override public int hashCode() { return Objects.hash(t, reason); }
    @Override public String toString() { return "RunEnded(" + "t=" + t + ", " + "reason=" + reason + ")"; }
}
