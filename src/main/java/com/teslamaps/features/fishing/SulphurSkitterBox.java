package com.teslamaps.features.fishing;

// Ported from SkyHanni's features/nether/SulphurSkitterBox.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.FishingHelpers;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class SulphurSkitterBox {

    private static final int SCAN = 15, RADIUS = 4;

    private static List<BlockPos> sponges = List.of();
    private static volatile AABB box;
    private static volatile BlockPos closest;
    private static boolean inIsle, lavaRod;
    private static Object lastLevel;
    private static int ticks;

    private SulphurSkitterBox() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SulphurSkitterBox").section("Fishing").when(() -> cfg().sulphurSkitterBox)
                .tick(SulphurSkitterBox::tick)
                .world(SulphurSkitterBox::render)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Sulphur Skitter Box", () -> c.sulphurSkitterBox, v -> c.sulphurSkitterBox = v)
                            .desc("Crimson Isle: 9x9x9 box around the closest sponge, the area Sulphur Skitter bites in")
                            .tags("sulphur", "skitter", "sponge", "trophy", "crimson");
                    b.dropdown("  Style", new String[] {"Filled", "Wireframe"},
                            () -> c.sulphurSkitterBoxType, v -> c.sulphurSkitterBoxType = v);
                    b.color("  Color", () -> c.colorSulphurSkitterBox, v -> c.colorSulphurSkitterBox = v);
                    b.toggle("  Only With Lava Rod", () -> c.sulphurSkitterBoxLavaRod, v -> c.sulphurSkitterBoxLavaRod = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            sponges = List.of();
            closest = null;
            box = null;
        }
        lavaRod = FishingHelpers.holdingLavaRod();
        ticks++;
        if (ticks % 20 == 0) {
            inIsle = SkyblockUtils.isInArea("Crimson Isle");
            sponges = inIsle ? scan(mc) : List.of();
        }
        if (ticks % 5 != 0) return;
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : sponges) {
            double d = mc.player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            if (d < bestD) { bestD = d; best = p; }
        }
        if (best == null) {
            closest = null;
            box = null;
        } else if (!best.equals(closest)) {
            closest = best;
            box = new AABB(best.getX() - RADIUS, best.getY() - RADIUS, best.getZ() - RADIUS,
                    best.getX() + RADIUS + 1, best.getY() + RADIUS + 1, best.getZ() + RADIUS + 1);
        }
    }

    private static List<BlockPos> scan(Minecraft mc) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos c = mc.player.blockPosition();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -SCAN; x <= SCAN; x++) {
            for (int y = -SCAN; y <= SCAN; y++) {
                for (int z = -SCAN; z <= SCAN; z++) {
                    m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                    if (mc.level.getBlockState(m).is(Blocks.SPONGE)) out.add(m.immutable());
                }
            }
        }
        return out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        AABB b = box;
        BlockPos p = closest;
        if (b == null || p == null || !inIsle) return;
        TeslaMapsConfig c = cfg();
        if (c.sulphurSkitterBoxLavaRod && !lavaRod) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player.distanceToSqr(p.getX(), p.getY(), p.getZ()) >= 2500) return;
        int color = TeslaMapsConfig.parseColor(c.colorSulphurSkitterBox);
        if ("Wireframe".equals(c.sulphurSkitterBoxType)) ESPRenderer.drawBoxOutline(pose, b, color | 0xFF000000, 2f, cam, false);
        else ESPRenderer.drawFilledBox(pose, b, color, cam, false);
    }
}
