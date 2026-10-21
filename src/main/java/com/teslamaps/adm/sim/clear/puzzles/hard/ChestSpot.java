package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/ChestReward.kt

/** A hard puzzle's reward chest cell (canonical frame) and facing. */
public class ChestSpot {
    public final int x;
    public final int y;
    public final int z;
    public final String facing;

    public ChestSpot(int x, int y, int z, String facing) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
    }

    public BlockEdit edit() { return new BlockEdit(x, y, z, "minecraft:chest[facing=" + facing + ",type=single,waterlogged=false]"); }
}
