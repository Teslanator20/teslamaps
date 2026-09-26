package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.BezierFitter;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Hotspot Radar shoots a curved trail of flame particles at the nearest fishing
 * hotspot. Fitting a curve through the first few particles gives away where it points,
 * long before the trail arrives.
 */
public class HotspotRadar {

    private static final String RADAR_ID = "HOTSPOT_RADAR";
    private static final long USE_WINDOW_MS = 2000;   // trail follows the right click within a second
    private static final long HELD_WINDOW_MS = 2000;  // fallback if the use hook is missed
    private static final long BURST_GAP_MS = 1000;
    private static final long KEEP_MS = 30_000;
    private static final double ARRIVED_DISTANCE = 10;
    private static final double MAX_GUESS_DISTANCE = 800;
    private static final long REUSE_GUARD_MS = 200;
    private static final double SNAP_DISTANCE_SQ = 15 * 15;
    private static final double UNKNOWN_DISTANCE = 40;
    private static final java.util.Set<String> NO_HOTSPOT_AREAS = java.util.Set.of(
            "the catacombs", "catacombs", "kuudra", "the rift", "garden", "private island", "dungeon hub");

    private static final BezierFitter fitter = new BezierFitter(3);

    private static volatile Vec3 hotspot = null;
    private static volatile long foundAt = 0;
    private static volatile long lastUse = 0;
    private static volatile long lastHeld = 0;
    private static volatile long lastParticle = 0;
    private static volatile boolean islandOk = true;
    private static volatile Vec3 snapped = null;
    private static Vec3 snappedFor = null;
    private static int tickCounter = 0;
    private static long lastDebug = 0;

    /** Right clicking the radar starts a new trail. Main thread. */
    public static void onUseItem(ItemStack stack) {
        if (!TeslaMapsConfig.get().hotspotRadar) return;
        if (!RADAR_ID.equals(ItemUtil.skyblockId(stack))) return;
        long now = System.currentTimeMillis();
        // a second click while the trail still flies must not wipe it
        if (now - lastParticle < REUSE_GUARD_MS) {
            debug("radar reuse ignored, trail still flying", true);
            return;
        }
        lastUse = now;
        synchronized (fitter) {
            fitter.reset();
        }
        debug("radar used", true);
    }

    /** Keeps a main thread record of the held item so the netty thread never touches the inventory. */
    public static void tick() {
        if (!TeslaMapsConfig.get().hotspotRadar) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (RADAR_ID.equals(ItemUtil.skyblockId(mc.player.getMainHandItem()))) {
            lastHeld = System.currentTimeMillis();
        }
        if (++tickCounter % 10 != 0) return;
        islandOk = com.teslamaps.utils.SkyblockUtils.isInSkyblock()
                && !NO_HOTSPOT_AREAS.contains(com.teslamaps.utils.SkyblockUtils.getArea().toLowerCase(java.util.Locale.ROOT));
        Vec3 guess = hotspot;
        if (guess == null) {
            snapped = null;
            snappedFor = null;
            return;
        }
        if (mc.level != null && !mc.level.getWorldBorder().isWithinBounds(guess.x, guess.z)) {
            reset();
            return;
        }
        if (snapped != null && guess == snappedFor) return;
        snappedFor = guess;
        snapped = nearestHotspotStand(guess);
    }

    private static Vec3 nearestHotspotStand(Vec3 guess) {
        Vec3 best = null;
        double bestDist = SNAP_DISTANCE_SQ;
        for (net.minecraft.world.entity.Entity e : com.teslamaps.utils.EntityCache.armorStands()) {
            if (!e.isAlive() || !e.hasCustomName()) continue;
            if (!"HOTSPOT".equals(com.teslamaps.utils.StringUtil.strip(e.getCustomName().getString()).trim())) continue;
            double dx = e.getX() - guess.x, dz = e.getZ() - guess.z;
            double d = dx * dx + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = new Vec3(e.getX(), e.getY() - 1, e.getZ());
            }
        }
        return best;
    }

    /** Called from ClientPlayNetworkHandlerMixin on the netty thread - no world access here. */
    public static void onParticlePacket(ClientboundLevelParticlesPacket packet) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.hotspotRadar) return;
        if (packet.getParticle().getType() != ParticleTypes.FLAME) return;
        if (packet.getCount() != 1 || packet.getMaxSpeed() != 0f) return;
        if (!islandOk) return;

        long now = System.currentTimeMillis();
        if (now - lastUse > USE_WINDOW_MS && now - lastHeld > HELD_WINDOW_MS) {
            debug("flame ignored, no radar use", false);
            return;
        }

        Vec3 guess;
        synchronized (fitter) {
            // a gap means the previous trail ended and this is a new cast
            if (now - lastParticle > BURST_GAP_MS) fitter.reset();
            lastParticle = now;

            if (!fitter.tryAdd(new Vec3(packet.getX(), packet.getY(), packet.getZ()), 3.0)) return;
            guess = fitter.solve();
        }
        if (guess == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && guess.distanceTo(mc.player.position()) > MAX_GUESS_DISTANCE) {
            debug("guess out of range: " + fmt(guess), true);
            return;
        }
        hotspot = guess;
        foundAt = now;
        debug("guess " + fmt(guess), true);
    }

    public static Vec3 guess() {
        Vec3 s = snapped;
        return s != null ? s : hotspot;
    }

    public static void reset() {
        hotspot = null;
        snapped = null;
        foundAt = 0;
        synchronized (fitter) {
            fitter.reset();
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Vec3 at = guess();
        if (!c.hotspotRadar || at == null) return;
        if (System.currentTimeMillis() - foundAt > KEEP_MS) {
            reset();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double distance = at.distanceTo(mc.player.getEyePosition());
        // once you are on top of it the guess has served its purpose
        if (distance <= ARRIVED_DISTANCE) {
            reset();
            return;
        }

        boolean unknown = c.hotspotRadarUnknown && snapped == null && distance < UNKNOWN_DISTANCE;
        int color = unknown ? 0xFFFF5555 : TeslaMapsConfig.parseColor(c.colorHotspot) | 0xFF000000;
        if (c.hotspotLine) {
            ESPRenderer.drawTracerFromCamera(matrices, at, color, cameraPos, true);
        }
        if (c.hotspotBeam) {
            ESPRenderer.drawBeaconBeam(matrices, net.minecraft.core.BlockPos.containing(at), color, cameraPos);
        }
        com.teslamaps.features.waypoints.WaypointSet.drawLabel(matrices, cameraPos, unknown ? "§c§lUNKNOWN HOTSPOT" : "§d§lHOTSPOT", at.add(0, 1.7, 0), 1.7f);
        com.teslamaps.features.waypoints.WaypointSet.drawLabel(matrices, cameraPos, "§e" + (int) distance + "m", at.add(0, 1.2, 0), 1.0f);
    }

    private static String fmt(Vec3 v) {
        return (int) v.x + " " + (int) v.y + " " + (int) v.z;
    }

    private static void debug(String s, boolean always) {
        if (!TeslaMapsConfig.get().debugMode) return;
        long now = System.currentTimeMillis();
        if (!always) {
            if (now - lastDebug < 2000) return;
            lastDebug = now;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§8[HotspotDbg] " + s));
        });
    }
}
