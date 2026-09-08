package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A fishing hotspot is a standing whirlpool of particles. It stops emitting the moment the
 * hotspot moves on, so the particle stream is what tells you it is gone.
 */
public class HotspotWatcher {

    private static final Set<ParticleType<?>> WHIRLPOOL = Set.of(
            ParticleTypes.DOLPHIN.getType(),
            ParticleTypes.NAUTILUS.getType(),
            ParticleTypes.BUBBLE.getType(),
            ParticleTypes.BUBBLE_COLUMN_UP.getType(),
            ParticleTypes.GLOW.getType(),
            ParticleTypes.END_ROD.getType());

    private static final double RADIUS_SQ = 36;      // 6 blocks around the whirlpool centre
    private static final double LEAVE_DISTANCE = 40; // walking off is not the hotspot vanishing
    private static final int CONFIRM_COUNT = 40;
    private static final long CONFIRM_MS = 2000;
    private static final long GONE_MS = 3000;

    private static volatile Vec3 center = null;
    private static volatile boolean confirmed = false;
    private static volatile int count = 0;
    private static volatile long windowStart = 0;
    private static volatile long lastSeen = 0;

    private static final Map<String, Integer> census = new ConcurrentHashMap<>();
    private static volatile long censusUntil = 0;

    /** Netty thread. */
    public static void onParticlePacket(ClientboundLevelParticlesPacket packet) {
        boolean counting = censusUntil > 0;
        if (!counting && !TeslaMapsConfig.get().hotspotGoneWarning) return;

        long now = System.currentTimeMillis();
        Vec3 at = new Vec3(packet.getX(), packet.getY(), packet.getZ());

        if (counting) runCensus(packet, at, now);
        if (!TeslaMapsConfig.get().hotspotGoneWarning) return;
        if (!WHIRLPOOL.contains(packet.getParticle().getType())) return;

        Vec3 c = center;
        if (c != null && at.distanceToSqr(c) <= RADIUS_SQ) {
            count++;
            lastSeen = now;
            if (!confirmed && count >= CONFIRM_COUNT && now - windowStart <= CONFIRM_MS) confirmed = true;
            return;
        }
        if (confirmed) return;
        if (c == null || now - windowStart > CONFIRM_MS) {
            center = at;
            count = 1;
            windowStart = now;
            lastSeen = now;
        }
    }

    public static void tick() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.hotspotGoneWarning) {
            reset();
            return;
        }
        Vec3 c = center;
        if (!confirmed || c == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.player.position().distanceTo(c) > LEAVE_DISTANCE) {
            reset();
            return;
        }
        if (System.currentTimeMillis() - lastSeen < GONE_MS) return;

        alert(cfg, c);
        reset();
    }

    public static void reset() {
        center = null;
        confirmed = false;
        count = 0;
        windowStart = 0;
        lastSeen = 0;
    }

    /** Records which particle types show up around the player, to pin down the whirlpool's type. */
    public static void startCensus(int seconds) {
        census.clear();
        censusUntil = System.currentTimeMillis() + seconds * 1000L;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(
                    "§b[TeslaMaps] §fCounting particles within 15 blocks for §e" + seconds + "s§f. Stand in a hotspot."));
        }
    }

    private static void runCensus(ClientboundLevelParticlesPacket packet, Vec3 at, long now) {
        if (now >= censusUntil) {
            censusUntil = 0;
            printCensus();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.position().distanceToSqr(at) > 225) return;
        String name = String.valueOf(BuiltInRegistries.PARTICLE_TYPE.getKey(packet.getParticle().getType()));
        census.merge(name, 1, Integer::sum);
    }

    private static void printCensus() {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(census.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) return;
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fParticle census:"));
            for (int i = 0; i < Math.min(12, sorted.size()); i++) {
                mc.player.sendSystemMessage(Component.literal(
                        "§7 " + sorted.get(i).getKey() + " §f" + sorted.get(i).getValue()));
            }
            if (sorted.isEmpty()) mc.player.sendSystemMessage(Component.literal("§7 nothing nearby"));
        });
    }

    private static void alert(TeslaMapsConfig cfg, Vec3 c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §cHotspot gone §7at §f"
                + (int) c.x + " " + (int) c.y + " " + (int) c.z));
        if (cfg.hotspotGoneTitle) {
            mc.gui.setTimes(0, 40, 5);
            mc.gui.setTitle(Component.literal("§c§lHOTSPOT GONE"));
        }
        if (cfg.hotspotGoneVolume > 0f) {
            com.teslamaps.utils.LoudSound.play(
                    com.teslamaps.utils.SoundOptions.resolve(cfg.hotspotGoneSound),
                    cfg.hotspotGoneVolume, 1.0f);
        }
    }
}
