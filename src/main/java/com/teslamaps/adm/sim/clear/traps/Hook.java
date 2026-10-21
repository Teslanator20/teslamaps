package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/TripLines.kt

/** A tripwire hook as the rooms pack lists it: its cell and the way it faces. */
public class Hook {
    public final int x;
    public final int y;
    public final int z;
    public final String facing;

    public Hook(int x, int y, int z, String facing) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
    }
}
