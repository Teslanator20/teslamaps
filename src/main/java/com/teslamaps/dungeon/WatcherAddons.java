/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * Watcher analytics mirror legitcatmod's WatcherAddons (grade by time-to-dialogue with <22s/<25s
 * thresholds + wolf/cat/wither sound + title; watcher-move timing; blood-camp breakdown; live HUD).
 * Reimplemented; watcher entity reused from BloodCamp.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.dungeon;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.LoudSound;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

// Grades the Watcher (blood camp) on time-to-dialogue (sound + title), times its first move, and prints a
// breakdown when it's defeated. Optional live HUD shows Dialogue / Move / Camp while the fight is active.
public class WatcherAddons {

    private static final String DIALOGUE = "[BOSS] The Watcher: Let's see how you can handle this.";
    private static final String PASS = "[BOSS] The Watcher: You have proven yourself. You may pass.";

    private static long startMs = 0L;     // first "[BOSS] The Watcher:" line of the fight
    private static long dialogueMs = 0L;  // time taken to reach the dialogue line
    private static long moveMs = 0L;      // time taken until the Watcher first moves
    private static boolean graded = false;
    private static Vec3 dialoguePos = null; // watcher position captured at dialogue (to detect its move)

    public static void onChatMessage(String raw) {
        if (!TeslaMapsConfig.get().watcherSpeedGrade && !TeslaMapsConfig.get().watcherHud) return;
        if (!DungeonManager.isInDungeon()) return;
        String msg = raw.replaceAll("(?i)§[0-9A-FK-OR]", "");

        if (msg.equals("Starting in 1 second.")) { reset(); return; }

        if (startMs == 0L && msg.startsWith("[BOSS] The Watcher:")) {
            startMs = System.currentTimeMillis();
        } else if (msg.equals(DIALOGUE)) {
            if (startMs == 0L || graded) return;
            dialogueMs = System.currentTimeMillis() - startMs;
            graded = true;
            if (TeslaMapsConfig.get().watcherSpeedGrade) grade(dialogueMs);
        } else if (msg.equals(PASS)) {
            if (startMs == 0L) return;
            if (TeslaMapsConfig.get().watcherSpeedGrade) breakdown(System.currentTimeMillis() - startMs, dialogueMs, moveMs);
            reset();
        }
    }

    // tracks the Watcher's first movement after the dialogue phase
    public static void tick() {
        if (startMs == 0L || !graded || moveMs != 0L) return;
        Zombie w = BloodCamp.getWatcherEntity();
        if (w == null) return;
        if (dialoguePos == null) {
            dialoguePos = w.position();
        } else if (w.position().distanceTo(dialoguePos) > 1.0) {
            moveMs = System.currentTimeMillis() - startMs;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && TeslaMapsConfig.get().watcherSpeedGrade) {
                mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] Watcher moved at §d§l" + fmt(moveMs) + "§b!"));
            }
        }
    }

    private static void grade(long ms) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double s = ms / 1000.0;

        String title;
        net.minecraft.sounds.SoundEvent sound;
        if (s < 22.0) {
            title = "§4§lFAST WATCHER";
            sound = SoundEvents.WOLF_GROWL_BABY.value();
        } else if (s < 25.0) {
            title = "§cWatcher Ready";
            sound = SoundEvents.CAT_PURR_BABY.value();
        } else {
            title = "§8Slow Watcher zz";
            sound = SoundEvents.WITHER_AMBIENT;
        }

        mc.player.sendSystemMessage(Component.literal(
                "§b[TeslaMaps] Watcher took §d§l" + fmt(ms) + "§r§b to reach dialogue!"));
        mc.gui.setTimes(0, 40, 5);
        mc.gui.setTitle(Component.literal(title));
        mc.gui.setSubtitle(Component.literal(String.format("§bTook §d%.1fs§b!", s)));
        LoudSound.play(sound, TeslaMapsConfig.get().watcherGradeVolume, 1.0f);
    }

    private static void breakdown(long fullMs, long dlgMs, long mvMs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(
                "§b[TeslaMaps] Blood camp breakdown - §rFull: §d§l" + fmt(fullMs)
                        + " §rDialogue: §d§l" + (dlgMs > 0 ? fmt(dlgMs) : "?")
                        + " §rMove: §d§l" + (mvMs > 0 ? fmt(mvMs) : "?") + "§b!"));
    }

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.watcherHud || startMs == 0L) return;
        if (!"Mage".equals(com.teslamaps.player.PlayerTracker.getLocalClass())) return; // mage-only

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        long now = System.currentTimeMillis();
        String dlg = dialogueMs > 0 ? fmt(dialogueMs) : fmt(now - startMs);
        String mv = moveMs > 0 ? fmt(moveMs) : "-";
        String camp = fmt(now - startMs);

        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(c.watcherHudX, c.watcherHudY);
        pose.scale(c.watcherHudScale, c.watcherHudScale);
        int y = 0;
        ctx.text(mc.font, "§c§lWatcher Stats:", 0, y, 0xFFFFFFFF); y += 10;
        ctx.text(mc.font, "§dDialogue§r: " + dlg, 0, y, 0xFFFFFFFF); y += 9;
        ctx.text(mc.font, "§dMove§r: " + mv, 0, y, 0xFFFFFFFF); y += 9;
        ctx.text(mc.font, "§dCamp§r: " + camp, 0, y, 0xFFFFFFFF);
        pose.popMatrix();
    }

    public static void reset() {
        startMs = 0L;
        dialogueMs = 0L;
        moveMs = 0L;
        graded = false;
        dialoguePos = null;
    }

    private static String fmt(long ms) {
        return String.format("%.1fs", ms / 1000.0);
    }
}
