package com.teslamaps.features;

// Ported from Skysoft's HealingPoolLine.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.phys.Vec3;

public final class HealingPoolMarker {

    private static final int RING = 12;
    private static final long WINDOW_MS = 100;
    private static final long TIMEOUT_MS = 750;
    private static final double RADIUS_SQ = 2.25;
    private static final double RING_TOL = 0.01;
    private static final double TOL = 0.0001;

    private static final double[] xs = new double[64], ys = new double[64], zs = new double[64];
    private static final long[] ts = new long[64];
    private static int count;

    private static Vec3 pool;
    private static long seenAt;
    private static boolean inIsle;
    private static int ticks;

    private HealingPoolMarker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("HealingPoolMarker").section("Combat").when(() -> cfg().healingPoolMarker)
                .tick(HealingPoolMarker::tick)
                .world(HealingPoolMarker::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Wisp Healing Pool");
                    b.toggle("Healing Pool Marker", () -> c.healingPoolMarker, v -> c.healingPoolMarker = v)
                            .desc("Crimson Isle: line from the crosshair to your Wisp pet's healing pool, found by its ring of cloud particles.")
                            .tags("wisp", "healing", "pool", "pet", "crimson");
                    b.toggle("  Show Text", () -> c.healingPoolText, v -> c.healingPoolText = v);
                    b.color("  Color", () -> c.healingPoolColor, v -> c.healingPoolColor = v);
                });
    }

    private static void tick() {
        if (++ticks % 20 == 0) inIsle = SkyblockUtils.isInArea("Crimson Isle");
        if (pool != null && System.currentTimeMillis() - seenAt > TIMEOUT_MS) pool = null;
    }

    /** handleParticleEvent HEAD. */
    public static void onParticle(ClientboundLevelParticlesPacket p) {
        TeslaMapsConfig c = cfg();
        if (!c.healingPoolMarker || !inIsle || !Minecraft.getInstance().isSameThread()) return;
        if (p.getParticle().getType() != ParticleTypes.CLOUD || p.getCount() != 1) return;
        if (Math.abs(p.getMaxSpeed()) > TOL || Math.abs(p.getXDist()) > TOL || Math.abs(p.getYDist()) > TOL
                || Math.abs(p.getZDist()) > TOL) return;
        long now = System.currentTimeMillis();
        int keep = 0;
        for (int i = 0; i < count; i++) {
            if (now - ts[i] > WINDOW_MS) continue;
            xs[keep] = xs[i]; ys[keep] = ys[i]; zs[keep] = zs[i]; ts[keep] = ts[i];
            keep++;
        }
        count = keep;
        if (count == xs.length) drop();
        xs[count] = p.getX(); ys[count] = p.getY(); zs[count] = p.getZ(); ts[count] = now;
        count++;
        if (count < RING) return;

        int from = count - RING;
        double cx = 0, cy = 0, cz = 0;
        for (int i = from; i < count; i++) { cx += xs[i]; cy += ys[i]; cz += zs[i]; }
        cx /= RING; cy /= RING; cz /= RING;
        for (int i = from; i < count; i++) {
            double dx = xs[i] - cx, dz = zs[i] - cz;
            if (Math.abs(ys[i] - cy) > TOL || Math.abs(dx * dx + dz * dz - RADIUS_SQ) > RING_TOL) {
                drop();
                return;
            }
        }
        count = 0;
        pool = new Vec3(cx, cy, cz);
        seenAt = now;
    }

    private static void drop() {
        for (int i = 1; i < count; i++) { xs[i - 1] = xs[i]; ys[i - 1] = ys[i]; zs[i - 1] = zs[i]; ts[i - 1] = ts[i]; }
        count--;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        Vec3 p = pool;
        if (p == null || !inIsle) return;
        TeslaMapsConfig c = cfg();
        int color = TeslaMapsConfig.parseColor(c.healingPoolColor) | 0xFF000000;
        ESPRenderer.drawTracerFromCamera(pose, p, color, cam, true);
        if (c.healingPoolText) ESPRenderer.drawText(pose, "§lHealing Pool", p.add(0, 1.5, 0), 1f, cam, true, true, color);
    }
}
