// Ported from ADM's p3sim/run/AdsCommands.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.Sim;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/** What a command sees on the sim server's thread, as the player who typed it. */
public class AdsServerContext {
    public final ServerPlayer player;
    public final List<String> args;

    public AdsServerContext(ServerPlayer player, List<String> args) {
        this.player = player;
        this.args = args;
    }

    public String arg(int i) { return i >= 0 && i < args.size() ? args.get(i) : null; }

    /** The current run, or null. */
    public Run run() { return Runs.current; }

    public void reply(String text) { Sim.note(text); }
}
