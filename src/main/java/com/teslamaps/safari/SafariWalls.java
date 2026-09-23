package com.teslamaps.safari;

import com.teslamaps.config.TeslaMapsConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

// Wall positions and the sight latch taken from critterMod's WallTracker.java
/**
 * The breakable walls a critter sits behind: five in the Cavern for Snoozle, three in the
 * Icy for Troodon. Fixed positions every run, so marking them gives nothing away - a wall
 * that is already gone simply drops out.
 */
public class SafariWalls {

    private record WallSet(String critter, SafariData.Biome biome, BlockPos[] positions) {}

    public record Wall(BlockPos pos, String critter, String label) {}

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

    /** Walls seen to be gone this run. */
    private static final Set<BlockPos> seenBroken = new HashSet<>();
    private static int ticks = 0;

    private static boolean latch() {
        return TeslaMapsConfig.get().safariWallSight;
    }

    public static void reset() {
        seenBroken.clear();
    }

    /** Latches walls that are loaded, air and in plain sight. */
    public static void tick(Minecraft mc) {
        if (!latch() || mc.level == null || mc.player == null) return;
        if (++ticks < 10) return;
        ticks = 0;
        for (WallSet set : SETS) {
            for (BlockPos pos : set.positions()) {
                if (seenBroken.contains(pos) || !mc.level.isLoaded(pos)) continue;
                if (!mc.level.getBlockState(pos).isAir()) continue;
                if (SafariSight.canSee(pos)) seenBroken.add(pos);
            }
        }
    }

    /** {intact, unknown} walls in front of that critter, null when it has none. */
    public static int[] state(Minecraft mc, String critter) {
        if (mc.level == null) return null;
        boolean latch = latch();
        for (WallSet set : SETS) {
            if (!set.critter().equals(critter)) continue;
            int intact = 0, unknown = 0;
            for (BlockPos pos : set.positions()) {
                if (latch && seenBroken.contains(pos)) continue;
                if (!mc.level.isLoaded(pos)) unknown++;
                else if (!mc.level.getBlockState(pos).isAir()) intact++;
                else if (latch) unknown++;
            }
            return new int[] {intact, unknown};
        }
        return null;
    }

    /** Only the walls of the biome you stand in, only while its critter is still uncaught. */
    public static void collect(Minecraft mc, List<Wall> out) {
        SafariData.Biome here = SafariData.currentBiome();
        if (here == null || mc.level == null || mc.player == null) return;
        boolean latch = latch();
        boolean labels = TeslaMapsConfig.get().safariWallLabels;

        for (WallSet set : SETS) {
            if (set.biome() != here) continue;
            SafariData.Critter critter = SafariData.byName(set.critter());
            if (critter != null && SafariTracker.hasCaught(critter)) continue;

            for (BlockPos pos : set.positions()) {
                if (latch) {
                    // only a wall seen gone drops out, unknown ones stay marked
                    if (seenBroken.contains(pos)) continue;
                } else {
                    // an unloaded chunk reports air too, so that is not a broken wall
                    if (!mc.level.isLoaded(pos)) continue;
                    if (mc.level.getBlockState(pos).isAir()) continue;
                }
                String label = null;
                if (labels) {
                    int dist = (int) Math.sqrt(mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                    label = set.critter() + " wall §e" + dist + "m";
                }
                out.add(new Wall(pos, set.critter(), label));
            }
        }
    }
}
