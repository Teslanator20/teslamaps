package com.teslamaps.safari;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

// Wall positions taken from critterMod's WallTracker.java
/**
 * The breakable walls a critter sits behind: five in the Cavern for Snoozle, three in the
 * Icy for Troodon. Fixed positions every run, so marking them gives nothing away - a wall
 * that is already gone simply drops out.
 */
public class SafariWalls {

    private record WallSet(String critter, SafariData.Biome biome, BlockPos[] positions) {}

    private static final WallSet[] SETS = {
            new WallSet("Snoozle", SafariData.Biome.CAVERN, new BlockPos[] {
                    new BlockPos(-126, 39, 74),
                    new BlockPos(-114, 39, 87),
                    new BlockPos(-70, 39, 68),
                    new BlockPos(-96, 40, 17),
                    new BlockPos(-95, 40, 42),
            }),
            new WallSet("Troodon", SafariData.Biome.ICY, new BlockPos[] {
                    new BlockPos(-104, 80, -95),
                    new BlockPos(-131, 78, -61),
                    new BlockPos(-109, 89, -27),
            }),
    };

    /** Only the walls of the biome you stand in, only while its critter is still uncaught. */
    public static void collect(Minecraft mc, List<BlockPos> out) {
        SafariData.Biome here = SafariData.currentBiome();
        if (here == null || mc.level == null) return;

        for (WallSet set : SETS) {
            if (set.biome() != here) continue;
            SafariData.Critter critter = SafariData.byName(set.critter());
            if (critter != null && SafariTracker.hasCaught(critter)) continue;

            for (BlockPos pos : set.positions()) {
                // an unloaded chunk reports air too, so that is not a broken wall
                if (!mc.level.isLoaded(pos)) continue;
                if (mc.level.getBlockState(pos).isAir()) continue;
                out.add(pos);
            }
        }
    }
}
