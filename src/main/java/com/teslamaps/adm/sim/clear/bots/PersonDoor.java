// Ported from ADM's p3sim/clear/bots/Leaps.kt
package com.teslamaps.adm.sim.clear.bots;

/** A wither door a person opened at [t], standing at [at]: the bots may leap to them as to a bot opener. */
public class PersonDoor {
    public final int door;
    public final int seat;
    public final int t;
    public final Pt at;

    public PersonDoor(int door, int seat, int t, Pt at) {
        this.door = door;
        this.seat = seat;
        this.t = t;
        this.at = at;
    }
}
