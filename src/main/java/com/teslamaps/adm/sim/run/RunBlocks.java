// Ported from ADM's p3sim/run/RunBlocks.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.Arena;
import com.teslamaps.adm.sim.SimServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The one way for run logic to change a dungeon block while the run is on (never Blocks.set / Arena.set: the boss restart would erase it).
 * Clients told, no neighbour updates. Server thread only.
 */
public final class RunBlocks {
    private RunBlocks() {}

    /** Clients told, no shape or neighbour updates. */
    public static final int FLAGS = Arena.FLAGS;

    /** FLAGS without the block's own onPlace (Water Board). */
    public static final int QUIET = FLAGS | Block.UPDATE_SKIP_ON_PLACE;

    /** The sim's level, or null. */
    public static ServerLevel level() { return SimServer.level(); }

    /** Whether column (x, z)'s chunk is loaded now; set/get on an unloaded one loads it synchronously. */
    public static boolean isLoaded(int x, int z) {
        ServerLevel lv = level();
        return lv != null && lv.isLoaded(new BlockPos(x, 64, z));
    }

    public static BlockState get(int x, int y, int z) {
        ServerLevel lv = level();
        return lv != null ? lv.getBlockState(new BlockPos(x, y, z)) : null;
    }

    /** No-op when it already is state; true when it changed. */
    public static boolean set(int x, int y, int z, BlockState state, int flags) {
        ServerLevel lv = level();
        if (lv == null) return false;
        BlockPos pos = new BlockPos(x, y, z);
        if (lv.getBlockState(pos).equals(state)) return false;
        return lv.setBlock(pos, state, flags);
    }

    public static boolean set(int x, int y, int z, BlockState state) { return set(x, y, z, state, FLAGS); }

    public static boolean set(BlockPos pos, BlockState state) { return set(pos.getX(), pos.getY(), pos.getZ(), state); }
}
