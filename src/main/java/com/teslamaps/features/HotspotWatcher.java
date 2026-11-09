package com.teslamaps.features;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HotspotWatcher {

    private static final Map<String, Integer> census = new ConcurrentHashMap<>();
    private static volatile long censusUntil = 0;

    /** Netty thread. */
    public static void onParticlePacket(ClientboundLevelParticlesPacket packet) {
        if (censusUntil <= 0) return;
        long now = System.currentTimeMillis();
        if (now >= censusUntil) {
            censusUntil = 0;
            printCensus();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(packet.getX(), packet.getY(), packet.getZ()) > 225) return;
        String name = String.valueOf(BuiltInRegistries.PARTICLE_TYPE.getKey(packet.getParticle().getType()));
        census.merge(name, 1, Integer::sum);
    }

    /** Records which particle types show up around the player. */
    public static void startCensus(int seconds) {
        census.clear();
        censusUntil = System.currentTimeMillis() + seconds * 1000L;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(
                    "§b[TeslaMaps] §fCounting particles within 15 blocks for §e" + seconds + "s§f. Stand in a hotspot."));
        }
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
}
