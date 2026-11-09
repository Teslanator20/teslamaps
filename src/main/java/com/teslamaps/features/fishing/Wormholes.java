package com.teslamaps.features.fishing;

// Ported from SkyOcean's api/WormholeAPI.kt + features/fishing/WormholeFeatures.kt and SkyHanni's WormholeFinder.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.FishingHelpers;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class Wormholes {

    private static final class Hole {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        double x, y, z, radius;
        long lastParticle;
        boolean fishedIn;

        void add(double px, double py, double pz, long now) {
            lastParticle = now;
            minX = Math.min(minX, px); maxX = Math.max(maxX, px);
            minZ = Math.min(minZ, pz); maxZ = Math.max(maxZ, pz);
            x = (minX + maxX) / 2;
            z = (minZ + maxZ) / 2;
            y = py;
            radius = Math.round((maxX - minX)) / 2.0;
        }
    }

    private record Ray(Vec3 from, Vec3 dir) {}

    private static final double CLUSTER_SQ = 18;
    private static final double RAY_LEN = 48;

    private static final List<Hole> holes = new ArrayList<>();
    private static volatile List<Hole> drawList = List.of();
    private static volatile List<Ray> rays = List.of();
    private static volatile List<Hole> pointed = List.of();
    private static long lastFish, lastClosed;
    private static boolean inIsland, froggles;
    private static Object lastLevel;
    private static int ticks;

    private Wormholes() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("Wormholes").section("Fishing").when(() -> cfg().wormholeHelper)
                .tick(Wormholes::tick)
                .world(Wormholes::render)
                .config("Fishing", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Wormholes");
                    b.toggle("Wormhole Helper", () -> c.wormholeHelper, v -> c.wormholeHelper = v)
                            .desc("Lotus Atoll / Crimson Isle: finds wormholes from their particles and draws them as a circle")
                            .tags("wormhole", "lotus", "froggles", "fishing", "crimson");
                    b.toggle("  Fill", () -> c.wormholeFill, v -> c.wormholeFill = v);
                    b.toggle("  Rim", () -> c.wormholeRim, v -> c.wormholeRim = v);
                    b.color("  Color", () -> c.colorWormhole, v -> c.colorWormhole = v);
                    b.toggle("  Hide Wormhole Particles", () -> c.wormholeHideParticles, v -> c.wormholeHideParticles = v);
                    b.toggle("  Despawn Warning", () -> c.wormholeDespawnWarn, v -> c.wormholeDespawnWarn = v)
                            .desc("Chat + title when a wormhole you fished in during the last 30 s disappears");
                    b.toggle("  Froggles Direction", () -> c.wormholeFroggles, v -> c.wormholeFroggles = v)
                            .desc("With Froggles on: a line along the arrow next to you, and a box on a known wormhole in that direction");
                    b.toggle("  Wormhole Closed Alert", () -> c.wormholeClosedAlert, v -> c.wormholeClosedAlert = v)
                            .desc("With Froggles on: title when the wormhole teleport sound plays");
                });
    }

    private static boolean enabled() {
        TeslaMapsConfig c = cfg();
        return c.wormholeHelper && c.section("Fishing");
    }

    private static boolean isWormholeParticle(ClientboundLevelParticlesPacket p) {
        var type = p.particle().getType();
        if (type == ParticleTypes.ENCHANT) return p.count() == 4 && p.xMaxSpeed() == -1.2f;
        if (type == ParticleTypes.PORTAL) return p.count() == 5 && p.xMaxSpeed() == 0.25f;
        return false;
    }

    /** Particle hide chain, main thread. True = drop the particle. */
    public static boolean onParticle(ClientboundLevelParticlesPacket p) {
        if (!inIsland || !enabled() || !isWormholeParticle(p)) return false;
        long now = System.currentTimeMillis();
        prune(now);
        Hole best = null;
        double bestD = CLUSTER_SQ;
        for (Hole h : holes) {
            if (h.lastParticle == 0) continue;
            double dx = p.x() - h.x, dz = p.z() - h.z;
            double d = dx * dx + dz * dz;
            if (d <= bestD) { bestD = d; best = h; }
        }
        if (best == null) {
            best = new Hole();
            holes.add(best);
        }
        best.add(p.x(), p.y(), p.z(), now);
        drawList = new ArrayList<>(holes);
        return cfg().wormholeHideParticles;
    }

    private static void prune(long now) {
        boolean changed = false;
        for (Iterator<Hole> it = holes.iterator(); it.hasNext(); ) {
            Hole h = it.next();
            if (now - h.lastParticle <= 3000) continue;
            it.remove();
            changed = true;
            despawned(h, now);
        }
        if (changed) drawList = new ArrayList<>(holes);
    }

    private static void despawned(Hole h, long now) {
        if (!cfg().wormholeDespawnWarn || !h.fishedIn || now - lastFish > 30_000) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(h.x, h.y, h.z) > 1600) return;
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §cWormhole despawned!"));
        mc.gui.hud.setTimes(0, 60, 10);
        mc.gui.hud.setTitle(Component.literal("§cWormhole despawned!"));
        mc.gui.hud.setSubtitle(Component.literal(""));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            holes.clear();
            drawList = List.of();
            rays = List.of();
            pointed = List.of();
        }
        long now = System.currentTimeMillis();
        prune(now);
        FishingHook hook = FishingHelpers.bobber();
        if (hook != null) {
            Hole best = null;
            double bestD = Double.MAX_VALUE;
            for (Hole h : holes) {
                if (Math.abs(hook.getY() - h.y) >= 3) continue;
                double dx = hook.getX() - h.x, dz = hook.getZ() - h.z;
                double d = dx * dx + dz * dz;
                double r = h.radius + 1.5;
                if (d < bestD && d <= r * r) { bestD = d; best = h; }
            }
            if (best != null) {
                best.fishedIn = true;
                lastFish = now;
            }
        }
        if (++ticks % 10 != 0) return;
        String area = SkyblockUtils.getArea();
        inIsland = area.equalsIgnoreCase("Lotus Atoll") || area.equalsIgnoreCase("Crimson Isle");
        String helmet = ItemUtil.skyblockId(mc.player.getItemBySlot(EquipmentSlot.HEAD));
        froggles = "FROGGLES_DIAMOND".equals(helmet) || "FROGGLES_GOLD".equals(helmet);
        if (!inIsland || !froggles || !cfg().wormholeFroggles) {
            rays = List.of();
            pointed = List.of();
            return;
        }
        List<Ray> r = new ArrayList<>(2);
        List<Hole> hit = new ArrayList<>(2);
        for (Display.TextDisplay td : EntityCache.byType(Display.TextDisplay.class)) {
            if (td.isRemoved() || td.distanceToSqr(mc.player) > 9) continue;
            Vec3 dir = forward(td);
            if (dir == null) continue;
            Vec3 from = td.position();
            r.add(new Ray(from, dir));
            for (Hole h : holes) {
                Vec3 d = new Vec3(h.x - from.x, 0, h.z - from.z);
                if (d.lengthSqr() < 1e-4) continue;
                if (d.normalize().dot(dir) >= 0.98 && !hit.contains(h)) hit.add(h);
            }
        }
        rays = r;
        pointed = hit;
    }

    private static Vec3 forward(Display.TextDisplay td) {
        Display.RenderState rs = td.renderState();
        if (rs == null) return new Vec3(0, 0, 1);
        var t = rs.transformation().get(0f);
        if (t == null) return new Vec3(0, 0, 1);
        Vector3f y = new Vector3f(0f, 1f, 0f);
        t.leftRotation().transform(y);
        Vec3 v = new Vec3(y.x, 0, y.z);
        return v.lengthSqr() < 1e-6 ? null : v.normalize();
    }

    /** From SoundPlayMixin. */
    public static boolean listening() {
        TeslaMapsConfig c = cfg();
        return c.wormholeHelper && c.wormholeClosedAlert && froggles && inIsland;
    }

    public static void onSound(String id, float pitch) {
        if (!"minecraft:entity.enderman.teleport".equals(id) || Math.abs(pitch - 0.6984127f) > 0.001f) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            long now = System.currentTimeMillis();
            if (mc.player == null || !enabled() || now - lastClosed < 3000) return;
            lastClosed = now;
            mc.gui.hud.setTimes(0, 40, 10);
            mc.gui.hud.setTitle(Component.literal("§cWormhole closed!"));
            mc.gui.hud.setSubtitle(Component.literal(""));
        });
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!inIsland) return;
        TeslaMapsConfig c = cfg();
        int color = TeslaMapsConfig.parseColor(c.colorWormhole);
        int rim = color | 0xFF000000;
        for (Hole h : drawList) {
            if (h.radius <= 0) continue;
            if (c.wormholeFill) ESPRenderer.drawDisc(pose, h.x, h.y, h.z, h.radius, color, cam);
            if (c.wormholeRim) {
                ESPRenderer.drawCircle(pose, h.x, h.y, h.z, h.radius, rim, 2f, cam);
                ESPRenderer.drawCircle(pose, h.x, h.y + 0.1, h.z, h.radius, rim, 2f, cam);
            }
        }
        for (Ray r : rays) {
            ESPRenderer.drawLine(pose, r.from, r.from.add(r.dir.scale(RAY_LEN)), 0xFFFF55FF, 2f, cam);
        }
        for (Hole h : pointed) {
            ESPRenderer.drawFilledBox(pose, new AABB(h.x - 0.5, h.y, h.z - 0.5, h.x + 0.5, h.y + 1, h.z + 0.5), 0x80FF55FF, cam, true);
            ESPRenderer.drawText(pose, "§dWormhole", new Vec3(h.x, h.y + 1.5, h.z), 1.5f, cam);
        }
    }
}
