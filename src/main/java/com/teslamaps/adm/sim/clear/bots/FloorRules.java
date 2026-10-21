// Ported from ADM's p3sim/clear/bots/RoomGrid.kt
package com.teslamaps.adm.sim.clear.bots;

import java.util.List;
import java.util.Set;

/** Whether a block state string can be stood on or walked through, for RoomGrids. */
public final class FloorRules {
    private FloorRules() {}

    private static final Set<String> LIQUID = Set.of("lava", "water", "fire", "soul_fire", "magma_block", "cactus", "powder_snow", "bubble_column", "sweet_berry_bush", "wither_rose");
    private static final Set<String> SOFT_EXACT = Set.of(
            "grass", "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "seagrass", "tall_seagrass", "kelp", "kelp_plant", "snow", "vine", "lever", "tripwire",
            "tripwire_hook", "string", "redstone_wire", "repeater", "comparator", "ladder", "flower_pot", "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet",
            "oxeye_daisy", "cornflower", "lily_of_the_valley", "sunflower", "lilac", "rose_bush", "peony", "red_mushroom", "brown_mushroom", "glow_lichen", "sculk_vein",
            "lantern", "soul_lantern", "candle", "moss_carpet", "pale_moss_carpet", "lily_pad", "sugar_cane", "wheat", "nether_wart", "torchflower", "pink_petals"
    );
    private static final List<String> SOFT_SUFFIX = List.of(
            "_carpet", "_pressure_plate", "_button", "torch", "_sign", "_hanging_sign", "rail", "_head", "_skull", "_banner", "_sapling", "_tulip", "_roots", "_candle"
    );
    private static final List<String> IN_THE_WAY_SUFFIX = List.of("_wall", "_fence", "fence_gate", "_pane", "iron_bars", "_door", "trapdoor", "chain", "barrier");

    private static String name(String state) {
        int i = state.indexOf('[');
        String n = i >= 0 ? state.substring(0, i) : state;
        return n.startsWith("minecraft:") ? n.substring("minecraft:".length()) : n;
    }

    public static boolean isAir(String state) {
        String n = name(state);
        return n.equals("air") || n.equals("cave_air") || n.equals("void_air");
    }

    public static boolean isLiquid(String state) { return LIQUID.contains(name(state)); }

    private static boolean soft(String n) {
        if (SOFT_EXACT.contains(n) || n.startsWith("potted_")) return true;
        for (String s : SOFT_SUFFIX) if (n.endsWith(s)) return true;
        return false;
    }

    /** A block a walker passes through (or stands in). */
    public static boolean passable(String state) {
        if (isAir(state)) return true;
        String n = name(state);
        if (LIQUID.contains(n)) return false;
        for (String s : IN_THE_WAY_SUFFIX) if (n.endsWith(s)) return false;
        if (n.endsWith("_slab")) return state.contains("type=bottom");
        if (n.endsWith("_stairs")) return true;
        return soft(n);
    }

    /** A block that holds a walker up. */
    public static boolean supports(String state) {
        if (isAir(state)) return false;
        String n = name(state);
        return !(LIQUID.contains(n) || soft(n) || n.equals("barrier"));
    }
}
