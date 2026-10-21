// Ported from ADM's p3sim/run/RunConfig.kt
package com.teslamaps.adm.sim.run;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.Objects;

public final class RunConfig {
    /** `F7`. */
    public final String floor;
    public final LayoutSource layout;
    /** The run's own random seed. */
    public final long seed;
    public final boolean bots;
    /** Party size including you, 1..5. */
    public final int partySize;
    public final LagPreset lag;
    public final boolean noDeaths;
    /** null = draw from the pack. */
    public final Double mobHpScale;
    public final String statsPreset;
    public final String strategy;
    public final String replay;

    public RunConfig(String floor, LayoutSource layout, long seed, boolean bots, int partySize, LagPreset lag, boolean noDeaths,
                     Double mobHpScale, String statsPreset, String strategy, String replay) {
        this.floor = floor;
        this.layout = layout;
        this.seed = seed;
        this.bots = bots;
        this.partySize = partySize;
        this.lag = lag;
        this.noDeaths = noDeaths;
        this.mobHpScale = mobHpScale;
        this.statsPreset = statsPreset;
        this.strategy = strategy;
        this.replay = replay;
    }

    public RunConfig(String floor, LayoutSource layout, long seed, boolean bots, int partySize, LagPreset lag, boolean noDeaths,
                     Double mobHpScale, String statsPreset, String strategy) {
        this(floor, layout, seed, bots, partySize, lag, noDeaths, mobHpScale, statsPreset, strategy, null);
    }

    public RunConfig(String floor, LayoutSource layout, long seed, boolean bots, int partySize, LagPreset lag, boolean noDeaths,
                     Double mobHpScale, String statsPreset) {
        this(floor, layout, seed, bots, partySize, lag, noDeaths, mobHpScale, statsPreset, null, null);
    }

    public RunConfig(String floor, LayoutSource layout, long seed, boolean bots, int partySize, LagPreset lag, boolean noDeaths, Double mobHpScale) {
        this(floor, layout, seed, bots, partySize, lag, noDeaths, mobHpScale, null, null, null);
    }

    public RunConfig(String floor, LayoutSource layout, long seed, boolean bots, int partySize, LagPreset lag, boolean noDeaths) {
        this(floor, layout, seed, bots, partySize, lag, noDeaths, 1.0, null, null, null);
    }

    public RunConfig(String floor, LayoutSource layout, long seed, boolean bots, int partySize, LagPreset lag) {
        this(floor, layout, seed, bots, partySize, lag, false, 1.0, null, null, null);
    }

    public RunConfig(String floor, LayoutSource layout, long seed, boolean bots, int partySize) {
        this(floor, layout, seed, bots, partySize, LagPreset.OFF, false, 1.0, null, null, null);
    }

    public RunConfig(String floor, LayoutSource layout, long seed, boolean bots) {
        this(floor, layout, seed, bots, 5, LagPreset.OFF, false, 1.0, null, null, null);
    }

    public RunConfig(String floor, LayoutSource layout, long seed) {
        this(floor, layout, seed, true, 5, LagPreset.OFF, false, 1.0, null, null, null);
    }

    public RunConfig(String floor, LayoutSource layout) {
        this(floor, layout, System.nanoTime(), true, 5, LagPreset.OFF, false, 1.0, null, null, null);
    }

    public RunConfig(String floor) {
        this(floor, LayoutSource.Random.INSTANCE, System.nanoTime(), true, 5, LagPreset.OFF, false, 1.0, null, null, null);
    }

