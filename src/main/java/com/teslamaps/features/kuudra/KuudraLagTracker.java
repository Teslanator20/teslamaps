package com.teslamaps.features.kuudra;

// Ported from Shizo's kuudra/general/LagTracker.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.Announce;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ServerTickTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Server ticks vs wall time from Elle's start line to KUUDRA DOWN / DEFEAT = seconds lost to lag. */
public final class KuudraLagTracker {

    private static long startMs, startTicks = -1;
    private static boolean pingMode;
    private static Object lastLevel;

    private KuudraLagTracker() {}

    public static void register() {
        Feature.of("KuudraLagTracker").section("Kuudra").when(() -> TeslaMapsConfig.get().kuudraLagTracker)
                .serverChat(KuudraLagTracker::onChat)
                .tick(KuudraLagTracker::tick);
    }

    private static void tick() {
        Object lvl = Minecraft.getInstance().level;
        if (lvl != lastLevel) {
            lastLevel = lvl;
            startTicks = -1;
        }
    }

    private static long ticks() {
        return pingMode ? ServerTickTimer.pings() : ServerTickTimer.now();
    }

    private static void onChat(String raw) {
        String s = KuudraRunInfo.strip(raw);
        if (s.equals(KuudraRunInfo.START)) {
            startMs = System.currentTimeMillis();
            pingMode = ServerTickTimer.serverDriven();
            startTicks = ticks();
            return;
        }
        if (startTicks < 0 || !(s.equals("KUUDRA DOWN!") || s.equals("DEFEAT"))) return;
        double real = (System.currentTimeMillis() - startMs) / 1000.0;
        double server = (ticks() - startTicks) / 20.0;
        startTicks = -1;
        String lost = String.format(Locale.ROOT, "%.2f", Math.max(0, real - server));
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§a[TeslaMaps] §c" + lost + "s §flost to lag §7(run "
                + String.format(Locale.ROOT, "%.1f", real) + "s)"));
        if (TeslaMapsConfig.get().kuudraLagTrackerParty) Announce.party("Time lost to lag: " + lost + "s");
    }
}
