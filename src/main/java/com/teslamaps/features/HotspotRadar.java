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

    private static final BezierFitter fitter = new BezierFitter(3);

    private static volatile Vec3 hotspot = null;
    private static volatile long foundAt = 0;
    private static volatile long lastUse = 0;
    private static volatile long lastHeld = 0;
    private static volatile long lastParticle = 0;
    private static long lastDebug = 0;

    /** Right clicking the radar starts a new trail. Main thread. */
    public static void onUseItem(ItemStack stack) {
        if (!TeslaMapsConfig.get().hotspotRadar) return;
        if (!RADAR_ID.equals(ItemUtil.skyblockId(stack))) return;
        lastUse = System.currentTimeMillis();
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
    }

    /** Called from ClientPlayNetworkHandlerMixin on the netty thread - no world access here. */
    public static void onParticlePacket(ClientboundLevelParticlesPacket packet) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.hotspotRadar) return;
        if (packet.getParticle().getType() != ParticleTypes.FLAME) return;
        if (packet.getCount() != 1 || packet.getMaxSpeed() != 0f) return;

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
        return hotspot;
    }

    public static void reset() {
        hotspot = null;
        foundAt = 0;
        synchronized (fitter) {
            fitter.reset();
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Vec3 at = hotspot;
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

        int color = TeslaMapsConfig.parseColor(c.colorHotspot) | 0xFF000000;
        if (c.hotspotLine) {
            ESPRenderer.drawTracerFromCamera(matrices, at, color, cameraPos, true);
        }
        if (c.hotspotBeam) {
            ESPRenderer.drawBeaconBeam(matrices, net.minecraft.core.BlockPos.containing(at), color, cameraPos);
        }
        ESPRenderer.drawText(matrices, "§d§lHOTSPOT", at.add(0, 1.7, 0), 1.7f, cameraPos);
        ESPRenderer.drawText(matrices, "§e" + (int) distance + "m", at.add(0, 1.2, 0), 1.0f, cameraPos);
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
