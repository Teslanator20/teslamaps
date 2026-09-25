package com.teslamaps.features;

// Ported from SkyHanni's PestSpawnTimer.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;

/** Time since the last pest spawn and the average spawn time over cycles of continuous farming. */
public final class PestSpawnStats {

    private static final long FARM_GAP_MS = 30_000L;
    private static final int KEEP = 20;

    private static final ArrayDeque<Long> intervals = new ArrayDeque<>();
    private static long lastSpawn;
    private static long longestGap;
    private static int ticks;
    private static String lastText = "-";
    private static String averageText = null;

    private PestSpawnStats() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PestSpawnStats").section("Garden").when(() -> cfg().pestSpawnStats || cfg().pestSpawnChat)
                .tick(PestSpawnStats::tick)
                .serverChat(PestSpawnStats::onChat);
    }

    private static void tick() {
        if (!SkyblockUtils.isInGarden()) {
            lastSpawn = 0;
            return;
        }
        long now = System.currentTimeMillis();
        long lastBreak = FarmSpeed.lastBreakMillis();
        if (lastSpawn > 0) longestGap = Math.max(longestGap, now - Math.max(lastBreak, lastSpawn));
        if (++ticks < 20) return;
        ticks = 0;
        lastText = lastSpawn == 0 ? "-" : format(now - lastSpawn) + " ago";
        if (intervals.isEmpty()) {
            averageText = null;
        } else {
            long sum = 0;
            for (long i : intervals) sum += i;
            averageText = format(sum / intervals.size());
        }
    }

    private static void onChat(String raw) {
        if (!SkyblockUtils.isInGarden()) return;
        PestSpawnAlerts.Spawn spawn = PestSpawnAlerts.parse(StringUtil.strip(raw));
        if (spawn == null || spawn.amount() < 0) return;
        long now = System.currentTimeMillis();
        if (lastSpawn > 0) {
            long took = now - lastSpawn;
            if (longestGap <= FARM_GAP_MS) {
                intervals.addLast(took);
                while (intervals.size() > KEEP) intervals.removeFirst();
            }
            Minecraft mc = Minecraft.getInstance();
            if (cfg().pestSpawnChat && mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §ePests spawned in §b" + format(took)));
            }
        }
        lastSpawn = now;
        longestGap = 0;
    }

    /** "4m 12s ago", or "-" before the first spawn this visit. */
    public static String lastText() {
        return lastText;
    }

    /** Average spawn time, or null without a clean cycle yet. */
    public static String averageText() {
        return averageText;
    }

    public static void reset() {
        intervals.clear();
        averageText = null;
    }

    private static String format(long ms) {
        long s = ms / 1000;
        return s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
    }
}
