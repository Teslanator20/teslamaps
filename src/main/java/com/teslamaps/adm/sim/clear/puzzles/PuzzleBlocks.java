// Ported from ADM's p3sim/clear/puzzles/PuzzleBlocks.kt
package com.teslamaps.adm.sim.clear.puzzles;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/** Block states written as strings in a room's canonical frame, turned for the way the room stands in the world. Server thread. */
public final class PuzzleBlocks {
    private PuzzleBlocks() {}

    private static final ConcurrentHashMap<String, BlockState> parsed = new ConcurrentHashMap<>();

    /** The state a string names, or null when it does not parse. */
    public static BlockState parse(String s) {
        BlockState cached = parsed.get(s);
        if (cached != null) return cached;
        BlockState st;
        try { st = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s, false).blockState(); } catch (Exception e) { return null; }
        parsed.put(s, st);
        return st;
    }

    /** The clockwise quarter turns as the game's rotation. */
    public static Rotation rotation(int quarterTurns) {
        return switch (((quarterTurns % 4) + 4) % 4) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** The state turned by quarterTurns. */
    public static BlockState state(String s, int quarterTurns) {
        BlockState st = parse(s);
        return st == null ? null : st.rotate(rotation(quarterTurns));
    }
}
