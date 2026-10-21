// Ported from ADM's p3sim/run/RunSavepoints.kt
package com.teslamaps.adm.sim.run;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.Ec;
import java.util.ArrayList;
import java.util.List;

public class RunSavepoints implements SavepointStore {
    private final SavepointWorld world;
    private final int maxKept;
    private final ArrayList<Savepoint> points = new ArrayList<>();
    private int counter = 0;

    public boolean restoring = false;

    public RunSavepoints() { this(SavepointWorld.None.INSTANCE); }

    public RunSavepoints(SavepointWorld world) { this(world, 120); }

    public RunSavepoints(SavepointWorld world, int maxKept) {
        this.world = world;
        this.maxKept = maxKept;
    }

    @Override
    public Savepoint take(Run run, String label, Integer roomIndex) {
        if (restoring || run.isOver() || !run.stage.inDungeon()) return null;
        for (PlayerRecord p : run.state.players.values()) if (p.ghost && !p.isBot) return null;
        JsonObject modules = new JsonObject();
        for (RunSystem s : run.systemList()) {
            JsonObject snap;
            try {
                snap = s.snapshot(run);
            } catch (Throwable t) {
                Ec.LOG.error("[ads] " + s.id() + ".snapshot failed", t);
                snap = null;
            }
            if (snap != null) modules.add(s.id(), snap);
        }
        JsonObject data = new JsonObject();
        data.add("state", run.state.snapshot());
        data.add("modules", modules);
        data.add("players", world.capturePlayers());
        data.addProperty("serverTick", run.serverTick);
        data.addProperty("t0Known", run.t0Tick != null);
        Savepoint sp = new Savepoint("sp" + (++counter), label, run.t(), run.stage, roomIndex, data);
        points.add(sp);
        if (points.size() > maxKept) points.remove(1);
        run.bus.post(new SavepointTaken(run.t(), sp.id));
        return sp;
    }

    @Override
    public List<Savepoint> list() { return new ArrayList<>(points); }

    @Override
    public boolean restore(Run run, String id) {
        Savepoint sp = null;
        for (Savepoint p : points) if (p.id.equals(id)) { sp = p; break; }
        if (sp == null) return false;
        if (restoring || run.isOver() || !run.stage.inDungeon() || !sp.stage.inDungeon()) {
            Ec.LOG.warn("[ads] savepoint " + sp.id + " (" + sp.label + ") cannot be restored in stage " + run.stage);
            return false;
        }
        restoring = true;
        try {
            if (run.stage != sp.stage) run.forceStage(sp.stage);
            JsonElement t0 = sp.data.get("t0Known");
            run.restoreClock(sp.t, t0 != null ? t0.getAsBoolean() : true);
            JsonObject state = sp.data.getAsJsonObject("state");
            if (state != null) run.state.restore(state);
            int fixed;
            try {
                fixed = world.resyncRegions();
            } catch (Throwable t) {
                Ec.LOG.error("[ads] region resync failed", t);
                fixed = 0;
            }
            try {
                world.clearEntities();
            } catch (Throwable t) {
                Ec.LOG.error("[ads] clearing entities failed", t);
            }
            JsonObject modules = sp.data.getAsJsonObject("modules");
            for (RunSystem s : run.systemList()) {
                if (modules == null) continue;
                JsonElement e = modules.get(s.id());
                if (e == null || !e.isJsonObject()) continue;
                try {
                    s.restore(run, e.getAsJsonObject());
                } catch (Throwable t) {
                    Ec.LOG.error("[ads] " + s.id() + ".restore failed", t);
                }
            }
            JsonArray players = sp.data.getAsJsonArray("players");
            if (players != null) {
                try {
                    world.restorePlayers(players);
                } catch (Throwable t) {
                    Ec.LOG.error("[ads] restoring players failed", t);
                }
            }
            Ec.LOG.info("[ads] restored savepoint " + sp.id + " \"" + sp.label + "\" at t=" + sp.t + " (" + fixed + " blocks resynced)");
        } finally {
            restoring = false;
        }
        run.bus.post(new SavepointRestored(run.t(), sp.id));
        return true;
    }
}
