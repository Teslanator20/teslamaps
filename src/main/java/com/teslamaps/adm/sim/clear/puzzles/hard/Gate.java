package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/WaterBoardModel.kt

/** One piston gate in the channel wall: when extended its block stands in the channel. */
public class Gate {
    public final int x;
    public final int y;
    public final WaterLever lever;
    public boolean extended;

    public Gate(int x, int y, WaterLever lever, boolean extended) {
        this.x = x;
        this.y = y;
        this.lever = lever;
        this.extended = extended;
    }
}
