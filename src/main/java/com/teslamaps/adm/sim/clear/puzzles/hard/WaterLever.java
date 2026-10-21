package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/WaterBoardModel.kt

import java.util.Objects;

/** The six gate levers and the water lever of the Water Board (canonical room frame). */
public enum WaterLever {
    COAL(20, 61, 10, "minecraft:coal_block"),
    GOLD(20, 61, 15, "minecraft:gold_block"),
    QUARTZ(20, 61, 20, "minecraft:quartz_block"),
    DIAMOND(10, 61, 20, "minecraft:diamond_block"),
    EMERALD(10, 61, 15, "minecraft:emerald_block"),
    CLAY(10, 61, 10, "minecraft:terracotta"),
    WATER(15, 60, 5, null);

    public final int x;
    public final int y;
    public final int z;
    public final String gateBlock;

    WaterLever(int x, int y, int z, String gateBlock) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.gateBlock = gateBlock;
    }

    public static WaterLever at(int x, int y, int z) {
        for (WaterLever l : values()) if (l.x == x && l.y == y && l.z == z) return l;
        return null;
    }

    public static WaterLever ofGateBlock(String name) {
        for (WaterLever l : values()) if (Objects.equals(l.gateBlock, name)) return l;
        return null;
    }
}
