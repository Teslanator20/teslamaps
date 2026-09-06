package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Times a pest round: from the teleport onto the plot until the warp back.
 * <p>
 * Driven by the commands actually sent, so it measures the same whether the
 * routine key or the chat was used.
 */
public class PestTimer {

    private static long startedAt = 0L;

    /** Hooked to every command the client sends, ours and typed alike. */
    public static void onCommandSent(String command) {
        if (!TeslaMapsConfig.get().pestTimer || !SkyblockUtils.isInGarden()) return;

        String lower = command.toLowerCase().trim();
        if (lower.startsWith("/")) lower = lower.substring(1);

        if (lower.startsWith("plottp") || lower.startsWith("tptoplot")) {
            startedAt = System.currentTimeMillis();
            return;
        }

        if (lower.startsWith("warp garden") && startedAt != 0L) {
            finish(System.currentTimeMillis() - startedAt);
            startedAt = 0L;
        }
    }

    private static void finish(long millis) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        TeslaMapsConfig config = TeslaMapsConfig.get();
        double seconds = millis / 1000.0;

        boolean best = config.pestPersonalBest <= 0 || seconds < config.pestPersonalBest;
        if (best) {
            config.pestPersonalBest = (float) seconds;
            TeslaMapsConfig.save();
        }

        String time = String.format("%.1fs", seconds);
        String message = best
                ? "§b[TeslaMaps] §fPest round §a" + time + " §6§lNEW PB"
                : "§b[TeslaMaps] §fPest round §e" + time
                        + " §7(PB " + String.format("%.1fs", config.pestPersonalBest) + ")";
        mc.player.sendSystemMessage(Component.literal(message));
    }

    /** Seconds of the round in progress, or -1 when none is running. */
    public static double runningSeconds() {
        if (startedAt == 0L) return -1;
        return (System.currentTimeMillis() - startedAt) / 1000.0;
    }

    public static void resetPersonalBest() {
        TeslaMapsConfig.get().pestPersonalBest = 0;
        TeslaMapsConfig.save();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fPest PB cleared"));
        }
    }
}
