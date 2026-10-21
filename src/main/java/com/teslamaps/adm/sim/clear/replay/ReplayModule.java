// Ported from ADM's p3sim/clear/replay/ReplayModule.kt
package com.teslamaps.adm.sim.clear.replay;

import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.DungeonClass;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.P3Sim;
import com.teslamaps.adm.sim.Party;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimNet;
import com.teslamaps.adm.sim.SimServer;
import com.teslamaps.adm.sim.clear.ui.RunStarter;
import com.teslamaps.adm.sim.clear.ui.UiModule;
import com.teslamaps.adm.sim.run.AdsCommand;
import com.teslamaps.adm.sim.run.AdsModule;
import com.teslamaps.adm.sim.run.EndReason;
import com.teslamaps.adm.sim.run.LagPreset;
import com.teslamaps.adm.sim.run.LayoutSource;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunConfig;
import com.teslamaps.adm.sim.run.RunEvent;
import com.teslamaps.adm.sim.run.RunStage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;

/**
 * Replays: a recorded run played in ADS on its own layout, taking over your player (`/ads run replay <id>`). You see what the
 * recording's player saw while ADS answers every click as it would yours, the teammates walk their recorded paths, and the differ
 * writes down every place ADS went another way (ReplayDiff).
 */
public final class ReplayModule implements AdsModule {
    public static final ReplayModule INSTANCE = new ReplayModule();

    private ReplayModule() {}

    @Override public String id() { return "replay"; }

    @Override public String owner() { return "replay"; }

    public static volatile ReplaySession session = null;

    @Override
    public void onInit() {
        ReplayClient.init();
        SimNet.onServer(NET + ".stop", (p, d) -> {
            ReplaySession s = session;
            if (s != null) s.stop("stopped by /ads run replay stop");
        });
    }

    @Override
    public void onRunStart(Run run) {
        ReplaySession old = session;
        if (old != null) old.stop("a new run started");
        session = null;
        String id = run.config.replay;
        if (id == null) return;
        ServerPlayer host = SimServer.player();
        if (host == null) return;
        try {
            ReplaySession s = new ReplaySession(run, ReplayFile.load(id), host);
            session = s;
            s.start();
        } catch (Throwable t) {
            Sim.note("Replay " + id + " could not be read: " + t.getMessage());
        }
    }

    @Override
    public void onTick(Run run) {
        ReplaySession s = session;
        if (s != null && s.run == run && !s.ended) s.tick();
    }

    @Override
    public void onStage(Run run, RunStage from, RunStage to) {
        if (to == RunStage.BOSS_ENTRY || to == RunStage.BOSS) {
            ReplaySession s = session;
            if (s != null && s.run == run) s.stop("ADS reached the boss");
        }
    }

    @Override
    public void onEvent(Run run, RunEvent event) {
        ReplaySession s = session;
        if (s != null && s.run == run && !s.ended) s.diff.adsEvent(event);
    }

    @Override
    public void onRunEnd(Run run, EndReason reason) {
        ReplaySession s = session;
        if (s != null && s.run == run) s.stop("the run ended (" + reason + ")");
        session = null;
    }

    @Override
    public List<AdsCommand> runCommands() {
        return List.of(new AdsCommand("replay", "/ads run replay [list | <id> | stop]",
                "Plays a recorded run on its layout with your player taken over; \"stop\" gives you it back. The report is written beside the replay.",
                false, ctx -> {
                    String a = ctx.arg(0);
                    if (a == null || a.equals("list")) {
                        List<String> all = ReplayFile.list();
                        if (all.isEmpty()) ctx.reply.accept("No replays in config/teslamaps/adm/ads/replays.");
                        else {
                            ctx.reply.accept(all.size() + " replays:");
                            for (String it : all) ctx.reply.accept("  " + it);
                        }
                    } else if (a.equals("stop")) {
                        SimNet.send(NET + ".stop");
                        ReplayClient.stop();
                    } else {
                        String id = ReplayFile.resolve(a);
                        if (id == null) { ctx.reply.accept("No replay \"" + a + "\" (or more than one starts so): /ads run replay list."); return; }
                        ReplayFile f;
                        try { f = ReplayFile.load(id); } catch (Throwable t) { ctx.reply.accept("Replay " + id + " could not be read: " + t.getMessage()); return; }
                        // You play the recording's class (the leap window, the tab and the class's items are theirs).
                        String want = f.self.cls != null ? f.self.cls.toUpperCase(Locale.ROOT) : null;
                        List<DungeonClass> classes = Party.CLASSES();
                        int ci = -1;
                        for (int i = 0; i < classes.size(); i++) if (classes.get(i).name().equals(want)) { ci = i; break; }
                        if (ci >= 0) P3Sim.classS.setIndex(ci);
                        List<String> mates = new ArrayList<>();
                        for (ReplayFile.Person p : f.party) mates.add(p.name + " (" + (p.cls != null ? p.cls : "?") + ")");
                        ctx.reply.accept("Replaying " + id + ": " + (f.self.cls != null ? f.self.cls : "?") + ", with " + String.join(", ", mates) + ".");
                        Minecraft.getInstance().execute(() -> RunStarter.start(new RunConfig("F7", new LayoutSource.Recorded(id), UiModule.drawSeed(), false, 5,
                                LagPreset.OFF, false, 1.0, null, null, id), msg -> {
                            ctx.reply.accept(msg);
                            // A layout ADS cannot build: the report says so at once (a batch must not wait on a replay that never starts).
                            for (String fl : FAILED) if (msg.contains(fl)) { failedReport(id, msg); break; }
                        }));
                    }
                }));
    }

    /** The report of a replay that never started: not complete, and why. */
    private static void failedReport(String id, String why) {
        Ec.safely("ads replay failed report", () -> {
            JsonObject j = new JsonObject();
            j.addProperty("id", id);
            j.addProperty("complete", false);
            j.addProperty("unbuildable", true);
            j.addProperty("end", why.replaceAll("§.", ""));
            try {
                Files.createDirectories(ReplayFile.dir());
                Files.writeString(ReplayFile.dir().resolve(id + ".report.json"), j.toString());
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    /** What RunStarter says when a run does not start. */
    private static final List<String> FAILED = List.of("not playable", "could not", "There is no recorded layout", " matches ", "Only the host");

    public static final String NET = ReplaySession.NET;
}
