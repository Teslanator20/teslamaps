package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/TpMazeModel.kt

/** Where a player lands: the middle of the cell on the slab, looking yaw. */
public class Landing {
    public final int pad;
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;

    public Landing(int pad, double x, double y, double z, float yaw, float pitch) {
        this.pad = pad;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }
}
