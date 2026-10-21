// Ported from ADM's p3sim/run/plugin/PartyStrategy.kt
package com.teslamaps.adm.sim.run.plugin;

import java.util.Objects;

public final class Seat {
    public final int index;
    public final String dungeonClass;
    public final boolean isHuman;

    public Seat(int index, String dungeonClass, boolean isHuman) {
        this.index = index;
        this.dungeonClass = dungeonClass;
        this.isHuman = isHuman;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Seat x && Objects.equals(index, x.index) && Objects.equals(dungeonClass, x.dungeonClass) && Objects.equals(isHuman, x.isHuman);
    }
    @Override public int hashCode() { return Objects.hash(index, dungeonClass, isHuman); }
    @Override public String toString() { return "Seat(" + "index=" + index + ", " + "dungeonClass=" + dungeonClass + ", " + "isHuman=" + isHuman + ")"; }
}
