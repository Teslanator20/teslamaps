package com.teslamaps.features;

// Ported from Odin's MageBeam.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class MageBeam {

    private static final class Beam {
        final List<Vec3> points = new ArrayList<>();
        int lastTick;
        int born;
        Vec3 near, far;
    }

    private static final List<Beam> beams = new ArrayList<>();
    private static int tick;
    private static Object lastLevel;

    private MageBeam() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MageBeam").section("Render").when(() -> cfg().mageBeam)
                .tick(MageBeam::tick)
                .world(MageBeam::render)
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Mage Beam");
                    b.toggle("Custom Mage Beam", () -> c.mageBeam, v -> c.mageBeam = v)
                            .desc("Draws the mage beam as one clean line instead of firework particles (dungeons).");
                    b.color("  Beam Color", () -> c.mageBeamColor, v -> c.mageBeamColor = v);
                    b.slider("  Duration (ticks)", 1f, 100f, () -> (float) c.mageBeamDuration, v -> c.mageBeamDuration = Math.round(v), 0);
                    b.slider("  Line Width", 1f, 10f, () -> c.mageBeamWidth, v -> c.mageBeamWidth = v, 1);
                    b.toggle("  Depth Check", () -> c.mageBeamDepth, v -> c.mageBeamDepth = v);
                    b.toggle("  Hide Particles", () -> c.mageBeamHideParticles, v -> c.mageBeamHideParticles = v);
                    b.slider("  Min Points", 2f, 10f, () -> (float) c.mageBeamMinPoints, v -> c.mageBeamMinPoints = Math.round(v), 0);
                });
    }

    /** Main thread only. True = drop the particle. */
    public static boolean onParticle(ClientboundLevelParticlesPacket p) {
        TeslaMapsConfig c = cfg();
        if (c.hideMageBeams && !c.mageBeam && p.getParticle().getType() == ParticleTypes.FIREWORK
                && DungeonManager.isInDungeon()) return true;
        if (!c.mageBeam || p.getParticle().getType() != ParticleTypes.FIREWORK || !c.section("Render")
                || !DungeonManager.isInDungeon()) return false;
        Vec3 pt = new Vec3(p.getX(), p.getY(), p.getZ());
        Beam recent = beams.isEmpty() ? null : beams.get(beams.size() - 1);
        if (recent != null && tick - recent.lastTick < 1 && inDirection(recent.points, pt)) {
            recent.points.add(pt);
            recent.lastTick = tick;
        } else {
            Beam b = new Beam();
            b.points.add(pt);
            b.lastTick = tick;
            b.born = tick;
            beams.add(b);
        }
        return c.mageBeamHideParticles;
    }

    private static boolean inDirection(List<Vec3> points, Vec3 pt) {
        if (points.size() <= 1) return true;
        Vec3 first = points.get(0), last = points.get(points.size() - 1);
        return last.subtract(first).normalize().dot(pt.subtract(last).normalize()) > 0.99;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            beams.clear();
        }
        tick++;
        int dur = cfg().mageBeamDuration;
        beams.removeIf(b -> tick - b.born > dur);
        Vec3 me = mc.player.position();
        for (Beam b : beams) {
            Vec3 near = b.points.get(0), far = near;
            double min = near.distanceToSqr(me), max = min;
            for (int i = 1; i < b.points.size(); i++) {
                Vec3 p = b.points.get(i);
                double d = p.distanceToSqr(me);
                if (d < min) { min = d; near = p; }
                if (d > max) { max = d; far = p; }
            }
            b.near = near;
            b.far = far;
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (beams.isEmpty()) return;
        TeslaMapsConfig c = cfg();
        int color = TeslaMapsConfig.parseColor(c.mageBeamColor);
        for (Beam b : beams) {
            if (b.points.size() < c.mageBeamMinPoints || b.near == null || b.near == b.far) continue;
            ESPRenderer.drawLine(pose, b.near, b.far, color, c.mageBeamWidth, cam, !c.mageBeamDepth);
        }
    }
}
