package com.teslamaps.features;

// OdinClient ChestEsp: every chest through walls, area filter, hide opened ones
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EspStyle;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ChestEsp {

    private static final String[] WHERE = {"Everywhere", "Dungeons", "Crystal Hollows"};

    private static final Set<BlockPos> opened = new HashSet<>();
    private static List<AABB> boxes = new ArrayList<>();
    private static Level lastLevel;
    private static int ticks;

    private ChestEsp() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ChestEsp").section("ESP").legit().when(() -> cfg().chestEsp)
                .tick(ChestEsp::tick)
                .world(ChestEsp::render)
                .config("ESP", b -> {
                    b.toggle("Chest ESP", () -> cfg().chestEsp, v -> cfg().chestEsp = v).cheat()
                            .desc("Every chest through walls (Crystal Hollows loot chests, dungeon secret chests)");
                    b.dropdown("  Show In", WHERE, () -> cfg().chestEspWhere, v -> cfg().chestEspWhere = v);
                    b.toggle("  Hide Opened Chests", () -> cfg().chestEspHideClicked, v -> cfg().chestEspHideClicked = v);
                    b.espStyle("Chest", () -> cfg().chestEspStyle);
                });
    }

    private static boolean here() {
        return switch (cfg().chestEspWhere) {
            case "Dungeons" -> DungeonManager.isInDungeon();
            case "Crystal Hollows" -> SkyblockUtils.isInArea("Crystal Hollows");
            default -> true;
        };
    }

    public static void onChestClick(BlockPos pos) {
        if (cfg().chestEsp) opened.add(pos.immutable());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            opened.clear();
            boxes = new ArrayList<>();
        }
        if (ticks++ % 4 != 0) return;
        if (!here()) {
            if (!boxes.isEmpty()) boxes = new ArrayList<>();
            return;
        }
        List<AABB> out = new ArrayList<>();
        boolean hide = cfg().chestEspHideClicked;
        int pcx = mc.player.chunkPosition().x(), pcz = mc.player.chunkPosition().z();
        int r = Math.min(mc.options.renderDistance().get(), 12);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(pcx + dx, pcz + dz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof ChestBlockEntity)) continue;
                    BlockPos p = be.getBlockPos();
                    if (hide && opened.contains(p)) continue;
                    out.add(new AABB(p.getX() + 0.0625, p.getY(), p.getZ() + 0.0625,
                            p.getX() + 0.9375, p.getY() + 0.875, p.getZ() + 0.9375));
                }
            }
        }
        boxes = out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<AABB> list = boxes;
        if (list.isEmpty()) return;
        EspStyle style = EspStyle.of(cfg().chestEspStyle);
        for (int i = 0, n = list.size(); i < n; i++) style.drawBox(pose, list.get(i), cam);
    }
}
