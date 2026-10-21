// Ported from ADM's p3sim/run/api/ChannelApi.kt
package com.teslamaps.adm.sim.run.api;

import com.teslamaps.adm.sim.Sim;
import net.minecraft.server.level.ServerPlayer;

/** What the channels do before the HUD module is filled in. Server thread only. */
public final class ChannelDefaults {
    private ChannelDefaults() {}

    public static void chat(String text, Audience to) { forAudience(to, () -> Sim.chat(text)); }

    public static void title(String title, String subtitle, int fadeIn, int stay, int fadeOut, Audience to) {
        forAudience(to, () -> Sim.title(title, subtitle, fadeIn, stay, fadeOut));
    }

    private static void forAudience(Audience to, Runnable block) {
        if (to instanceof Audience.Everyone) {
            Sim.everyone(block);
        } else if (to instanceof Audience.Only o) {
            for (ServerPlayer p : Sim.players()) {
                if (p.getUUID().equals(o.player)) { Sim.asPlayer(p, false, block); break; }
            }
        } else if (to instanceof Audience.AllBut a) {
            for (ServerPlayer p : Sim.players()) if (!p.getUUID().equals(a.player)) Sim.asPlayer(p, false, block);
        }
    }
}