    public RunConfig() {
        this("F7", LayoutSource.Random.INSTANCE, System.nanoTime(), true, 5, LagPreset.OFF, false, 1.0, null, null, null);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("floor", floor);
        if (layout instanceof LayoutSource.Seed s) { o.addProperty("layout", "seed"); o.addProperty("layoutSeed", s.seed); }
        else if (layout instanceof LayoutSource.Recorded r) { o.addProperty("layout", "recorded"); o.addProperty("layoutId", r.id); }
        else if (layout instanceof LayoutSource.Practice p) { o.addProperty("layout", "practice"); o.addProperty("practiceRoom", p.room); }
        else o.addProperty("layout", "random");
        o.addProperty("seed", seed);
        o.addProperty("bots", bots);
        o.addProperty("partySize", partySize);
        o.addProperty("lag", lag.name());
        o.addProperty("noDeaths", noDeaths);
        // null is written: it means "draw from the pack", a missing field "the default"
        if (mobHpScale != null) o.addProperty("mobHpScale", mobHpScale); else o.add("mobHpScale", JsonNull.INSTANCE);
        if (statsPreset != null) o.addProperty("statsPreset", statsPreset);
        if (strategy != null) o.addProperty("strategy", strategy);
        if (replay != null) o.addProperty("replay", replay);
        return o;
    }

    private static JsonElement prim(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e : null;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = prim(o, k);
        return e != null ? e.getAsString() : null;
    }

    /** Reads what [toJson] wrote; unknown or missing fields keep their defaults. */
    public static RunConfig fromJson(JsonObject o) {
        String kind = str(o, "layout");
        LayoutSource layout;
        if ("seed".equals(kind)) {
            JsonElement e = prim(o, "layoutSeed");
            layout = e != null ? new LayoutSource.Seed(e.getAsLong()) : LayoutSource.Random.INSTANCE;
        } else if ("recorded".equals(kind)) {
            String id = str(o, "layoutId");
            layout = id != null ? new LayoutSource.Recorded(id) : LayoutSource.Random.INSTANCE;
        } else if ("practice".equals(kind)) {
            String room = str(o, "practiceRoom");
            layout = room != null ? new LayoutSource.Practice(room) : LayoutSource.Random.INSTANCE;
        } else {
            layout = LayoutSource.Random.INSTANCE;
        }
        RunConfig d = new RunConfig();
        String floor = str(o, "floor");
        JsonElement seed = prim(o, "seed");
        JsonElement bots = prim(o, "bots");
        JsonElement partySize = prim(o, "partySize");
        String lagName = str(o, "lag");
        LagPreset lag = d.lag;
        for (LagPreset p : LagPreset.values()) if (p.name().equals(lagName)) { lag = p; break; }
        JsonElement noDeaths = prim(o, "noDeaths");
        Double mobHpScale;
        if (o.has("mobHpScale")) {
            JsonElement e = o.get("mobHpScale");
            mobHpScale = e != null && e.isJsonPrimitive() ? e.getAsDouble() : null;
        } else {
            mobHpScale = d.mobHpScale;
        }
        return new RunConfig(
                floor != null ? floor : d.floor,
                layout,
                seed != null ? seed.getAsLong() : d.seed,
                bots != null ? bots.getAsBoolean() : d.bots,
                Math.clamp(partySize != null ? partySize.getAsInt() : d.partySize, 1, 5),
                lag,
                noDeaths != null ? noDeaths.getAsBoolean() : d.noDeaths,
                mobHpScale,
                str(o, "statsPreset"),
                str(o, "strategy"),
                str(o, "replay"));
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof RunConfig x && Objects.equals(floor, x.floor) && Objects.equals(layout, x.layout) && seed == x.seed && bots == x.bots
                && partySize == x.partySize && lag == x.lag && noDeaths == x.noDeaths && Objects.equals(mobHpScale, x.mobHpScale)
                && Objects.equals(statsPreset, x.statsPreset) && Objects.equals(strategy, x.strategy) && Objects.equals(replay, x.replay);
    }

    @Override public int hashCode() {
        return Objects.hash(floor, layout, seed, bots, partySize, lag, noDeaths, mobHpScale, statsPreset, strategy, replay);
    }

    @Override public String toString() {
        return "RunConfig(floor=" + floor + ", layout=" + layout + ", seed=" + seed + ", bots=" + bots + ", partySize=" + partySize + ", lag=" + lag
                + ", noDeaths=" + noDeaths + ", mobHpScale=" + mobHpScale + ", statsPreset=" + statsPreset + ", strategy=" + strategy + ", replay=" + replay + ")";
    }
}
