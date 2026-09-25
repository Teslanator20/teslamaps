package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;

// Hypixel sometimes drops you to the SkyBlock lobby when joining a dungeon ("A kick occurred in your
// connection..."). This starts a timer and tells the party "/pc kicked" so they know to wait.
public class KickedTimer {
    public static final String SAMPLE_TEXT = "Wait 45s before rejoining!";

    private static long kickedAtMs = 0L;
    private static long lastSendMs = 0L;
    private static boolean sawLobby = false;

    public static void onChatMessage(String message) {
        if (!com.teslamaps.utils.StringUtil.isServerMessage(message)) return;
        String clean = com.teslamaps.utils.StringUtil.strip(message);
        boolean joinProblem = clean.contains("There was a problem joining SkyBlock, try again in a moment");
        if (!joinProblem
                && !clean.contains("A kick occurred in your connection")
                && !clean.contains("An exception occurred in your connection")
                && !clean.contains("so you were put in the SkyBlock lobby")
                && !clean.contains("so you were put in the SkyBlock Lobby")
                && !clean.contains("You were kicked while joining that server")) return;

        long now = System.currentTimeMillis();
        if (now - kickedAtMs > 15_000L) { kickedAtMs = now; sawLobby = false; } // new kick episode -> (re)start timer

        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (!joinProblem && cfg.kickedTimer && cfg.kickedPartyMessage && mc.getConnection() != null && now - lastSendMs > 15_000L) {
            com.teslamaps.features.ChatQueue.command("pc kicked");
            lastSendMs = now;
        }
    }

    public static void tick() {
        if (kickedAtMs == 0) return;
        if (DungeonManager.isInDungeon()) { kickedAtMs = 0; sawLobby = false; return; } // rejoined -> clear

        // kick drops you into the lobby; once a real skyblock island is back, the wait is over
        boolean inSkyblock = SkyblockUtils.isInSkyblock() && !SkyblockUtils.getArea().isEmpty();
        if (!inSkyblock) sawLobby = true;
        else if (sawLobby) { kickedAtMs = 0; sawLobby = false; return; }

        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (System.currentTimeMillis() - kickedAtMs >= waitMs(cfg)) { // wait over -> ping and hide
            if (cfg.kickedTimer) {
                LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.6f);
                ScreenFlash.alert(cfg.screenFlashKicked);
                if (cfg.kickedTitle) Minecraft.getInstance().gui.setTitle(
                        net.minecraft.network.chat.Component.literal("§eTry rejoining SkyBlock now!"));
            }
            kickedAtMs = 0;
        }
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        if (!TeslaMapsConfig.get().kickedTimer || kickedAtMs == 0) return;
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        long elapsed = (System.currentTimeMillis() - kickedAtMs) / 1000L;
        long wait = waitMs(cfg) / 1000L;
        if (elapsed >= wait) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // rejoining too early after a kick tends to kick you again
        if (cfg.kickedCenterWarning) {
            draw(context, mc, cfg.kickedWarnX, cfg.kickedWarnY, cfg.kickedWarnScale,
                    "§c§lWait §f§l" + (wait - elapsed) + "s §c§lbefore rejoining!");
        }
    }

    /** Draw at a position/scale. Used by the live HUD and the HUD editor preview. */
    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        draw(context, mc, x, y, scale, "§c§lWait §f§l45s §c§lbefore rejoining!");
    }

    private static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, String text) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        context.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        pose.popMatrix();
    }

    private static long waitMs(TeslaMapsConfig cfg) {
        return Math.max(5, cfg.kickedWaitSeconds) * 1000L;
    }

    public static void reset() { kickedAtMs = 0; sawLobby = false; }
}
