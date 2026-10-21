package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/TpMazeModel.kt

/** One teleport pad: its end-portal frame cell and the cell it lands you on (canonical frame). */
public class Pad {
    public final int index;
    public final int frameX;
    public final int frameZ;
    public final int landX;
    public final int landZ;

    public Pad(int index, int frameX, int frameZ, int landX, int landZ) {
        this.index = index;
        this.frameX = frameX;
        this.frameZ = frameZ;
        this.landX = landX;
        this.landZ = landZ;
    }

    /** Group of four pads; the two centre pads (28, 29) belong to none. */
    public int group() { return index < 28 ? index / 4 : -1; }

    public double centreX() { return frameX + 0.5; }

    public double centreZ() { return frameZ + 0.5; }
}
