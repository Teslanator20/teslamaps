package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Tikis are 3 skull blocks stacked on top of each other. Skulls are block entities,
// so the loaded-chunk block entity maps give us a live list without scanning blocks.
public class TikiESP {

    private static final List<BlockPos> tikis = new ArrayList<>();
    private static int ticks = 0;

    private static boolean enabled() {
        return TeslaMapsConfig.get().tikiESP && CritterESP.inArea();
    }

    public static void tick() {
        if (!enabled()) {
            if (!tikis.isEmpty()) tikis.clear();
            return;
        }
        if (++ticks < 20) return;
        ticks = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Set<Long> skulls = new HashSet<>();
        int pcx = mc.player.chunkPosition().x(), pcz = mc.player.chunkPosition().z();
        int r = Math.min(mc.options.renderDistance().get(), 16);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                    BlockEntity be = e.getValue();
                    if (be instanceof SkullBlockEntity && be.getBlockState().getBlock() instanceof SkullBlock) {
                        skulls.add(e.getKey().asLong());
                    }
                }
            }
        }

        List<BlockPos> found = new ArrayList<>();
        for (long key : skulls) {
            BlockPos p = BlockPos.of(key);
            if (skulls.contains(p.below().asLong())) continue;
            if (skulls.contains(p.above().asLong()) && skulls.contains(p.above(2).asLong())) found.add(p);
        }
        tikis.clear();
        tikis.addAll(found);
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!enabled() || tikis.isEmpty()) return;
        int color = TeslaMapsConfig.parseColor(cfg.tikiESPColor);
        for (BlockPos p : tikis) {
            AABB box = new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 3, p.getZ() + 1);
            ESPRenderer.drawBoxOutline(matrices, box, color, 1.5f, cameraPos);
            if (cfg.tikiESPName) {
                ESPRenderer.drawText(matrices, "§eTiki",
                        new Vec3(p.getX() + 0.5, p.getY() + 3.4, p.getZ() + 0.5), 1.5f, cameraPos);
            }
        }
    }
}
