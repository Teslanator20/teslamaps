// Ported from SkyHanni's FishingHotspotRadar.kt (LGPL-2.1), see NOTICE.md
package com.teslamaps.features;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.BezierFitter;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.phys.Vec3;

/**
 * The Hotspot Radar shoots a curved trail of flame particles at the nearest fishing
 * hotspot. Fitting a curve through the first few particles gives away where it points,
 * long before the trail arrives.
 */
public class HotspotRadar {

    private static final String RADAR_ID = "HOTSPOT_RADAR";
    private static final long BURST_GAP_MS = 1000;
    private static final long KEEP_MS = 30_000;
    private static final double ARRIVED_DISTANCE = 10;

    private static final BezierFitter fitter = new BezierFitter(3);

    private static Vec3 hotspot = null;
    private static long foundAt = 0;
    private static long lastParticle = 0;

    /** Called from ClientPlayNetworkHandlerMixin on the netty thread - no world access here. */
    public static void onParticlePacket(ClientboundLevelParticlesPacket packet) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.hotspotRadar) return;
        if (packet.getParticle().getType() != ParticleTypes.FLAME) return;
        if (packet.getCount() != 1 || packet.getMaxSpeed() != 0f) return;
        if (!holdingRadar()) return;

        long now = System.currentTimeMillis();
        // a gap means the previous trail ended and this is a new cast
        if (now - lastParticle > BURST_GAP_MS) fitter.reset();
        lastParticle = now;

        if (!fitter.tryAdd(new Vec3(packet.getX(), packet.getY(), packet.getZ()), 3.0)) return;

        Vec3 guess = fitter.solve();
        if (guess == null) return;
        hotspot = guess;
        foundAt = now;
    }

    private static boolean holdingRadar() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return RADAR_ID.equals(ItemUtil.skyblockId(mc.player.getMainHandItem()));
    }

    public static void reset() {
        hotspot = null;
        foundAt = 0;
        fitter.reset();
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.hotspotRadar || hotspot == null) return;
        if (System.currentTimeMillis() - foundAt > KEEP_MS) {
            reset();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double distance = hotspot.distanceTo(mc.player.getEyePosition());
        // once you are on top of it the guess has served its purpose
        if (distance <= ARRIVED_DISTANCE) {
            reset();
            return;
        }

        int color = TeslaMapsConfig.parseColor(c.colorHotspot) | 0xFF000000;
        if (c.hotspotLine) {
            ESPRenderer.drawTracerFromCamera(matrices, hotspot, color, cameraPos, true);
        }
        if (c.hotspotBeam) {
            ESPRenderer.drawBeaconBeam(matrices, net.minecraft.core.BlockPos.containing(hotspot), color, cameraPos);
        }
        ESPRenderer.drawText(matrices, "§d§lHOTSPOT", hotspot.add(0, 1.7, 0), 1.7f, cameraPos);
        ESPRenderer.drawText(matrices, "§e" + (int) distance + "m", hotspot.add(0, 1.2, 0), 1.0f, cameraPos);
    }
}
