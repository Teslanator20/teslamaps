package com.teslamaps.dungeon;

// Ported from Devonian's DragonHitCount.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.player.PlayerTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ServerTickTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class DragonHitCount {

    private DragonHitCount() {}

    private static final int WINDOW = 90;

    private static final Set<Integer> seen = new HashSet<>();
    private static final int[] hits = new int[WINDOW];
    private static WitherDragons.Dragon type = null;
    private static int currId = 0;
    private static long startTick = 0;
    private static Object lastLevel = null;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("DragonHitCount").when(() -> cfg().dragonHitCounter).tick(DragonHitCount::tick);
    }

    /** handleSoundEvent HEAD, runs on netty and main thread. */
    public static void onSound(SoundEvent sound, SoundSource source, float volume) {
        if (currId == 0 || sound != SoundEvents.ARROW_HIT_PLAYER) return;
        if (source != SoundSource.NEUTRAL || volume != 1f) return;
        if (!Minecraft.getInstance().isSameThread()) return;
        long i = ServerTickTimer.now() - startTick;
        if (i >= 0 && i < WINDOW) hits[(int) i]++;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            clear();
            seen.clear();
        }
        DungeonFloor f = DungeonManager.getCurrentFloor();
        if (f == null || f.getLevel() != 7 || !f.isMaster() || F7Devices.phase() != 5) {
            if (currId != 0) clear();
            return;
        }

        if (currId != 0) {
            Entity e = mc.level.getEntity(currId);
            if (!(e instanceof EnderDragon d) || d.dragonDeathTime > 0 || ServerTickTimer.now() - startTick >= WINDOW) end();
        }

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof EnderDragon) || !seen.add(e.getId())) continue;
            WitherDragons.Dragon t = nearest(e);
            WitherDragons.Dragon prio = WitherDragons.priorityDragon;
            if (prio != null && prio != t) continue;
            if (currId != 0) end();
            type = t;
            currId = e.getId();
            startTick = ServerTickTimer.now();
            java.util.Arrays.fill(hits, 0);
        }
    }

    private static WitherDragons.Dragon nearest(Entity e) {
        WitherDragons.Dragon best = null;
        double bd = Double.MAX_VALUE;
        for (WitherDragons.Dragon d : WitherDragons.Dragon.values()) {
            double dist = Math.abs(d.spawnPos.getX() - e.getX()) + Math.abs(d.spawnPos.getY() - e.getY())
                    + Math.abs(d.spawnPos.getZ() - e.getZ());
            if (dist < bd) { bd = dist; best = d; }
        }
        return best;
    }

    private static void end() {
        String cls = PlayerTracker.getLocalClass();
        boolean db = "Healer".equals(cls) || "Tank".equals(cls) || "Mage".equals(cls);
        int endI = 0, sum = 0, stack = db ? -1 : 0;
        for (int i = 0; i < WINDOW; i++) {
            int v = hits[i];
            if (v > 0) endI = i;
            sum += v;
            if (db) {
                if (sum >= 5 && stack == -1) stack = i;
            } else if (i < 20) stack += v;
        }
        if (db && stack == -1) stack = endI;
        int partArrows = db ? Math.min(sum, 5) : stack;
        int partTicks = db ? stack : Math.min(20, endI);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && type != null) {
            mc.player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                    "§%c%s§7: §b%d §aarrows in §d%.2fs (%d ticks) §7| §b%d §aarrows in §d%.2fs (%d ticks)",
                    type.colorCode, type.dispName, sum, endI * 0.05, endI, partArrows, partTicks * 0.05, partTicks)));
        }
        clear();
    }

    private static void clear() {
        type = null;
        currId = 0;
        java.util.Arrays.fill(hits, 0);
    }
}
