// Ported from ADM's p3sim/run/api/DamageApi.kt
package com.teslamaps.adm.sim.run.api;

import java.util.Objects;

public final class HurtResult {
    public final double applied;
    public final boolean died;
    public final boolean ignored;

    public HurtResult(double applied, boolean died, boolean ignored) {
        this.applied = applied;
        this.died = died;
        this.ignored = ignored;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof HurtResult x && Objects.equals(applied, x.applied) && Objects.equals(died, x.died) && Objects.equals(ignored, x.ignored);
    }
    @Override public int hashCode() { return Objects.hash(applied, died, ignored); }
    @Override public String toString() { return "HurtResult(" + "applied=" + applied + ", " + "died=" + died + ", " + "ignored=" + ignored + ")"; }
}
