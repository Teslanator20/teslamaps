package com.teslamaps.features.mining;

// Ported from Skyblocker's CarpetHighlighter.java
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Unbreakable carpets on sea lanterns inside Dwarven Mines ore veins. */
public final class CarpetHighlight {

    private static final int RADIUS = 15;
    private static final Set<BlockPos> found = new HashSet<>();
    private static List<AABB> boxes = List.of();
    private static int ticks;
    private static Object lastLevel;

    private CarpetHighlight() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CarpetHighlight").section("Mining").when(() -> cfg().carpetHighlight)
                .tick(CarpetHighlight::tick)
                .world(CarpetHighlight::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Unbreakable Carpet Highlight", () -> c.carpetHighlight, v -> c.carpetHighlight = v)
                            .desc("Dwarven Mines: fills the grey and light blue carpets on sea lanterns inside ore veins. They can't be mined.")
                            .tags("carpet", "mithril", "tungsten", "dwarven", "unbreakable");
                    b.color("  Carpet Color", () -> c.carpetHighlightColor, v -> c.carpetHighlightColor = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            found.clear();
            boxes = List.of();
        }
        if (++ticks % 15 != 0) return;
        if (!SkyblockUtils.isInArea("Dwarven Mines")) {
            if (!found.isEmpty()) {
                found.clear();
                boxes = List.of();
            }
            return;
        }
        Level level = mc.level;
        BlockPos center = mc.player.blockPosition();
        found.removeIf(p -> p.distManhattan(center) <= RADIUS && !isCarpet(level, p));
        for (BlockPos p : BlockPos.withinManhattan(center, RADIUS, RADIUS, RADIUS)) {
            if (isCarpet(level, p)) found.add(p.immutable());
        }
        List<AABB> out = new ArrayList<>(found.size());
        for (BlockPos p : found) out.add(new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1 / 16.0, p.getZ() + 1));
        boxes = out;
    }

    private static boolean isCarpet(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (!s.is(Blocks.GRAY_CARPET) && !s.is(Blocks.LIGHT_BLUE_CARPET) && !s.is(Blocks.LIGHT_GRAY_CARPET)) return false;
        return level.getBlockState(p.below()).is(Blocks.SEA_LANTERN);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<AABB> list = boxes;
        if (list.isEmpty()) return;
        int color = TeslaMapsConfig.parseColor(cfg().carpetHighlightColor);
        for (int i = 0, n = list.size(); i < n; i++) ESPRenderer.drawFilledBox(pose, list.get(i), color, cam, false);
    }
}
