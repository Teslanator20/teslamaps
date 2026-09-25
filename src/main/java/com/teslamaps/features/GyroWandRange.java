package com.teslamaps.features;

// Ported from Odin's features/impl/render/GyroWand.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class GyroWandRange {

    private static final int SEGMENTS = 64;
    private static final double RADIUS = 10;
    private static final double[] COS = new double[SEGMENTS + 1], SIN = new double[SEGMENTS + 1];
    private static long castAt, lastSeen;

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double a = Math.PI * 2 * i / SEGMENTS;
            COS[i] = Math.cos(a) * RADIUS;
            SIN[i] = Math.sin(a) * RADIUS;
        }
    }

    private GyroWandRange() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("GyroWandRange").section("Combat").when(() -> cfg().gyroWandRange)
                .world(GyroWandRange::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Gyrokinetic Wand");
                    b.toggle("Gyro Wand Range", () -> c.gyroWandRange, v -> c.gyroWandRange = v)
                            .desc("Draws the 10 block pull radius where the Gyrokinetic Wand would land.");
                    b.color("  Color", () -> c.gyroWandColor, v -> c.gyroWandColor = v);
                    b.toggle("  Show Cooldown", () -> c.gyroWandCooldown, v -> c.gyroWandCooldown = v)
                            .desc("Switches to the cooldown color after Gravity Storm shows in the action bar.");
                    b.color("  Cooldown Color", () -> c.gyroWandCooldownColor, v -> c.gyroWandCooldownColor = v);
                    b.slider("  Cooldown (s)", 5f, 30f, () -> (float) c.gyroWandCooldownSeconds, v -> c.gyroWandCooldownSeconds = Math.round(v), 0)
                            .desc("30 without cooldown reduction; lower it for mage cooldown reduction.");
                    b.toggle("  Through Walls", () -> c.gyroWandThroughWalls, v -> c.gyroWandThroughWalls = v);
                });
    }

    /** Action bar, main thread. */
    public static void onActionBar(Component message) {
        TeslaMapsConfig c = cfg();
        if (!c.gyroWandRange || !c.gyroWandCooldown) return;
        String s = message.getString();
        if (!s.contains("(Gravity Storm)")) return;
        s = StringUtil.strip(s);
        if (!s.contains(" Mana (Gravity Storm)")) return;
        long now = System.currentTimeMillis();
        if (now - lastSeen > 2500) castAt = now;
        lastSeen = now;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !SkyblockUtils.isInSkyblock()) return;
        ItemStack held = mc.player.getMainHandItem();
        if (!held.is(Items.BLAZE_ROD) || !"GYROKINETIC_WAND".equals(ItemUtil.skyblockId(held))) return;
        HitResult hit = mc.player.pick(25, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), false);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos p = bh.getBlockPos();
        if (mc.level.getBlockState(p).isAir()) return;
        TeslaMapsConfig c = cfg();
        boolean cd = c.gyroWandCooldown && System.currentTimeMillis() - castAt < c.gyroWandCooldownSeconds * 1000L;
        int color = TeslaMapsConfig.parseColor(cd ? c.gyroWandCooldownColor : c.gyroWandColor);
        double cx = p.getX() + 0.5, cy = p.getY() + 1.0, cz = p.getZ() + 0.5;
        boolean through = c.gyroWandThroughWalls;
        for (int i = 0; i < SEGMENTS; i++) {
            Vec3 a = new Vec3(cx + COS[i], cy, cz + SIN[i]);
            Vec3 b = new Vec3(cx + COS[i + 1], cy, cz + SIN[i + 1]);
            ESPRenderer.drawLine(pose, a, b, color, 3f, cam, through);
            ESPRenderer.drawLine(pose, a.add(0, 0.3, 0), b.add(0, 0.3, 0), color, 1.5f, cam, through);
        }
    }
}
