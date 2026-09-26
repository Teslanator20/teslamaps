package com.teslamaps.features.kuudra;

// Ported from Shizo's kuudra/general/LagTracker.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.Announce;
import com.teslamaps.utils.ServerTickTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Server ticks vs wall time from Elle's start line to KUUDRA DOWN / DEFEAT = seconds lost to lag. */
public final class KuudraLagTracker {

    private KuudraLagTracker() {}

    public static void register() {
        Kuudra.LISTENERS.add((prev, now) -> {
            if (now == Kuudra.Phase.COMPLETED) onEnd();
        });
    }

    private static void onEnd() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.kuudraLagTracker || !c.section("Kuudra")) return;
        long start = Kuudra.runStartMs();
        if (start == 0 || ServerTickTimer.lastPingMs() == 0) return;
        long ticks = ServerTickTimer.pings() - Kuudra.startPings();
        if (ticks <= 0) return;
        long realMs = Kuudra.startMs(Kuudra.Phase.COMPLETED) - start;
        String lost = String.format(Locale.ROOT, "%.2f", Math.max(0, realMs - ticks * 50) / 1000.0);
        var p = Minecraft.getInstance().player;
        boolean splitsShowIt = c.kuudraSplits && c.kuudraSplitsLag;
        if (p != null && !splitsShowIt) p.sendSystemMessage(Component.literal("§a[TeslaMaps] §c" + lost + "s §flost to lag §7(run "
                + String.format(Locale.ROOT, "%.1f", realMs / 1000.0) + "s)"));
        if (c.kuudraLagTrackerParty) Announce.party("Time lost to lag: " + lost + "s");
    }
}
