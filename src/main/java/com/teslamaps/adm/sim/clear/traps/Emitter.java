package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/TrapSites.kt

/** A dispenser as traps.json lists it: its cell (canonical, absolute y) and facing. */
public class Emitter {
    public final int x;
    public final int y;
    public final int z;
    public final String facing;

    public Emitter(int x, int y, int z, String facing) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
    }

    /** The facing as a step on each canonical axis (north is -z, east is +x). */
    public int dx() {
        return switch (facing) { case "east" -> 1; case "west" -> -1; default -> 0; };
    }

    public int dy() {
        return switch (facing) { case "up" -> 1; case "down" -> -1; default -> 0; };
    }

    public int dz() {
        return switch (facing) { case "south" -> 1; case "north" -> -1; default -> 0; };
    }
}
