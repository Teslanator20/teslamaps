// Ported from ADM's p3sim/clear/blood/camp/CampEngine.kt
package com.teslamaps.adm.sim.clear.blood.camp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.clear.blood.camp.CampMech.Line;
import com.teslamaps.adm.sim.clear.blood.camp.CampMech.Request;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The Watcher's blood camp as a live state machine (the 10-tick task from the door click). No Minecraft. */
public class CampEngine {
    private enum State { OPENING, AT_HOME, LATER, DONE }

    private static final class Mob {
        final String handle;
        final int spawnTick;
        final boolean ws;
        int death = Integer.MAX_VALUE;
        boolean counted = false;

        Mob(String handle, int spawnTick, boolean ws) {
            this.handle = handle;
            this.spawnTick = spawnTick;
            this.ws = ws;
        }

        Mob(String handle, int spawnTick) { this(handle, spawnTick, false); }

        boolean alive() { return death == Integer.MAX_VALUE; }
    }

    private final Vec3 home;
    private final KRandom rng;
    private final CampPort port;
    private final CampConfig config;

    private final List<WallStation> throwOrder;
    private final Map<Integer, WallStation> bySlot = new HashMap<>();
    private final List<Mob> mobs = new ArrayList<>();
    private final Map<String, Mob> byHandle = new HashMap<>();
    private final List<Landing> scheduled = new ArrayList<>();
    private final List<Landing> thrown = new ArrayList<>();
    private final List<Request> requests = new ArrayList<>();

    private State state = State.OPENING;
    private Vec3 pos;
    private int movingUntil = -1;
    private int throws_ = 0;
    private int homeArrival = 0;
    private int kills = 0;
    private int last = -1;
    private int defeatPrinted = -1;
    private int pendingFinish = -1;
    private int moveStart = 0;
    private Vec3 moveFrom;
    private Vec3 moveTo;
    private double moveV = 0.0;
    private int moveN = 0;
    private final Set<CampMech.Printed> said = new HashSet<>();
    private final List<Pair<Integer, WallStation>> arrivals = new ArrayList<>();

    /** True on and after the tick the pass line prints. */
    public boolean finished = false;

    /** The tick of the first departure for the later heads, once it happened. */
    public int firstLaterDecision = -1;

    public CampEngine(Vec3 home, List<WallStation> stations, KRandom rng, CampPort port, CampConfig config, List<WallStation> order) {
        this.home = home;
        this.rng = rng;
        this.port = port;
        this.config = config;
        this.pos = home;
        this.moveFrom = home;
        this.moveTo = home;
        this.throwOrder = order != null ? order : WallPlan.throwOrder(stations, rng, config.laterThrows, config.wsChance);
        for (WallStation s : stations) bySlot.put(s.slot, s);
        if (config.recordedLines != null) requests.addAll(config.recordedLines);
        else requests.add(new Request(3, Line.INTRO));
    }

    public CampEngine(Vec3 home, List<WallStation> stations, KRandom rng, CampPort port, CampConfig config) {
        this(home, stations, rng, port, config, null);
    }

    public CampEngine(Vec3 home, List<WallStation> stations, KRandom rng, CampPort port) {
        this(home, stations, rng, port, new CampConfig(), null);
    }

    /** Kills counted so far. */
    public int killsCounted() { return kills; }

    /** The door tick the pass line prints, once known (null before). */
    public Integer passTick() { return defeatPrinted >= 0 ? defeatPrinted : null; }

    /** The landing plan so far, in throw order. */
    public List<Landing> landings() { return thrown; }

    /** Where the Watcher would be now. */
    public Vec3 watcherRest() { return pos; }

    /** A mob of this engine died on door tick r. */
    public void noteDeath(String handle, int r) {
        Mob m = byHandle.get(handle);
        if (m == null) return;
        if (m.alive()) m.death = r;
    }

    public void step(int r) { step(r, null); }

    /** Runs every tick up to r that has not run yet. */
    public void step(int r, Set<String> aliveNow) {
        if (finished) return;
        int t = last + 1;
        while (t <= r && !finished) {
            if (aliveNow != null && t == r) for (Mob m : mobs) if (m.alive() && m.handle != null && m.spawnTick < t && !aliveNow.contains(m.handle)) {
                m.death = t;
                port.log("mob " + m.handle + " vanished without a kill report on tick " + t + "; counted dead");
            }
            tickOne(t);
            t++;
        }
        last = Math.max(last, r);
    }

