package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/HardBlocks.kt

import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.run.RunBlocks;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/** Block writes of the hard puzzles and the trap rooms: canonical states turned and placed quietly. Server thread only. */
public final class HardBlocks {
    private HardBlocks() {}

    public static final int FLAGS = RunBlocks.QUIET;

    private static final ConcurrentHashMap<String, BlockState> parsed = new ConcurrentHashMap<>();

    /** The block state a string names, or null when it does not parse. */
    public static BlockState parse(String s) {
        BlockState cached = parsed.get(s);
        if (cached != null) return cached;
        BlockState st;
        try { st = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s, false).blockState(); } catch (Exception e) { return null; }
        parsed.put(s, st);
        return st;
    }

    public static Rotation rotation(int quarterTurns) {
        return switch (((quarterTurns % 4) + 4) % 4) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** The state turned for a placement. */
    public static BlockState state(String s, int quarterTurns) {
        BlockState st = parse(s);
        return st == null ? null : st.rotate(rotation(quarterTurns));
    }

    /** Writes one world block. True when it changed. */
    public static boolean setWorld(int x, int y, int z, BlockState state) {
        return RunBlocks.set(x, y, z, state, FLAGS);
    }

    /** Applies canonical-frame edits through a placement. Returns how many blocks changed. */
    public static int apply(Placement placement, List<BlockEdit> edits) {
        int n = 0;
        for (BlockEdit e : edits) {
            BlockState st = state(e.state, placement.quarterTurns());
            if (st == null) continue;
            WorldPos w = placement.toWorld(e.x, e.y, e.z);
            if (setWorld(w.x, w.y, w.z, st)) n++;
        }
        return n;
    }
}
