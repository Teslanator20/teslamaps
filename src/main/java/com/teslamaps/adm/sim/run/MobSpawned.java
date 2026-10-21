// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;

public final class MobSpawned implements RunEvent {
    public final int t;
    public final MobRef mob;

    public MobSpawned(int t, MobRef mob) {
        this.t = t;
        this.mob = mob;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof MobSpawned x && Objects.equals(t, x.t) && Objects.equals(mob, x.mob);
    }
    @Override public int hashCode() { return Objects.hash(t, mob); }
    @Override public String toString() { return "MobSpawned(" + "t=" + t + ", " + "mob=" + mob + ")"; }
}
