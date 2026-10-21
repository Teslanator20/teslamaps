// Ported from ADM's p3sim/run/plugin/PartyStrategy.kt
package com.teslamaps.adm.sim.run.plugin;

import java.util.Map;
import java.util.Objects;

public final class BloodAssignment {
    public final Integer seat;
    public final Integer arriveAtT;
    public final Map<String, String> extras;

    public BloodAssignment(Integer seat, Integer arriveAtT, Map<String, String> extras) {
        this.seat = seat;
        this.arriveAtT = arriveAtT;
        this.extras = extras;
    }

    public BloodAssignment(Integer seat, Integer arriveAtT) { this(seat, arriveAtT, java.util.Collections.emptyMap()); }

    public BloodAssignment(Integer seat) { this(seat, null, java.util.Collections.emptyMap()); }

    public static final BloodAssignment NOBODY = new BloodAssignment(null);

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof BloodAssignment x && Objects.equals(seat, x.seat) && Objects.equals(arriveAtT, x.arriveAtT) && Objects.equals(extras, x.extras);
    }
    @Override public int hashCode() { return Objects.hash(seat, arriveAtT, extras); }
    @Override public String toString() { return "BloodAssignment(" + "seat=" + seat + ", " + "arriveAtT=" + arriveAtT + ", " + "extras=" + extras + ")"; }
}
