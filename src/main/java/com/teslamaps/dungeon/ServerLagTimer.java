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

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ServerLagTimer").section("Score & Splits").when(() -> cfg().serverLagTimer)
                .tick(ServerLagTimer::tick)
                .serverChat(ServerLagTimer::onChat)
                .config("Score & Splits", b -> b.toggle("  Server Lag Line", () -> cfg().serverLagTimer,
                        v -> cfg().serverLagTimer = v)
                        .desc("At the end of a run: how long the server lagged (client vs server ticks since Mort)"));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            ticking = false;
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
                "§b[TeslaMaps] §7Server lagged for §c%.1fs §8| §c%d ticks", lag / 20.0, lag)));
    }
}
