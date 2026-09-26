package com.teslamaps.features.rift;

// After SkyHanni's rift/area/stillgorechateau/SplatterHearts.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.FishingHelpers;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.FeatureRegistry;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Stillgore Château: the heart particles of Splatter Cruxes get red boxes. */
public final class RiftSplatterHearts {

    private static volatile boolean active;
    private static long lastHeart;
    private static List<AABB> shown = List.of();
    private static final List<AABB> current = new ArrayList<>();
    private static int ticks;

    private RiftSplatterHearts() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        FeatureRegistry.category("Rift", "Skyblock", true);
        Feature.of("RiftSplatterHearts").section("Rift").when(() -> cfg().riftSplatterHearts)
                .tickAlways(RiftSplatterHearts::tick)
                .world(RiftSplatterHearts::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Splatter Hearts Highlight", () -> c.riftSplatterHearts, v -> c.riftSplatterHearts = v)
                            .desc("Stillgore Château: boxes the hearts Splatter Cruxes drop so they are easy to pick up.")
                            .tags("rift", "splatter", "crux", "hearts", "stillgore", "chateau");
                    b.color("  Heart Color", () -> c.riftSplatterHeartsColor, v -> c.riftSplatterHeartsColor = v);
                });
    }

    private static void tick() {
        if (ticks++ % 20 != 0) return;
        boolean on = false;
        if (Minecraft.getInstance().player != null && SkyblockUtils.getArea().contains("Rift")) {
            String sub = FishingHelpers.subArea();
            on = sub.startsWith("Stillgore") || sub.equals("Oubliette");
        }
        active = on;
    }

    public static void onParticle(ClientboundLevelParticlesPacket p) {
        if (!active || !cfg().riftSplatterHearts || !Minecraft.getInstance().isSameThread()) return;
        if (p.getParticle().getType() != ParticleTypes.HEART || p.getCount() != 3 || p.getMaxSpeed() != 0f) return;
        long now = System.currentTimeMillis();
        if (now - lastHeart > 50) {
            shown = new ArrayList<>(current);
            current.clear();
        }
        lastHeart = now;
        if (current.size() < 64) current.add(new AABB(p.getX() - 0.5, p.getY() + 0.3, p.getZ() - 0.5,
                p.getX() + 0.5, p.getY() + 1.3, p.getZ() + 0.5));
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!active || System.currentTimeMillis() - lastHeart > 300) return;
        List<AABB> l = shown;
        if (l.isEmpty()) return;
        int color = TeslaMapsConfig.parseColor(cfg().riftSplatterHeartsColor);
        for (int i = 0; i < l.size(); i++) ESPRenderer.drawFilledBox(pose, l.get(i), color, cam, true);
    }
}
