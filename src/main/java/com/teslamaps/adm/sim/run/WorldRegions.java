// Ported from ADM's p3sim/run/WorldRegions.kt
package com.teslamaps.adm.sim.run;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/** The regions of the world. The generator hook calls fillAll; the engine registers them. */
public final class WorldRegions {
    private WorldRegions() {}

    private static final CopyOnWriteArrayList<WorldRegion> list = new CopyOnWriteArrayList<>();
    private static final Set<String> used = ConcurrentHashMap.newKeySet();

    public static List<WorldRegion> regions() { return list; }

    /** Adds (or replaces, by name) a region for the NEXT chunk generation. */
    public static void register(WorldRegion region) {
        list.removeIf(r -> r.name().equals(region.name()));
        used.remove(region.name());
        list.add(region);
    }

    public static void unregister(String name) {
        list.removeIf(r -> r.name().equals(name));
        used.remove(name);
    }

    public static void clear() { list.clear(); used.clear(); }

    /** [name] has written into the running world. */
    public static void noteUsed(String name) { used.add(name); }

    /** The sim server stopped: forget the regions its world used, keep the ones for the next world. */
    public static void worldStopped() {
        list.removeIf(r -> used.contains(r.name()));
        used.clear();
    }

    /** Fills a chunk from every region that touches it. Worker threads. */
    public static void fillAll(ChunkAccess chunk) {
        int x = chunk.getPos().getMinBlockX();
        int z = chunk.getPos().getMinBlockZ();
        boolean filled = false;
        for (WorldRegion r : list) {
            if (r.intersects(x, z)) { r.fill(chunk); noteUsed(r.name()); filled = true; }
        }
        // one prime after the last pass
        if (filled) Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG));
    }

    /** Resyncs every region; total blocks fixed. Server thread. */
    public static int resyncAll(ServerLevel level) {
        int n = 0;
        for (WorldRegion r : list) n += r.resync(level);
        return n;
    }
}
