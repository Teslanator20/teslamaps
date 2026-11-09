package com.teslamaps.features;

// Ported from SkyHanni's features/fishing/trophy/GeyserFishing.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GeyserBox {

    private static volatile boolean inVolcano;
    private static volatile Vec3 geyser;
    private static volatile AABB box;
    private static Object lastLevel;
    private static int timer;
    private static boolean lavaRod;

    private GeyserBox() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("GeyserBox").section("Fishing").when(() -> cfg().geyserBox)
                .tick(GeyserBox::tick)
                .world(GeyserBox::render)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Geyser");
                    b.toggle("Geyser Box", () -> c.geyserBox, v -> c.geyserBox = v)
                            .desc("Blazing Volcano: flat 4x4 box over the active geyser at Y 118, where the bobber has to land for trophy fish")
                            .tags("geyser", "trophy", "volcano", "crimson", "fishing");
                    b.color("  Color", () -> c.colorGeyserBox, v -> c.colorGeyserBox = v);
                    b.toggle("  Only With Lava Rod", () -> c.geyserBoxLavaRod, v -> c.geyserBoxLavaRod = v);
                    b.toggle("  Hide Particles At Bobber", () -> c.geyserBoxHideParticles, v -> c.geyserBoxHideParticles = v)
                            .desc("Drops the geyser clouds around your own bobber");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            geyser = null;
            box = null;
        }
        lavaRod = FishingHelpers.holdingLavaRod();
        if (timer++ % 20 != 0) return;
        inVolcano = SkyblockUtils.isInArea("Crimson Isle") && FishingHelpers.subArea().equals("Blazing Volcano");
    }

    private static boolean isGeyser(ClientboundLevelParticlesPacket p) {
        return p.particle().getType() == ParticleTypes.CLOUD && p.count() == 15
                && Math.abs(p.xMaxSpeed() - 0.05f) < 1e-4f
                && Math.abs(p.xDist() - 0.1f) < 1e-4f && Math.abs(p.yDist() - 0.6f) < 1e-4f
                && Math.abs(p.zDist() - 0.1f) < 1e-4f;
    }

    /** From ClientPlayNetworkHandlerMixin particle HEAD inject, any thread: only stores the position. */
    public static void onParticle(ClientboundLevelParticlesPacket p) {
        TeslaMapsConfig c = cfg();
        if (!c.geyserBox || !inVolcano || !isGeyser(p)) return;
        double x = p.x(), z = p.z();
        geyser = new Vec3(x, p.y(), z);
        box = new AABB(x - 2, 118.0 - 0.1, z - 2, x + 2, 118.0 - 0.09, z + 2);
    }

    /** From the particle hide chain (main thread). */
    public static boolean hideParticle(ClientboundLevelParticlesPacket p) {
        TeslaMapsConfig c = cfg();
        if (!c.geyserBox || !c.geyserBoxHideParticles || !inVolcano || !c.section("Fishing")) return false;
        Vec3 g = geyser;
        FishingHook bobber = FishingHelpers.bobber();
        if (g == null || bobber == null || !isGeyser(p)) return false;
        Vec3 b = bobber.position();
        return b.distanceTo(new Vec3(p.x(), p.y(), p.z())) < 3 && b.distanceTo(g) < 3;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        AABB bx = box;
        Vec3 g = geyser;
        if (bx == null || g == null || !inVolcano) return;
        if (cfg().geyserBoxLavaRod && !lavaRod) return;
        Minecraft mc = Minecraft.getInstance();
        double dx = g.x - mc.player.getX(), dz = g.z - mc.player.getZ();
        if (dx * dx + dz * dz > 96 * 96) return;
        ESPRenderer.drawFilledBox(pose, bx, TeslaMapsConfig.parseColor(cfg().colorGeyserBox), cam);
    }
}
