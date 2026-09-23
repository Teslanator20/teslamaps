package com.teslamaps.features.foraging;

// Ported from Skysoft's QueenAntWarning.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class QueenAntWarning {

    private static final String ANTHILL_DESTROYED = "You destroyed an anthill, angering the ants inside!";
    private static final double SEARCH_DIST_SQ = 10.0 * 10.0;
    private static final long SEARCH_WINDOW_MS = 3000;
    private static final int SOUND_PLAYS = 3;
    private static final long SOUND_INTERVAL_MS = 450;

    private static long searchUntil = 0;
    private static Vec3 searchPos = null;
    private static final Set<UUID> knownQueens = new HashSet<>();

    private static UUID activeQueen = null;
    private static Vec3 queenPos = null;

    private static int soundsLeft = 0;
    private static long nextSoundAt = 0;

    public static void onChatMessage(String text) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.queenAntWarning || !Foraging.inTorrhus()) return;
        if (!StringUtil.strip(text).trim().equals(ANTHILL_DESTROYED)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || activeQueen != null) return;

        searchPos = mc.player.position();
        knownQueens.clear();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (isQueenTag(e)) knownQueens.add(e.getUUID());
        }
        searchUntil = System.currentTimeMillis() + SEARCH_WINDOW_MS;
    }

    public static void tick() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (!c.queenAntWarning || mc.level == null || mc.player == null) { clear(); return; }

        long now = System.currentTimeMillis();

        if (soundsLeft > 0 && now >= nextSoundAt) {
            LoudSound.play(SoundOptions.resolve(c.queenAntSound), c.queenAntVolume, 1.0f);
            soundsLeft--;
            nextSoundAt = now + SOUND_INTERVAL_MS;
        }

        if (activeQueen != null) {
            Vec3 pos = null;
            for (Entity e : com.teslamaps.utils.EntityCache.all()) {
                if (e.getUUID().equals(activeQueen) && e.isAlive()) { pos = e.position(); break; }
            }
            if (pos == null) { activeQueen = null; queenPos = null; }
            else queenPos = pos;
            return;
        }

        if (searchUntil == 0 || now > searchUntil) { searchUntil = 0; return; }

        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : com.teslamaps.utils.EntityCache.all()) {
            if (!isQueenTag(e) || knownQueens.contains(e.getUUID())) continue;
            double d = e.position().distanceToSqr(searchPos);
            if (d > SEARCH_DIST_SQ || d >= bestDist) continue;
            best = e;
            bestDist = d;
        }
        if (best == null) return;

        activeQueen = best.getUUID();
        queenPos = best.position();
        searchUntil = 0;

        mc.gui.setTimes(0, 50, 5);
        mc.gui.setTitle(Component.literal("§c§lQueen Ant Found!"));
        if (c.queenAntVolume > 0) {
            LoudSound.play(SoundOptions.resolve(c.queenAntSound), c.queenAntVolume, 1.0f);
            soundsLeft = SOUND_PLAYS - 1;
            nextSoundAt = now + SOUND_INTERVAL_MS;
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().queenAntLine) return;
        if (activeQueen == null || queenPos == null) return;
        ESPRenderer.drawTracerFromCamera(matrices, queenPos.add(0, 1, 0), 0xE6FF5555, cameraPos);
    }

    private static boolean isQueenTag(Entity e) {
        if (!(e instanceof ArmorStand) || !e.isAlive() || !e.hasCustomName()) return false;
        String name = StringUtil.strip(e.getCustomName().getString());
        return name.contains("Queen Ant");
    }

    private static void clear() {
        searchUntil = 0;
        activeQueen = null;
        queenPos = null;
        soundsLeft = 0;
    }
}
