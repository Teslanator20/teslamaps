// Ported from ADM's p3sim/clear/bots/ClearPlan.kt
package com.teslamaps.adm.sim.clear.bots;

import java.util.List;

/** The seat's moving and doing for the whole plan. */
public class SeatTrack {
    public final int seat;
    public final List<Leg> legs;
    public final List<Act> acts;

    public SeatTrack(int seat, List<Leg> legs, List<Act> acts) {
        this.seat = seat;
        this.legs = legs;
        this.acts = acts;
    }

    /** Where the seat is at tick [t]: before the first leg, at its start; after the last, where it ended. */
    public Pt positionAt(int t) {
        if (legs.isEmpty()) return new Pt(0.0, 0.0);
        if (t <= legs.get(0).t0) return legs.get(0).a;
        Leg last = legs.get(legs.size() - 1);
        if (t >= last.t1) return last.b;
        int lo = 0, hi = legs.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (legs.get(mid).t0 <= t) lo = mid; else hi = mid - 1;
        }
        return legs.get(lo).at(t);
    }

    /** The end of the track. */
    public int endT() { return legs.isEmpty() ? 0 : legs.get(legs.size() - 1).t1; }
}
