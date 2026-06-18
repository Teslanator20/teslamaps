package com.teslamaps.dungeon;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.regex.Pattern;

/**
 * Blood Camp helper (ported from Odin) — Stage 1: Watcher move prediction.
 *
 * Chat/tick based: between the two Watcher lines it measures the spawn-wave timing and predicts
 * when the Watcher will move, showing a countdown HUD, an optional chat/party message and a
 * "Kill Mobs" title when the move happens. (Stage 2 — the packet-based spawn boxes — and Stage 3 —
 * the Watcher HP bar — are separate.)
 */
public class BloodCamp {

    private static final Pattern MOVE_START = Pattern.compile("^\\[BOSS] The Watcher: Things feel a little more roomy now, eh\\?$");
    private static final Pattern MOVE_PREDICT = Pattern.compile("^\\[BOSS] The Watcher: Let's see how you can handle this\\.$");

    private static long tickCounter = 0L;
    private static boolean hasStart = false;
    private static long startTimeMs = 0L;
    private static long startTick = 0L;
    private static boolean hasFinal = false;
    private static long finalTick = 0L;

    public static void tick() {
        if (!TeslaMapsConfig.get().bloodCampMoveTimer) {
            hasFinal = false;
            return;
        }
        tickCounter++;
        if (hasFinal && tickCounter >= finalTick) {
            hasFinal = false;
            if (TeslaMapsConfig.get().bloodCampKillTitle) {
                Minecraft mc = Minecraft.getInstance();
                mc.gui.setTimes(0, 20, 5);
                mc.gui.setTitle(Component.literal("§cKill Mobs"));
            }
        }
    }

    public static void onChatMessage(String message) {
        if (!TeslaMapsConfig.get().bloodCampMoveTimer) return;
        TeslaMapsConfig config = TeslaMapsConfig.get();

        if (MOVE_START.matcher(message).matches()) {
            startTimeMs = System.currentTimeMillis();
            startTick = tickCounter;
            hasStart = true;
            return;
        }

        if (!MOVE_PREDICT.matcher(message).matches() || !hasStart) return;
        hasStart = false;

        float moveTicks = (tickCounter - startTick) * 0.05f + 0.1f;
        int base;
        if (moveTicks >= 31 && moveTicks < 34) base = 36;
        else if (moveTicks >= 28 && moveTicks < 31) base = 33;
        else if (moveTicks >= 25 && moveTicks < 28) base = 30;
        else if (moveTicks >= 22 && moveTicks < 25) base = 27;
        else if (moveTicks >= 1 && moveTicks < 22) base = 24;
        else return;

        float predictionTicks = base + ((float) Math.ceil((System.currentTimeMillis() - startTimeMs) / 1000f) - moveTicks) / 2f - 0.6f;
        if (predictionTicks < 20 || predictionTicks > 40) return;

        String secs = String.format("%.2f", predictionTicks * 0.05f);
        Minecraft mc = Minecraft.getInstance();
        if (config.bloodCampPartyMessage && mc.getConnection() != null) {
            mc.getConnection().sendCommand("pc Watcher will move in " + secs + "s.");
        }
        if (config.bloodCampMoveMessage && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fWatcher will move in §e" + secs + "s§f."));
        }

        int moveTimeTicks = (int) ((predictionTicks - moveTicks) * 20 - 3);
        finalTick = tickCounter + moveTimeTicks;
        hasFinal = moveTimeTicks > 0;
    }

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.bloodCampMoveTimer || !hasFinal) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float remaining = Math.max(0, (finalTick - tickCounter)) * 0.05f;
        String text = String.format("§cMove Timer: §f%.2fs", remaining);

        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(config.bloodCampX, config.bloodCampY);
        pose.scale(config.bloodCampScale, config.bloodCampScale);
        context.text(mc.font, text, 0, 0, 0xFFFFFFFF);
        pose.popMatrix();
    }

    public static void reset() {
        hasStart = false;
        hasFinal = false;
        tickCounter = 0L;
    }
}
