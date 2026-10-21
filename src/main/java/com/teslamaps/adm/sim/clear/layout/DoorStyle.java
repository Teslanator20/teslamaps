package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/DoorFrames.kt

import java.util.Collections;
import java.util.List;

public enum DoorStyle {
    STONE_BRICKS("minecraft:stone_bricks", Collections.nCopies(4, "minecraft:stone_bricks"), 33),
    SPRUCE("minecraft:spruce_planks", Collections.nCopies(4, "minecraft:spruce_planks"), 21),
    STONE_BRICKS_ACACIA("minecraft:stone_bricks", List.of("minecraft:stone_bricks", DoorFramesKt.ACACIA, DoorFramesKt.ACACIA, "minecraft:stone_bricks"), 16),
    /** Always the entrance door. */
    COBBLESTONE_ENTRANCE("minecraft:cobblestone", List.of("minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:stone_bricks", "minecraft:stone_bricks"), 0),
    COBBLESTONE_ACACIA("minecraft:cobblestone", List.of("minecraft:cobblestone", DoorFramesKt.ACACIA, DoorFramesKt.ACACIA, "minecraft:cobblestone"), 17),
    /** Always the blood door. */
    BLOOD("minecraft:red_wool", List.of("minecraft:nether_bricks", "minecraft:red_wool", "minecraft:red_wool", "minecraft:nether_bricks"), 0);

    public final String lintel;
    public final List<String> pillars;
    public final int weight;

    DoorStyle(String lintel, List<String> pillars, int weight) {
        this.lintel = lintel;
        this.pillars = pillars;
        this.weight = weight;
    }
}
