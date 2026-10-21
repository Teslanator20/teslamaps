// Ported from ADM's p3sim/clear/bots/ClearPlan.kt
package com.teslamaps.adm.sim.clear.bots;

import java.util.Objects;

/** Finds the room's secrets, spread over the window, so the last one is found at until(). */
public final class FindSecrets implements Act {
    private final int seat;
    private final int t;
    private final int until;
    public final int room;

    public FindSecrets(int seat, int t, int until, int room) {
        this.seat = seat;
        this.t = t;
        this.until = until;
        this.room = room;
    }

    @Override public int seat() { return seat; }

    @Override public int t() { return t; }

    @Override public int until() { return until; }

    @Override public boolean equals(Object o) {
        return o instanceof FindSecrets x && seat == x.seat && t == x.t && until == x.until && room == x.room;
    }

    @Override public int hashCode() { return Objects.hash(seat, t, until, room); }

    @Override public String toString() { return "FindSecrets(seat=" + seat + ", t=" + t + ", until=" + until + ", room=" + room + ")"; }
}
