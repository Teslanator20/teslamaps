package com.teslamaps.dungeon;

// Zen's ServerLagTimer: client vs server ticks from Mort's "Good luck." to the defeat line
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.regex.Pattern;

public class ServerLagTimer {

    private static final Pattern DEFEATED = Pattern.compile("^\\s*☠ Defeated .+ in 0?[\\dhms ]+?\\s*(?:\\(NEW RECORD!\\))?$");

    private static boolean ticking = false;
    private static long clientTicks = 0;
    private static long pingStart = 0;
    private static Object lastLevel = null;
    private static long pendingLag = -1;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ServerLagTimer").section("Score & Splits").when(() -> cfg().serverLagTimer)
                .tick(ServerLagTimer::tick)
                .serverChat(ServerLagTimer::onChat)
                .config("Score & Splits", b -> b.toggle("  Server Lag Line", () -> cfg().serverLagTimer,
                        v -> cfg().serverLagTimer = v)
                        .desc("At the end of a run: how long the server lagged (client vs server ticks since Mort), inside the compact summary when that is on"));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            ticking = false;
            pendingLag = -1;
        }
        if (ticking) clientTicks++;
    }

    private static void onChat(String raw) {
        String msg = StringUtil.stripTrim(raw);
        if (msg.equals("[NPC] Mort: Good luck.")) {
            ticking = true;
            clientTicks = 0;
            pingStart = ServerTickTimer.pings();
            return;
        }
        if (!ticking || msg.indexOf('☠') < 0 || !msg.contains("Defeated")) return;
        if (!DEFEATED.matcher(msg).matches()) return;
        ticking = false;
        long serverTicks = ServerTickTimer.pings() - pingStart;
        if (serverTicks <= 0) return;
        long lag = Math.max(0, clientTicks - serverTicks);
        if (cfg().extraStatsCompact && DungeonManager.isInDungeon()) {
            pendingLag = lag;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + text(lag)));
    }

    private static String text(long lag) {
        return String.format(Locale.ROOT, "§7Server lagged for §c%.1fs §8| §c%d ticks", lag / 20.0, lag);
    }

    /** Line for the compact run summary, once. */
    static String takeSummaryLine() {
        long lag = pendingLag;
        pendingLag = -1;
        if (lag < 0 || !cfg().serverLagTimer || !cfg().section("Score & Splits")) return null;
        return text(lag);
    }
}
