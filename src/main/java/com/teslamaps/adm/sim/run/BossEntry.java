// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;

public final class BossEntry implements RunEvent {
    public final int t;

    public BossEntry(int t) {
        this.t = t;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof BossEntry x && Objects.equals(t, x.t);
    }
    @Override public int hashCode() { return Objects.hash(t); }
    @Override public String toString() { return "BossEntry(" + "t=" + t + ")"; }
}
