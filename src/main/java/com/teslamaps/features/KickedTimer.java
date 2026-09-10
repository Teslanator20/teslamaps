package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.utils.LoudSound;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;

// Hypixel sometimes drops you to the SkyBlock lobby when joining a dungeon ("A kick occurred in your
// connection..."). This starts a timer and tells the party "/pc kicked" so they know to wait.
public class KickedTimer {
    public static final String SAMPLE_TEXT = "Kicked: 12s";

    private static long kickedAtMs = 0L;
    private static long lastSendMs = 0L;

    public static void onChatMessage(String message) {
        String clean = message.replaceAll("(?i)§[0-9A-FK-OR]", "");
        if (!clean.contains("A kick occurred in your connection")
                && !clean.contains("so you were put in the SkyBlock lobby")
                && !clean.contains("You were kicked while joining that server")) return;

        long now = System.currentTimeMillis();
        if (now - kickedAtMs > 15_000L) kickedAtMs = now; // new kick episode -> (re)start timer

        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (cfg.kickedTimer && cfg.kickedPartyMessage && mc.getConnection() != null && now - lastSendMs > 15_000L) {
            mc.getConnection().sendCommand("pc kicked");
            lastSendMs = now;
        }
    }

    public static void tick() {
        if (kickedAtMs == 0) return;
        if (DungeonManager.isInDungeon()) { kickedAtMs = 0; return; } // rejoined -> clear
        if (System.currentTimeMillis() - kickedAtMs >= 60_000L) { // wait over -> ping and hide
            if (TeslaMapsConfig.get().kickedTimer) LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.6f);
            kickedAtMs = 0;
        }
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        if (!TeslaMapsConfig.get().kickedTimer || kickedAtMs == 0) return;
        long elapsed = (System.currentTimeMillis() - kickedAtMs) / 1000L;
        if (elapsed >= 60) { kickedAtMs = 0; return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        draw(context, mc, TeslaMapsConfig.get().kickedX, TeslaMapsConfig.get().kickedY,
                TeslaMapsConfig.get().kickedScale, "§c[Kicked] §f" + elapsed + "s");

        // rejoining too early after a kick tends to kick you again - big center warning for 60s
        if (TeslaMapsConfig.get().kickedCenterWarning && elapsed < 60) {
            int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
            var pose = context.pose();
            pose.pushMatrix();
            pose.translate(sw / 2f, sh * 0.38f);
            pose.scale(2f, 2f);
            String warn = "§c§lWait §f§l" + (60 - elapsed) + "s §c§lbefore rejoining!";
            context.text(mc.font, warn, -mc.font.width(warn) / 2, 0, 0xFFFFFFFF, true);
            pose.popMatrix();
        }
    }

    /** Draw at a position/scale. Used by the live HUD and the HUD editor preview. */
    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        draw(context, mc, x, y, scale, "§c[Kicked] §f12s");
    }

    private static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, String text) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        context.text(mc.font, text, 0, 0, 0xFFFFFFFF);
        pose.popMatrix();
    }

    public static void reset() { kickedAtMs = 0; }
}