    private void tickOne(int r) {
        List<Landing> due = new ArrayList<>();
        for (Landing l : scheduled) if (l.spawnTick == r) due.add(l);
        for (Landing l : due) {
            String h = port.spawn(l);
            Mob m = new Mob(h, r, l.station.cls == WallClass.WANDERING_SOUL);
            if (h == null) { m.death = r; port.log("a camp mob could not be spawned at tick " + r + "; counted dead"); } else byHandle.put(h, m);
            mobs.add(m);
        }
        scheduled.removeAll(due);
        for (Landing l : thrown) if (l.throwTick == r) {
            port.headThrown(l.station);
            port.flyHead(l.station, headEnd(l), l.initial ? CampMech.FLIGHT_INITIAL : CampMech.FLIGHT_LATER);
        }
        List<Pair<Integer, WallStation>> gone = new ArrayList<>();
        for (Pair<Integer, WallStation> a : arrivals) if (a.first == r) gone.add(a);
        for (Pair<Integer, WallStation> g : gone) port.headArrived(g.second);
        arrivals.removeAll(gone);
        flyWatcher(r);
        sayLines(r);
        if (pendingFinish >= 0) { if (r >= pendingFinish) finished = true; return; }
        if (!CampMech.isLoop(r)) return;
        // the kill counter
        List<Mob> newly = new ArrayList<>();
        for (Mob m : mobs) if (!m.counted && !m.ws && m.death < r) newly.add(m);
        for (Mob m : newly) {
            m.counted = true;
            kills++;
            // asked for the next tick: this tick's lines are already out
            if (config.chatCoins && rng.nextBoolean()) ask(new Request(r + 1, Line.REACT));
        }
        if (!newly.isEmpty()) port.killsCounted(kills);
        if (kills >= config.killsToDefeat) {
            List<Request> asked = new ArrayList<>();
            for (Request q : requests) if (q.t <= r) asked.add(q);
            List<CampMech.Printed> lines = CampMech.chatLines(asked, r);
            defeatPrinted = CampMech.passTick(r, lines);
            state = State.DONE;
            pendingFinish = defeatPrinted;
        }
        if (pendingFinish >= 0) { if (r >= pendingFinish) finished = true; return; }
        int m10 = r / CampMech.LOOP;
        if (movingUntil >= r) return;
        int alive = 0;
        for (Mob m : mobs) if (!m.ws && m.spawnTick < r && m.death >= r) alive++;
        if (state == State.OPENING && m10 % 3 == 0 && m10 >= CampMech.OPEN_FIRST / CampMech.LOOP) {
            if (throws_ < 4) throwAt(r, CampMech.V_OPEN, true); else goHome(r);
        } else if ((state == State.AT_HOME || state == State.LATER) && throws_ < throwOrder.size() && m10 >= 42) {
            CampMech.Speed speed;
            if (alive == 0 && m10 % 4 == 0) speed = CampMech.Speed.FAST;
            else if (alive > 0 && m10 % 6 == 0) speed = CampMech.Speed.SLOW;
            else return;
            if (state == State.AT_HOME) {
                if (speed == CampMech.Speed.SLOW && r - homeArrival < CampMech.HOME_MIN) return;
                if (speed == CampMech.Speed.FAST && CampMech.talking(requests, r)) return;
                state = State.LATER;
                firstLaterDecision = r;
            }
            throwAt(r, speed.v, false);
        } else if (state == State.LATER && throws_ == throwOrder.size() && m10 % 4 == 0) {
            CampMech.Fly fly = CampMech.flyTo(pos, home, CampMech.V_HOME);
            beginMove(r, pos, fly, home, CampMech.V_HOME);
            if (rng.nextDouble() < config.enoughChance) ask(new Request(r, Line.ENOUGH));
            movingUntil = r + fly.n;
            pos = fly.end;
            state = State.DONE;
        }
    }

    /** Thrown heads aim 0.3 over the Watcher's home height. */
    public static final double HEAD_TARGET_RISE = 0.3;

    public static final double HEAD_LENGTH_INITIAL = 16.0;
    public static final double HEAD_LENGTH_LATER = 12.0;

