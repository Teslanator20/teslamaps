// Ported from ADM's p3sim/run/Lag.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;

public final class LagEvent {
    public final double gapMs;
    public final int missedTicks;
    public final int recoveredTicks;

    public LagEvent(double gapMs, int missedTicks, int recoveredTicks) {
        this.gapMs = gapMs;
        this.missedTicks = missedTicks;
        this.recoveredTicks = recoveredTicks;
    }

    public int lostTicks() { return missedTicks - recoveredTicks; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LagEvent e)) return false;
        return Double.compare(gapMs, e.gapMs) == 0 && missedTicks == e.missedTicks && recoveredTicks == e.recoveredTicks;
    }

    @Override
    public int hashCode() { return Objects.hash(gapMs, missedTicks, recoveredTicks); }

    @Override
    public String toString() { return "LagEvent(gapMs=" + gapMs + ", missedTicks=" + missedTicks + ", recoveredTicks=" + recoveredTicks + ")"; }
}
