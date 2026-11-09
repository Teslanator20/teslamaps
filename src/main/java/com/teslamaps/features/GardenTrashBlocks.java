package com.teslamaps.features;

// Ported from Skytils' GardenFeatures (gardenPlotCleanupHelper)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Plot cleanup leftovers on the Garden's trash blocks. */
public final class GardenTrashBlocks {

    private static final int SCAN_H = 24, SCAN_DOWN = 8, SCAN_UP = 16, MAX_BOXES = 4000;

    private static Set<Block> trash;
    private static List<AABB> cleanup = List.of();
    private static boolean cleaning;
    private static int ticks;

    private GardenTrashBlocks() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("GardenTrashBlocks").section("Garden")
                .when(() -> cfg().plotCleanupHelper)
                .tick(GardenTrashBlocks::tick)
                .world(GardenTrashBlocks::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Trash Blocks");
                    b.toggle("Plot Cleanup Helper", () -> c.plotCleanupHelper, v -> c.plotCleanupHelper = v)
                            .desc("While the scoreboard shows \"Cleanup: x%\", grass, flowers and leaves around you get a sponge-colored box so leftovers stand out.")
                            .tags("cleanup", "plot", "unlock", "sponge");
                    b.color("  Cleanup Color", () -> c.plotCleanupColor, v -> c.plotCleanupColor = v);
                });
    }

    private static boolean isTrash(Block b) {
        if (trash == null) {
            trash = Set.of(Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.POPPY, Blocks.DANDELION,
                    Blocks.OAK_LEAVES, Blocks.BIRCH_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES,
                    Blocks.JUNGLE_LEAVES, Blocks.SPRUCE_LEAVES);
        }
        return trash.contains(b);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        if (!cfg().plotCleanupHelper) {
            cleaning = false;
            cleanup = List.of();
            return;
        }
        if (ticks % 5 == 0) cleaning = SkyblockUtils.isInGarden() && cleanupLine();
        if (!cleaning) {
            cleanup = List.of();
            return;
        }
        if (ticks % 20 == 0) cleanup = scan(mc);
    }

    private static boolean cleanupLine() {
        for (String l : ScoreboardUtils.getScoreboardLines()) {
            String s = ScoreboardUtils.cleanLine(l);
            if (s.startsWith("Cleanup:") && s.endsWith("%")) return true;
        }
        return false;
    }

    private static List<AABB> scan(Minecraft mc) {
        BlockPos c = mc.player.blockPosition();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        List<AABB> out = new ArrayList<>();
        for (int x = -SCAN_H; x <= SCAN_H; x++) {
            for (int z = -SCAN_H; z <= SCAN_H; z++) {
                for (int y = -SCAN_DOWN; y <= SCAN_UP; y++) {
                    p.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                    if (!isTrash(mc.level.getBlockState(p).getBlock())) continue;
                    out.add(new AABB(p).inflate(0.01));
                    if (out.size() >= MAX_BOXES) return out;
                }
            }
        }
        return out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<AABB> cl = cleanup;
        if (!cl.isEmpty()) {
            int color = TeslaMapsConfig.parseColor(cfg().plotCleanupColor);
            ESPRenderer.beginBoxBatch(pose, cam, true, false);
            for (AABB b : cl) ESPRenderer.batchBox(b, color, 1f);
            ESPRenderer.endBoxBatch();
        }
    }
}
