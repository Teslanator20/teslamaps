// Ported from ADM's p3sim/run/AdsBootstrap.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimNet;
import com.teslamaps.adm.sim.SimServer;
import com.teslamaps.adm.sim.run.plugin.AdsPlugins;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRules;

/** One-time wiring of run/ and the modules. Each step guarded: one failing never stops the others. */
public final class AdsBootstrap {
    private AdsBootstrap() {}

    private static boolean done = false;

    /** Plug-ins, the stage line, the command receiver, every module's onInit. Once. */
    public static void init() {
        if (done) return;
        done = true;
        Ec.safely("ads plug-ins", AdsPlugins::load);
        RunWire.sender = (kind, data) -> SimNet.sendAll(kind, data);
        Ec.safely("ads client run", ClientRun::register);
        // leaving the world forgets the run's stage
        Ec.safely("ads client run reset", () -> ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientRun.reset()));
        Runs.onStopped = r -> {
            LagRuntime.runPreset = LagPreset.OFF;
            ViewPin.release();
            // the boss practice that may follow has the frozen noon it always had
            MinecraftServer s = SimServer.server;
            if (s != null) {
                Ec.safely("ads run stop clock", () -> {
                    s.getGameRules().set(GameRules.ADVANCE_TIME, false, s);
                    Sim.command("time set noon");
                });
            }
            // the clear's entities go with the run; not when the server is gone
            if (SimServer.server != null) Ec.safely("ads run stop entities", Sim::clearEntities);
        };
        Ec.safely("ads input", RunInput::install);
        Ec.safely("ads commands", AdsCommands::onInit);
        for (AdsModule m : AdsModules.all()) Ec.safely("ads " + m.id() + " init", m::onInit);
    }

    /** Lets every module add its settings. */
    public static void registerSettings() {
        for (AdsModule m : AdsModules.all()) Ec.safely("ads " + m.id() + " settings", m::registerSettings);
    }
}
