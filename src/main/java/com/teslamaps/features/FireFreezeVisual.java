package com.teslamaps.features;

// Ported from SkyHanni's features/itemabilities/FireFreezeFeatures.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FireFreezeVisual {

    private static final double RADIUS = 5.0;
    private static final long FREEZE_MS = 10_000;
    private static final float PARTICLE_OFFSET = 3.921568568330258E-4f;

    private static final class Area {
        final Vec3 center;
        final float firstPitch;
        long freezeAt;
        boolean known, frozen;
        Area(Vec3 center, float pitch) {
            this.center = center;
            this.firstPitch = pitch;
            this.freezeAt = timeFromPitch(pitch);
        }
        boolean finished(long now) {
            return frozen || now - freezeAt > 500;
        }
        boolean inside(Vec3 p, double extra) {
            double dx = p.x - center.x, dz = p.z - center.z;
            return dx * dx + dz * dz < (RADIUS + extra) * (RADIUS + extra);
        }
    }

    private static final List<Area> areas = new ArrayList<>();
    private static final Map<LivingEntity, Long> frozen = new HashMap<>();
    private static final List<Vec3> circles = new ArrayList<>();
    private static final List<String> circleText = new ArrayList<>();
    private static final List<LivingEntity> mobs = new ArrayList<>();
    private static final List<String> mobText = new ArrayList<>();
    private static final List<Integer> mobColor = new ArrayList<>();
    private static int circleColor;
    private static String lastColorHex;
    private static Feature feature;
    private static Object lastLevel;

    private FireFreezeVisual() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("FireFreezeVisual").section("Combat").when(() -> cfg().fireFreezeVisual)
                .tick(FireFreezeVisual::tick)
                .world(FireFreezeVisual::render)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Fire Freeze Staff");
                    b.toggle("Fire Freeze Visuals", () -> c.fireFreezeVisual, v -> c.fireFreezeVisual = v)
                            .desc("Detects a Fire Freeze Staff cast from its sound: clean circle, countdown until the freeze, "
                                    + "then a box and 10s timer on every frozen mob.")
                            .tags("fire freeze", "staff", "freeze", "ice");
                    b.toggle("  Clean Circle", () -> c.fireFreezeCircle, v -> c.fireFreezeCircle = v)
                            .desc("Replaces Hypixel's particle ring with a line circle (radius 5).");
                    b.color("  Circle Color", () -> c.fireFreezeCircleColor, v -> c.fireFreezeCircleColor = v);
                    b.toggle("  Freeze Countdown", () -> c.fireFreezeCountdown, v -> c.fireFreezeCountdown = v);
                    b.toggle("  Frozen Mob Box", () -> c.fireFreezeMobBox, v -> c.fireFreezeMobBox = v);
                    b.toggle("  Frozen Mob Timer", () -> c.fireFreezeMobTimer, v -> c.fireFreezeMobTimer = v);
                });
    }

    // pitch starts at 2.0 and drops by 0.5 every 2s
    private static long timeFromPitch(float pitch) {
        return System.currentTimeMillis() + (long) ((2.0 * pitch + 1) * 1000);
    }

    private static boolean on() {
        return feature != null && feature.active();
    }

    private static Area at(Vec3 p) {
        for (Area a : areas) if (a.center.distanceToSqr(p) < 0.01) return a;
        return null;
    }

    /** Main thread, from ClientPlayNetworkHandlerMixin. */
    public static void onSound(ClientboundSoundPacket packet) {
        if (!on()) return;
        SoundEvent s = packet.getSound().value();
        Vec3 p = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        if (s == SoundEvents.ELDER_GUARDIAN_AMBIENT) {
            if (packet.getVolume() != 0.2f) return;
            float pitch = packet.getPitch();
            if (pitch < 0 || pitch > 2) return;
            Area a = at(p);
            if (a == null) {
                areas.add(new Area(p, pitch));
            } else if (!a.known && a.firstPitch != pitch) {
                a.freezeAt = timeFromPitch(pitch);
                a.known = true;
            }
        } else if (s == SoundEvents.ANVIL_LAND) {
            if (packet.getVolume() != 0.6f || Math.abs(packet.getPitch() - 0.4920635f) > 0.0001f) return;
            Area a = at(p);
            if (a == null) return;
            freeze(a);
            areas.remove(a);
        }
    }

    private static void freeze(Area a) {
        if (a.frozen) return;
        a.frozen = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long until = System.currentTimeMillis() + FREEZE_MS;
        for (Entity e : mc.level.getEntities((Entity) null, new AABB(a.center, a.center).inflate(RADIUS, 6, RADIUS),
                x -> x instanceof LivingEntity && !(x instanceof ArmorStand) && !(x instanceof Player))) {
            if (!a.inside(e.position(), 0)) continue;
            LivingEntity le = (LivingEntity) e;
            Long prev = frozen.get(le);
            if (prev == null || prev < System.currentTimeMillis()) frozen.put(le, until);
        }
    }

    /** Main thread. True = drop Hypixel's ring particle. */
    public static boolean hideParticle(ClientboundLevelParticlesPacket packet) {
        if (areas.isEmpty() || !on() || !cfg().fireFreezeCircle) return false;
        if (packet.particle().getType() != ParticleTypes.DUST || packet.count() != 0 || packet.xMaxSpeed() != 1.0f) return false;
        if (Math.abs(packet.xDist() - PARTICLE_OFFSET) > 1e-6f || Math.abs(packet.zDist() - PARTICLE_OFFSET) > 1e-6f) return false;
        Vec3 p = new Vec3(packet.x(), packet.y(), packet.z());
        long now = System.currentTimeMillis();
        for (Area a : areas) if (!a.finished(now) && a.inside(p, 0.5)) return true;
        return false;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            areas.clear();
            frozen.clear();
        }
        TeslaMapsConfig c = cfg();
        if (!c.fireFreezeCircleColor.equals(lastColorHex)) {
            lastColorHex = c.fireFreezeCircleColor;
            circleColor = 0xFF000000 | TeslaMapsConfig.parseColor(lastColorHex);
        }
        long now = System.currentTimeMillis();
        areas.removeIf(a -> a.frozen || now - a.freezeAt > 2000);
        frozen.entrySet().removeIf(e -> !e.getKey().isAlive() || e.getKey().isRemoved() || e.getValue() < now);

        circles.clear();
        circleText.clear();
        for (Area a : areas) {
            if (a.finished(now)) continue;
            circles.add(a.center.add(0, 1, 0));
            circleText.add("§b❄ " + String.format(Locale.ROOT, "%.1fs", Math.max(0, a.freezeAt - now) / 1000.0));
        }
        mobs.clear();
        mobText.clear();
        mobColor.clear();
        for (var e : frozen.entrySet()) {
            long left = e.getValue() - now;
            double pct = 1 - Math.max(0, Math.min(1, (left / 1000.0 - 5) / 5));
            int g = (int) (0xFF * (1 - pct));
            mobs.add(e.getKey());
            mobColor.add(0xFF0000 | g << 8);
            mobText.add((pct < 0.34 ? "§b❄ §e" : pct < 0.67 ? "§b❄ §6" : "§b❄ §c") + String.format(Locale.ROOT, "%.1fs", left / 1000.0));
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        TeslaMapsConfig c = cfg();
        for (int i = 0; i < circles.size(); i++) {
            Vec3 p = circles.get(i);
            if (c.fireFreezeCircle) ESPRenderer.drawCircle(pose, p.x, p.y, p.z, RADIUS, circleColor, 2f, cam);
            if (c.fireFreezeCountdown) ESPRenderer.drawText(pose, circleText.get(i), p, 1f, cam, true, true);
        }
        for (int i = 0; i < mobs.size(); i++) {
            LivingEntity e = mobs.get(i);
            int rgb = mobColor.get(i);
            AABB box = ESPRenderer.lerpBox(e).inflate(0.1);
            if (c.fireFreezeMobBox) ESPRenderer.drawFilledBox(pose, box, 0x80000000 | rgb, cam, false);
            if (c.fireFreezeMobTimer) {
                ESPRenderer.drawText(pose, mobText.get(i), box.getBottomCenter().add(0, box.getYsize() + 0.4, 0), 1f, cam, true, true);
            }
        }
    }
}
