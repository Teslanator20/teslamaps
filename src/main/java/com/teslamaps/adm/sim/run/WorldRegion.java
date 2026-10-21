// Ported from ADM's p3sim/run/WorldRegions.kt
package com.teslamaps.adm.sim.run;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

/** A rectangle of the ADS world with a block source of its own. */
public interface WorldRegion {
    /** For logs ("dungeon", "arena"). */
    String name();

    /** The inclusive block rectangle this region may write. */
    int minX();

    int maxX();

    int minZ();

    int maxZ();

    /** Writes this region's part of a fresh chunk, no updates, no heightmap prime. WORKER threads. */
    void fill(ChunkAccess chunk);

    /** Puts differences to the start state back; returns blocks fixed. Server thread. */
    int resync(ServerLevel level);

    default boolean intersects(int chunkMinX, int chunkMinZ) {
        return chunkMinX + 15 >= minX() && chunkMinX <= maxX() && chunkMinZ + 15 >= minZ() && chunkMinZ <= maxZ();
    }
}
