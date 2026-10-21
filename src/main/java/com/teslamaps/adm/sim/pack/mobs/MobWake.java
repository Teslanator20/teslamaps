// Ported from ADM's p3sim/pack/mobs/MobPack.kt
package com.teslamaps.adm.sim.pack.mobs;

import java.util.Objects;

/** A sleeper (Fels) wakes when a player body enters a sphere of [radius] blocks. */
public final class MobWake {
    public final double radius;
    public final String trigger;

    public MobWake(double radius, String trigger) { this.radius = radius; this.trigger = trigger; }

    @Override public boolean equals(Object o) { return o instanceof MobWake w && Double.compare(radius, w.radius) == 0 && Objects.equals(trigger, w.trigger); }
    @Override public int hashCode() { return Objects.hash(radius, trigger); }
    @Override public String toString() { return "MobWake(radius=" + radius + ", trigger=" + trigger + ")"; }
}
