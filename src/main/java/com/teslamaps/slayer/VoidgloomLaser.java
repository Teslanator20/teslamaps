package com.teslamaps.slayer;

// Ported from Skyblocker's LazerTimer and Zen's LaserTimer (8.2 s)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.Vec3;

public final class VoidgloomLaser {

    private static final int DURATION = 164;

    private static int remaining;
    private static long lastStart;
    private static String text = "";

    private VoidgloomLaser() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("VoidgloomLaser").section("Combat").when(() -> cfg().voidgloomLaserTimer || cfg().slayerNametag)
                .tick(VoidgloomLaser::tick);
        Feature.of("VoidgloomLaserText").section("Combat").when(() -> cfg().voidgloomLaserTimer)
                .world(VoidgloomLaser::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Voidgloom Laser Timer", () -> c.voidgloomLaserTimer, v -> c.voidgloomLaserTimer = v)
                            .desc("Countdown over your Voidgloom Seraph while it rides the laser phase")
                            .tags("slayer", "voidgloom", "enderman", "laser", "timer");
                });
    }

    /** Laser seconds left on the tracked boss, 0 outside the phase. */
    public static float remaining() { return remaining / 20f; }

    private static void tick() {
        Entity boss = SlayerHUD.getBossEntity();
        boolean riding = boss instanceof EnderMan && boss.isAlive() && boss.getVehicle() instanceof ArmorStand;
        long now = System.currentTimeMillis();
        if (riding && remaining <= 0 && now - lastStart >= 10_000) {
            lastStart = now;
            remaining = DURATION;
        } else if (remaining > 0) {
            remaining--;
        }
        if (boss == null || !boss.isAlive()) remaining = 0;
        text = remaining > 0 ? "§b" + String.format(java.util.Locale.ROOT, "%.1fs", remaining / 20f) : "";
    }

    private static void render(PoseStack matrices, Vec3 cam) {
        if (text.isEmpty()) return;
        Entity boss = SlayerHUD.getBossEntity();
        if (boss == null || boss.getVehicle() == null) return;
        ESPRenderer.drawText(matrices, text, ESPRenderer.lerpBox(boss).getCenter().add(0, 2.2, 0), 2.5f, cam);
    }
}
