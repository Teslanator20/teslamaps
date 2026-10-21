// Ported from ADM's p3sim/clear/bots/PlanGeometry.kt
package com.teslamaps.adm.sim.clear.bots;

import java.util.Objects;

/** One point of a route; [gate] is the door index when the point is a door spot the walker has to pass. */
public final class RoutePoint {
    public final Pt pt;
    public final Integer gate;

    public RoutePoint(Pt pt, Integer gate) { this.pt = pt; this.gate = gate; }

    public RoutePoint(Pt pt) { this(pt, null); }

    @Override public boolean equals(Object o) {
        return o instanceof RoutePoint r && Objects.equals(pt, r.pt) && Objects.equals(gate, r.gate);
    }

    @Override public int hashCode() { return Objects.hash(pt, gate); }

    @Override public String toString() { return "RoutePoint(pt=" + pt + ", gate=" + gate + ")"; }
}
