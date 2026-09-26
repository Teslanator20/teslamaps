package com.teslamaps.features.rift;

// After SkyHanni's rift/area/dreadfarm/RiftAgaricusCap.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Farming Wand on a brown mushroom: timer above it until it turns red, then "Click!". */
public final class RiftAgaricusCap {

    private static final long RED = Long.MAX_VALUE;

    private static BlockPos location;
    private static long start;
    private static boolean inArea;
    private static int ticks;
    private static String label;
    private static Vec3 labelPos;
    private static Object lastLevel;

    private RiftAgaricusCap() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("RiftAgaricusCap").section("Rift").when(() -> cfg().riftAgaricusCap)
                .tick(RiftAgaricusCap::tick)
                .world(RiftAgaricusCap::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Agaricus Cap Timer", () -> c.riftAgaricusCap, v -> c.riftAgaricusCap = v)
                            .desc("Dreadfarm / West Village: look at a brown mushroom with the Farming Wand, a timer runs above it. \"Click!\" once it turned red.")
                            .tags("rift", "agaricus", "mushroom", "dreadfarm", "farming wand");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            location = null;
            start = 0;
        }
        if (ticks++ % 20 == 0) {
            String sub = RiftSpot.inRift() ? RiftSpot.sub() : "";
            inArea = sub.equals("Dreadfarm") || sub.equals("West Village");
        }
        location = inArea ? update(mc) : null;
        if (location == null) {
            label = null;
            return;
        }
        labelPos = new Vec3(location.getX() + 0.5, location.getY() + 0.6 + 0.5, location.getZ() + 0.5);
        if (start == RED) {
            label = "§cClick!";
        } else {
            long ms = System.currentTimeMillis() - start;
            label = "§b" + (ms / 1000) + "." + (ms % 1000 / 100) + "s";
        }
    }

    private static BlockPos update(Minecraft mc) {
        if (mc.player == null || !"FARMING_WAND".equals(ItemUtil.skyblockId(mc.player.getMainHandItem()))) return null;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = hit.getBlockPos();
        Block block = mc.level.getBlockState(pos).getBlock();
        if (block == Blocks.BROWN_MUSHROOM) {
            if (!pos.equals(location)) {
                start = System.currentTimeMillis();
                return pos.immutable();
            }
            if (start == RED) start = System.currentTimeMillis();
            return location;
        }
        if (block == Blocks.RED_MUSHROOM && pos.equals(location)) {
            start = RED;
            return location;
        }
        return null;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        String l = label;
        if (l == null || labelPos == null) return;
        ESPRenderer.drawText(pose, l, labelPos, 1.5f, cam);
    }
}
