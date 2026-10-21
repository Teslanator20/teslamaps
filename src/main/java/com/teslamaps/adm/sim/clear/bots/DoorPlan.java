// Ported from ADM's p3sim/clear/bots/ClearPlan.kt
package com.teslamaps.adm.sim.clear.bots;

/** A paid door's schedule: [seat] opens it at [openT]; the key drops at [keyT] in a room whose last mob [keySeat] kills. */
public class DoorPlan {
    public final int door;
    public final int seat;
    public final int keyT;
    public final int openT;
    public final int keySeat;

    public DoorPlan(int door, int seat, int keyT, int openT, int keySeat) {
        this.door = door;
        this.seat = seat;
        this.keyT = keyT;
        this.openT = openT;
        this.keySeat = keySeat;
    }

    public DoorPlan(int door, int seat, int keyT, int openT) { this(door, seat, keyT, openT, seat); }
}
