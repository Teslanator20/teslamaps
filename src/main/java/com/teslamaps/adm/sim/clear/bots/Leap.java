// Ported from ADM's p3sim/clear/bots/Leaps.kt
package com.teslamaps.adm.sim.clear.bots;

/** A teammate's leap: at [t] seat [seat] teleports to seat [to], who opened wither door [door] a moment before. */
public class Leap {
    public final int t;
    public final int seat;
    public final int to;
    public final int door;

    public Leap(int t, int seat, int to, int door) {
        this.t = t;
        this.seat = seat;
        this.to = to;
        this.door = door;
    }
}