    private void throwAt(int r, double v, boolean initial) {
        WallStation st = throwOrder.get(throws_++);
        CampMech.Fly fly = CampMech.flyTo(pos, CampMech.target(st.stand), v);
        int arrive = r + fly.n + 1;
        int thr = arrive + CampMech.THROW_DELAY;
        int spawn = thr + (initial ? CampMech.INITIAL_SPAWN : CampMech.LATER_SPAWN);
        int land = thr + (initial ? CampMech.FLIGHT_INITIAL : CampMech.FLIGHT_LATER);
        Landing l = new Landing(st, initial, thr, spawn, land);
        beginMove(r, pos, fly, CampMech.target(st.stand), v);
        arrivals.add(Pair.of(thr + (initial ? CampMech.FLIGHT_INITIAL : CampMech.FLIGHT_LATER), st));
        scheduled.add(l);
        thrown.add(l);
        pos = fly.end;
        movingUntil = r + fly.n;
        if (config.chatCoins && rng.nextBoolean()) ask(new Request(arrive, Line.SPAWN));
    }

    private Vec3 headEnd(Landing l) {
        return WallPlan.headEnd(l.station.stand, new Vec3(home.x, home.y + HEAD_TARGET_RISE, home.z), l.initial ? HEAD_LENGTH_INITIAL : HEAD_LENGTH_LATER);
    }

    /** Where a thrown head ends, as the mob's landing spot reads it. */
    public Vec3 endOf(Landing l) { return headEnd(l); }

    private void beginMove(int r, Vec3 from, CampMech.Fly fly, Vec3 to, double v) {
        moveStart = r; moveFrom = from; moveTo = to; moveV = v; moveN = fly.n;
    }

    private void flyWatcher(int r) {
        int k = r - moveStart;
        if (moveN <= 0 || k < 1 || k > moveN) return;
        Vec3 d = moveTo.minus(moveFrom);
        double dist = d.length();
        if (dist < 1e-9) return;
        Vec3 at = moveFrom.plus(d.times(Math.min(moveV * k, dist) / dist));
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        port.watcherAt(at.x, at.y, at.z, yaw);
    }

    private void sayLines(int r) {
        List<Request> asked = new ArrayList<>();
        for (Request q : requests) if (q.t <= r) asked.add(q);
        for (CampMech.Printed p : CampMech.chatLines(asked, r + 1)) if (p.at == r && said.add(p)) port.say(p.line);
    }

    private void ask(Request request) { if (config.recordedLines == null) requests.add(request); }

    private void goHome(int r) {
        CampMech.Fly fly = CampMech.flyTo(pos, home, CampMech.V_HOME);
        movingUntil = r + fly.n;
        beginMove(r, pos, fly, home, CampMech.V_HOME);
        homeArrival = r + fly.n + 1;
        ask(new Request(homeArrival, Line.HANDLE));
        pos = fly.end;
        state = State.AT_HOME;
    }

    // ---- savepoints

    public JsonObject snapshot() {
        JsonObject o = new JsonObject();
        o.addProperty("state", state.name());
        o.addProperty("last", last);
        o.addProperty("throws", throws_);
        o.addProperty("kills", kills);
        o.addProperty("movingUntil", movingUntil);
        o.addProperty("homeArrival", homeArrival);
        o.addProperty("defeatPrinted", defeatPrinted);
        o.addProperty("pendingFinish", pendingFinish);
        o.addProperty("firstLaterDecision", firstLaterDecision);
        o.addProperty("finished", finished);
        o.add("pos", vec(pos));
        o.addProperty("moveStart", moveStart);
        o.add("moveFrom", vec(moveFrom)); o.add("moveTo", vec(moveTo));
        o.addProperty("moveV", moveV); o.addProperty("moveN", moveN);
        JsonArray order = new JsonArray();
        for (WallStation s : throwOrder) order.add(s.slot);
        o.add("order", order);
        JsonArray ma = new JsonArray();
        for (Mob m : mobs) {
            JsonObject j = new JsonObject();
            if (m.handle != null) j.addProperty("h", m.handle);
            j.addProperty("spawn", m.spawnTick); j.addProperty("ws", m.ws); j.addProperty("counted", m.counted);
            if (!m.alive()) j.addProperty("death", m.death);
            ma.add(j);
        }
        o.add("mobs", ma);
        o.add("scheduled", landingsJson(scheduled));
        o.add("thrown", landingsJson(thrown));
        JsonArray ra = new JsonArray();
        for (Request q : requests) { JsonArray p = new JsonArray(); p.add(q.t); p.add(q.line.name()); ra.add(p); }
        o.add("requests", ra);
        JsonArray sa = new JsonArray();
        for (CampMech.Printed q : said) { JsonArray p = new JsonArray(); p.add(q.at); p.add(q.line.name()); p.add(q.requested); sa.add(p); }
        o.add("said", sa);
        JsonArray aa = new JsonArray();
        for (Pair<Integer, WallStation> q : arrivals) { JsonArray p = new JsonArray(); p.add(q.first); p.add(q.second.slot); aa.add(p); }
        o.add("arrivals", aa);
        return o;
    }

