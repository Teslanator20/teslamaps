// Ported from ADM's p3sim/run/ArenaRegion.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.Arena;
import com.teslamaps.adm.sim.pack.DungeonFrame;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class ArenaRegion implements WorldRegion {
    public static final ArenaRegion INSTANCE = new ArenaRegion();

    private ArenaRegion() {}

    public static final String NAME = "arena";

    public static final int CLIP_MIN = DungeonFrame.ARENA_MIN;

    @Override public String name() { return NAME; }
    @Override public int minX() { return CLIP_MIN; }
    @Override public int minZ() { return CLIP_MIN; }
    @Override public int maxX() { return 176; }
    @Override public int maxZ() { return 192; }

    public static boolean active() {
        for (WorldRegion r : WorldRegions.regions()) if (r.name().equals(NAME)) return true;
        return false;
    }

    private static volatile boolean dirty = false;

    public static void markDirty() { dirty = true; }

    public static int clippedMin(int extMin, boolean clip) { return clip ? Math.max(extMin, CLIP_MIN) : extMin; }

    public static boolean keeps(int x, int z, boolean clip) { return !clip || (x >= CLIP_MIN && z >= CLIP_MIN); }

    public static void reset() { dirty = false; }

    public static void register() { WorldRegions.register(INSTANCE); }

    @Override
    public void fill(ChunkAccess chunk) { Arena.fillBlocks(chunk); }

    @Override
    public int resync(ServerLevel level) {
        if (!dirty) return 0;
        dirty = false;
        return Arena.resync(level);
    }
}
