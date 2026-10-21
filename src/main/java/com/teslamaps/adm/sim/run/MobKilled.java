// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;
import java.util.UUID;

public final class MobKilled implements RunEvent {
    public final int t;
    public final MobRef mob;
    public final UUID by;

    public MobKilled(int t, MobRef mob, UUID by) {
        this.t = t;
        this.mob = mob;
        this.by = by;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof MobKilled x && Objects.equals(t, x.t) && Objects.equals(mob, x.mob) && Objects.equals(by, x.by);
    }
    @Override public int hashCode() { return Objects.hash(t, mob, by); }
    @Override public String toString() { return "MobKilled(" + "t=" + t + ", " + "mob=" + mob + ", " + "by=" + by + ")"; }
}