    private static JsonArray vec(Vec3 v) {
        JsonArray a = new JsonArray();
        a.add(v.x); a.add(v.y); a.add(v.z);
        return a;
    }

    private static Vec3 vec(JsonArray a) { return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()); }

    private static JsonArray landingsJson(List<Landing> list) {
        JsonArray a = new JsonArray();
        for (Landing l : list) {
            JsonObject j = new JsonObject();
            j.addProperty("slot", l.station.slot); j.addProperty("initial", l.initial);
            j.addProperty("throw", l.throwTick); j.addProperty("spawn", l.spawnTick); j.addProperty("land", l.landTick);
            a.add(j);
        }
        return a;
    }

    private Landing land(JsonObject j) {
        WallStation st = Objects.requireNonNull(bySlot.get(j.get("slot").getAsInt()));
        return new Landing(st, j.get("initial").getAsBoolean(), j.get("throw").getAsInt(), j.get("spawn").getAsInt(), j.get("land").getAsInt());
    }

    /** Takes the state snapshot wrote; false (nothing changed) if it does not fit the wall. */
    public boolean restore(JsonObject o) {
        try {
            List<Mob> newMobs = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("mobs")) {
                JsonObject j = e.getAsJsonObject();
                JsonElement h = j.get("h");
                Mob m = new Mob(h != null ? h.getAsString() : null, j.get("spawn").getAsInt(), j.get("ws").getAsBoolean());
                m.counted = j.get("counted").getAsBoolean();
                JsonElement d = j.get("death");
                if (d != null) m.death = d.getAsInt();
                newMobs.add(m);
            }
            List<Landing> newScheduled = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("scheduled")) newScheduled.add(land(e.getAsJsonObject()));
            List<Landing> newThrown = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("thrown")) newThrown.add(land(e.getAsJsonObject()));
            List<Request> newRequests = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("requests")) {
                JsonArray a = e.getAsJsonArray();
                newRequests.add(new Request(a.get(0).getAsInt(), Line.valueOf(a.get(1).getAsString())));
            }
            List<CampMech.Printed> newSaid = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("said")) {
                JsonArray a = e.getAsJsonArray();
                newSaid.add(new CampMech.Printed(a.get(0).getAsInt(), Line.valueOf(a.get(1).getAsString()), a.get(2).getAsInt()));
            }
            List<Pair<Integer, WallStation>> newArrivals = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("arrivals")) {
                JsonArray a = e.getAsJsonArray();
                newArrivals.add(Pair.of(a.get(0).getAsInt(), Objects.requireNonNull(bySlot.get(a.get(1).getAsInt()))));
            }
            State newState = State.valueOf(o.get("state").getAsString());
            Vec3 newPos = vec(o.getAsJsonArray("pos"));
            Vec3 newFrom = vec(o.getAsJsonArray("moveFrom")); Vec3 newTo = vec(o.getAsJsonArray("moveTo"));
            String[] keys = {"last", "throws", "kills", "movingUntil", "homeArrival", "defeatPrinted", "pendingFinish", "firstLaterDecision", "moveStart", "moveN"};
            int[] ints = new int[keys.length];
            for (int i = 0; i < keys.length; i++) ints[i] = o.get(keys[i]).getAsInt();
            double newMoveV = o.get("moveV").getAsDouble(); boolean newFinished = o.get("finished").getAsBoolean();
            // everything parsed: take it
            state = newState; pos = newPos; moveFrom = newFrom; moveTo = newTo; moveV = newMoveV; finished = newFinished;
            last = ints[0]; throws_ = ints[1]; kills = ints[2]; movingUntil = ints[3]; homeArrival = ints[4];
            defeatPrinted = ints[5]; pendingFinish = ints[6]; firstLaterDecision = ints[7]; moveStart = ints[8]; moveN = ints[9];
            mobs.clear(); mobs.addAll(newMobs);
            byHandle.clear(); for (Mob m : newMobs) if (m.handle != null) byHandle.put(m.handle, m);
            scheduled.clear(); scheduled.addAll(newScheduled);
            thrown.clear(); thrown.addAll(newThrown);
            requests.clear(); requests.addAll(newRequests);
            said.clear(); said.addAll(newSaid);
            arrivals.clear(); arrivals.addAll(newArrivals);
            return true;
        } catch (Exception t) {
            port.log("the saved camp does not fit this wall: " + t.getMessage());
            return false;
        }
    }
}
