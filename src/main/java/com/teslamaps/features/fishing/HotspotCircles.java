package com.teslamaps.features.fishing;

// Ported from GanKura's HotspotAreaHandler / HotspotParticleMixin / HotspotPerk (after SkyOcean's HotspotAPI)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HotspotCircles {

    private static final double MAX_DRAW_DISTANCE_SQ = 128 * 128;

    private static final class Spot {
        Vec3 center;
        final Map<Double, Integer> votes = new HashMap<>();
        double radius;
        int color = 0xFF55FF;
        boolean seen;
    }

    private static final Map<Long, Spot> spots = new HashMap<>();
    private static volatile List<Spot> drawList = List.of();
    private static Object lastLevel;
    private static int ticks;

    private HotspotCircles() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("HotspotCircles").section("Fishing").when(() -> cfg().hotspotCircle)
                .tick(HotspotCircles::tick)
                .world(HotspotCircles::render);
    }

    private static int perkColor(String t) {
        if (t.contains("Sea Creature Chance")) return 0x00AAAA;
        if (t.contains("Fishing Speed")) return 0x55FFFF;
        if (t.contains("Double Hook Chance")) return 0x5555FF;
        if (t.contains("Treasure Chance") || t.contains("Trophy Chance")) return 0xFFAA00;
        if (t.contains("Shard")) return 0xFFFF55;
        return 0xFF55FF;
    }

    private static double maxRadius() {
        String area = SkyblockUtils.getArea();
        if (area.equalsIgnoreCase("Crimson Isle") || area.equalsIgnoreCase("Torrhus Canyon")) return 25;
        if (area.equalsIgnoreCase("Jerry's Workshop") || area.equalsIgnoreCase("Lotus Atoll")) return 16;
        return 9;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            spots.clear();
        }
        if (++ticks % 10 != 0) return;
        for (Spot s : spots.values()) s.seen = false;
        List<ArmorStand> named = EntityCache.namedArmorStands();
        for (ArmorStand stand : named) {
            if (!"HOTSPOT".equals(StringUtil.strip(stand.getCustomName().getString()).trim())) continue;
            long key = ((long) Math.floor(stand.getX()) << 32) ^ (int) Math.floor(stand.getZ());
            Spot s = spots.computeIfAbsent(key, k -> new Spot());
            s.center = surface(mc, stand);
            s.color = 0xFF55FF;
            for (ArmorStand o : named) {
                if (o == stand || o.getX() != stand.getX() || o.getZ() != stand.getZ()) continue;
                if (Math.abs(o.getY() - stand.getY()) > 1.0) continue;
                s.color = perkColor(StringUtil.strip(o.getCustomName().getString()));
                break;
            }
            s.seen = true;
        }
        spots.values().removeIf(s -> !s.seen);
        List<Spot> l = new ArrayList<>(spots.size());
        for (Spot s : spots.values()) if (s.radius > 0) l.add(s);
        drawList = l;
    }

    private static Vec3 surface(Minecraft mc, ArmorStand stand) {
        BlockPos base = BlockPos.containing(stand.getX(), stand.getY(), stand.getZ());
        for (int i = 0; i < 4; i++) {
            BlockPos p = base.below(i);
            FluidState f = mc.level.getFluidState(p);
            if (f.isEmpty()) continue;
            return new Vec3(stand.getX(), p.getY() + f.getHeight(mc.level, p) + 0.05, stand.getZ());
        }
        return new Vec3(stand.getX(), stand.getY(), stand.getZ());
    }

    private static final Vector3f WATER = new Vector3f(1.0f, 0.4117647f, 0.7058824f);

    private static boolean edgeParticle(ClientboundLevelParticlesPacket p) {
        var type = p.particle().getType();
        if (type == ParticleTypes.SMOKE) return p.count() == 5 || p.count() == 2;
        if (type != ParticleTypes.DUST) return false;
        if (p.count() != 0 || p.xMaxSpeed() != 1f || p.xDist() != 1f) return false;
        Vector3f c = ((DustParticleOptions) p.particle()).getColor();
        return Math.abs(c.x - WATER.x) < 0.01f && Math.abs(c.y - WATER.y) < 0.01f && Math.abs(c.z - WATER.z) < 0.01f;
    }

    /** Particle hide chain, main thread. Votes the radius; true = drop the particle. */
    public static boolean onParticle(ClientboundLevelParticlesPacket p) {
        TeslaMapsConfig c = cfg();
        if (!c.hotspotCircle || spots.isEmpty() || !c.section("Fishing")) return false;
        if (!edgeParticle(p)) return false;
        double max = maxRadius();
        Spot best = null;
        double bestD = (max + 1) * (max + 1);
        for (Spot s : spots.values()) {
            if (s.center == null) continue;
            double dx = p.x() - s.center.x, dz = p.z() - s.center.z;
            double d = dx * dx + dz * dz;
            if (d > bestD) continue;
            bestD = d;
            best = s;
        }
        if (best == null) return false;
        double r = Math.round(Math.sqrt(bestD) * 2) / 2.0;
        if (r <= max) {
            int n = best.votes.merge(r, 1, Integer::sum);
            Integer cur = best.votes.get(best.radius);
            if (cur == null || n >= cur) best.radius = r;
        }
        return c.hotspotCircleHideParticles;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        TeslaMapsConfig c = cfg();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int alpha = Math.round(Math.max(0f, Math.min(1f, c.hotspotCircleAlpha)) * 255);
        for (Spot s : drawList) {
            Vec3 at = s.center;
            if (at == null || at.distanceToSqr(mc.player.position()) > MAX_DRAW_DISTANCE_SQ) continue;
            if (c.hotspotCircleFill && alpha > 0) ESPRenderer.drawDisc(pose, at.x, at.y, at.z, s.radius, (alpha << 24) | s.color, cam);
            if (c.hotspotCircleRim) {
                ESPRenderer.drawCircle(pose, at.x, at.y, at.z, s.radius, 0xFF000000 | s.color, 2f, cam);
                ESPRenderer.drawCircle(pose, at.x, at.y + 1, at.z, s.radius, 0x80000000 | s.color, 1.5f, cam);
            }
        }
    }
}
