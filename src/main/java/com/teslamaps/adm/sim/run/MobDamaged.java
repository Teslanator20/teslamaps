// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;
import java.util.UUID;

public final class MobDamaged implements RunEvent {
    public final int t;
    public final MobRef mob;
    public final double amount;
    public final double remaining;
    public final UUID by;

    public MobDamaged(int t, MobRef mob, double amount, double remaining, UUID by) {
        this.t = t;
        this.mob = mob;
        this.amount = amount;
        this.remaining = remaining;
        this.by = by;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof MobDamaged x && Objects.equals(t, x.t) && Objects.equals(mob, x.mob) && Objects.equals(amount, x.amount) && Objects.equals(remaining, x.remaining) && Objects.equals(by, x.by);
    }
    @Override public int hashCode() { return Objects.hash(t, mob, amount, remaining, by); }
    @Override public String toString() { return "MobDamaged(" + "t=" + t + ", " + "mob=" + mob + ", " + "amount=" + amount + ", " + "remaining=" + remaining + ", " + "by=" + by + ")"; }
}
