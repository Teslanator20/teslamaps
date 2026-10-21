// Ported from ADM's p3sim/clear/bots/ClearPlan.kt
package com.teslamaps.adm.sim.clear.bots;

import java.util.Objects;

/** Kills the room's required mobs, spread over the window, so the last one dies at until(). */
public final class ClearMobs implements Act {
    private final int seat;
    private final int t;
    private final int until;
    public final int room;

    public ClearMobs(int seat, int t, int until, int room) {
        this.seat = seat;
        this.t = t;
        this.until = until;
        this.room = room;
    }

    @Override public int seat() { return seat; }

    @Override public int t() { return t; }

    @Override public int until() { return until; }

    @Override public boolean equals(Object o) {
        return o instanceof ClearMobs x && seat == x.seat && t == x.t && until == x.until && room == x.room;
    }

    @Override public int hashCode() { return Objects.hash(seat, t, until, room); }

    @Override public String toString() { return "ClearMobs(seat=" + seat + ", t=" + t + ", until=" + until + ", room=" + room + ")"; }
}
