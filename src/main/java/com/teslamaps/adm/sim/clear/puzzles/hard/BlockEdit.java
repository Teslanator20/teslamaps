package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/WaterBoardModel.kt

/** One block change in the room's canonical frame: state is a block-state string, `minecraft:air` to clear. */
public class BlockEdit {
    public final int x;
    public final int y;
    public final int z;
    public final String state;

    public BlockEdit(int x, int y, int z, String state) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.state = state;
    }
}
