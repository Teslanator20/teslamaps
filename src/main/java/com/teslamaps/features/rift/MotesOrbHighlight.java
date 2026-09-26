package com.teslamaps.features.rift;

// Ported from SkyHanni's RiftMotesOrb.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class MotesOrbHighlight {

    private static final class Orb {
        double x, y, z;
        int counter;
        final long start;
        long last;
        boolean isOrb, pickedUp;

        Orb(double x, double y, double z, long now) {
            this.x = x; this.y = y; this.z = z;
            start = last = now;
        }
    }

    private static final List<Orb> orbs = new ArrayList<>();
    private static boolean inRift;
    private static int ticks;
    private static Feature feature;

    private MotesOrbHighlight() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("MotesOrbHighlight").section("Rift").when(() -> cfg().motesOrbHighlight)
                .tick(MotesOrbHighlight::tick)
                .world(MotesOrbHighlight::render)
                .serverChat(MotesOrbHighlight::onChat)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Motes Orb Highlight", () -> c.motesOrbHighlight, v -> c.motesOrbHighlight = v)
                            .desc("Rift: finds Motes Orbs from their particles and marks them with a purple box, grey once picked up.")
                            .tags("motes", "orb", "rift");
                    b.slider("  Size", 1f, 10f, () -> c.motesOrbSize, v -> c.motesOrbSize = v, 0);
                    b.toggle("  Hide Orb Particles", () -> c.motesOrbHideParticles, v -> c.motesOrbHideParticles = v);
                });
    }

    private static void tick() {
        if (++ticks % 20 == 0) inRift = RiftSpot.inRift();
        long now = System.currentTimeMillis();
        orbs.removeIf(o -> now > o.last + 2000);
        for (Orb o : orbs) {
            double age = (now - o.start) / 1000.0;
            if (age < 0.5) continue;
            double pps = o.counter / age;
            if (pps < 60 || pps > 90) continue;
            o.isOrb = true;
            if (now > o.last + 300) o.pickedUp = true;
        }
    }

    /** Particle hide chain, main thread. True = drop the particle. */
    public static boolean onParticle(ClientboundLevelParticlesPacket p) {
        if (!inRift || p.getParticle().getType() != ParticleTypes.ENTITY_EFFECT || feature == null || !feature.active()) return false;
        double x = p.getX() - 0.5, y = p.getY(), z = p.getZ() - 0.5;
        long now = System.currentTimeMillis();
        Orb orb = null;
        for (Orb o : orbs) {
            double dx = o.x - x, dy = o.y - y, dz = o.z - z;
            if (dx * dx + dy * dy + dz * dz < 9) { orb = o; break; }
        }
        if (orb == null) {
            orb = new Orb(x, y, z, now);
            orbs.add(orb);
        }
        orb.x = x; orb.y = y; orb.z = z;
        orb.last = now;
        orb.counter++;
        orb.pickedUp = false;
        return orb.isOrb && cfg().motesOrbHideParticles;
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.startsWith("ORB! Picked up +") || !t.contains(" Motes")) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) return;
            Orb best = null;
            double bestD = Double.MAX_VALUE;
            for (Orb o : orbs) {
                double d = mc.player.distanceToSqr(o.x + 0.5, o.y, o.z + 0.5);
                if (d < bestD) { bestD = d; best = o; }
            }
            if (best != null) best.pickedUp = true;
        });
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (orbs.isEmpty()) return;
        double grow = (cfg().motesOrbSize - 5) * 0.1;
        for (Orb o : orbs) {
            if (!o.isOrb) continue;
            int rgb = o.pickedUp ? 0xAAAAAA : 0xFF55FF;
            double y = o.y + 0.5;
            AABB box = new AABB(o.x - grow, y - grow, o.z - grow, o.x + 1 + grow, y + 1 + grow, o.z + 1 + grow);
            ESPRenderer.drawFilledBox(pose, box, 0x60000000 | rgb, cam, true);
            ESPRenderer.drawText(pose, o.pickedUp ? "§7Motes Orb" : "§dMotes Orb",
                    new Vec3(o.x + 0.5, y + 1.3 + grow, o.z + 0.5), 1.5f, cam, true, false);
        }
    }
}
