// Ported from ADM's p3sim/clear/bots/BotTables.kt
package com.teslamaps.adm.sim.clear.bots;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.pack.JsonKt;
import com.teslamaps.adm.sim.pack.runstate.ClassRole;
import com.teslamaps.adm.sim.pack.runstate.Dist;
import com.teslamaps.adm.sim.pack.runstate.RoomRun;
import com.teslamaps.adm.sim.pack.runstate.RunStatePack;
import com.teslamaps.adm.sim.pack.runstate.Splits;
import com.teslamaps.adm.sim.pack.runstate.TypeStates;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.RoomType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The run-state tables as the ClearPlan reads them, each with a built-in default. Pure. */
public class BotTables {
    private final RunStatePack pack;
    public final String floor;

    public BotTables(RunStatePack pack, String floor) {
        this.pack = pack;
        this.floor = floor;
    }

    public BotTables(RunStatePack pack) { this(pack, BotTuning.FLOOR); }

    private Splits splits() { return pack == null || pack.splits == null ? null : pack.splits.get(floor); }

    private JsonObject rolesRaw() { return pack == null || pack.classRoles == null ? null : pack.classRoles.raw; }

    /** True when the pack's tables are being used. */
    public boolean fromPack() { return pack != null; }

    private static Dist ok(Dist d) { return d != null && !d.advisory() ? d : null; }

    private Dist splitDist(String key) {
        Splits s = splits();
        if (s == null || s.dists == null) return null;
        return ok(s.dists.get(key));
    }

    // ---- doors, keys, blood

    /** Ticks after T0 that the [k]-th paid wither door (1-based) falls. */
    public Dist doorOpen(int k) {
        JsonObject raw = rolesRaw();
        JsonObject o = raw == null ? null : JsonKt.obj(raw, "doorOpenAfterT0Ticks");
        Dist d = null;
        if (o != null) {
            JsonElement e = o.get(String.valueOf(Math.min(k, 6)));
            if (e != null) d = Dist.parse(e, pack != null && pack.pct != null ? pack.pct : Dist.DEFAULT_PCT);
        }
        if (d != null && !d.advisory()) return k <= 6 ? d : shifted(d, (k - 6) * DEFAULT_EXTRA_DOOR_TICKS);
        return PlanMath.around(DEFAULT_DOOR_MEDIANS[Math.clamp(k - 1, 0, DEFAULT_DOOR_MEDIANS.length - 1)] + Math.max(k - DEFAULT_DOOR_MEDIANS.length, 0) * DEFAULT_EXTRA_DOOR_TICKS,
                DOOR_SHAPE);
    }

    /** Ticks from a wither key being taken to another player opening its door. */
    public Dist keyToUse() {
        Dist d = splitDist("keyToUse");
        return d != null ? d : KEY_TO_USE;
    }

    /** Ticks from the last paid wither door falling to the blood key. */
    public Dist bloodKeyAfterLastDoor() {
        Dist d = splitDist("bloodKeyAfterLastDoor");
        return d != null ? d : BLOOD_KEY_AFTER_LAST_DOOR;
    }

    /** Ticks from the paid wither door before to the [k]-th (k at least 2) falling. */
    public Dist doorGap(int k) {
        Dist d = splitDist("doorGap" + k);
        return d != null ? d : DOOR_GAPS.get(Math.clamp(k - 2, 0, DOOR_GAPS.size() - 1));
    }

    /** Ticks after T0 that the first paid wither door falls in a five-player party. */
    public Dist firstDoor() {
        Dist d = splitDist("firstDoor");
        return d != null ? d : FIRST_DOOR;
    }

    /** Ticks after T0 that the blood key is picked up (a layout with no paid door). */
    public Dist bloodKey() {
        Splits s = splits();
        Dist d = s == null ? null : ok(s.bloodKey());
        if (d != null) return d;
        Dist e = pack == null || pack.doors == null ? null : ok(pack.doors.bloodKeyAfterStart);
        if (e != null) return e;
        return PlanMath.around(DEFAULT_BLOOD_KEY, DOOR_SHAPE);
    }

    /** Ticks from the blood key to the blood door falling. */
    public Dist bloodDoorAfterKey() {
        Dist d = splitDist("bloodDoorAfterKey");
        return d != null ? d : KEY_TO_USE;
    }

    // ---- the milestones the gate compares

    public Dist recordedBloodOpen() { Splits s = splits(); return s == null ? null : s.bloodOpen(); }

    public Dist recordedWatcherDone() { Splits s = splits(); return s == null ? null : s.watcherDone(); }

    public Dist recordedMaxorEntry() { Splits s = splits(); return s == null ? null : s.maxorEntry(); }

    public Dist recordedTilesDone() { Splits s = splits(); return s == null ? null : s.tilesDoneAtMaxor(); }

    public Dist recordedGreenFrac() { Splits s = splits(); return s == null ? null : s.roomsGreenFracAtMaxor(); }

    /** bloodOpen to the Watcher's "proven" line. */
    public Dist campTicks() {
        Splits s = splits();
        Dist d = s == null ? null : ok(s.campTicks());
        return d != null ? d : PlanMath.around(DEFAULT_CAMP_TICKS, CAMP_SHAPE);
    }

    /** "Proven" to the first boss line. */
    public Dist provenToMaxor() {
        Splits s = splits();
        Dist d = s == null ? null : ok(s.provenToMaxor());
        return d != null ? d : PlanMath.around(DEFAULT_PROVEN_TO_MAXOR, CAMP_SHAPE);
    }

    /** The share of runs in which the blood room is GREEN (not RED) at Maxor. */
    public double bloodGreenFrac() {
        Splits s = splits();
        JsonObject raw = s == null ? null : s.raw;
        JsonObject perType = raw == null ? null : JsonKt.obj(raw, "perType");
        JsonObject blood = perType == null ? null : JsonKt.obj(perType, "BLOOD");
        Double v = blood == null ? null : JsonKt.dbl(blood, "greenFrac");
        return v != null ? v : DEFAULT_BLOOD_GREEN_FRAC;
    }

    // ---- rooms

    /** First entry to GREEN for a room, for a seat of class [cls]. */
    public Dist roomDur(PlacedRoom room, String cls) {
        RoomRun rr = pack == null ? null : pack.room(room.name);
        if (cls != null && rr != null && rr.byClass != null) {
            Dist d = ok(rr.byClass.get(cls));
            if (d != null) return d;
        }
        if (rr != null) {
            Dist d = ok(rr.dur);
            if (d != null) return d;
        }
        return typeDur(room.type);
    }

    private Dist typeTicks(PlacedRoom room, String key) {
        if (pack == null || pack.checkStates == null || pack.checkStates.types == null) return null;
        TypeStates ts = pack.checkStates.types.get(room.type.name().toLowerCase(java.util.Locale.ROOT));
        if (ts == null || ts.ticks == null) return null;
        return ok(ts.ticks.get(key));
    }

    /** NONE to WHITE for a room with secrets, or null when nothing is measured for it. */
    public Dist roomToWhite(PlacedRoom room) {
        RoomRun rr = pack == null ? null : pack.room(room.name);
        Dist d = rr == null ? null : ok(rr.toWhite);
        return d != null ? d : typeTicks(room, "NtoW");
    }

    /** WHITE to GREEN for a room, or null. */
    public Dist roomToGreen(PlacedRoom room) {
        RoomRun rr = pack == null ? null : pack.room(room.name);
        Dist d = rr == null ? null : ok(rr.toGreen);
        return d != null ? d : typeTicks(room, "WtoG");
    }

    private Dist typeDur(RoomType type) {
        String key = type == RoomType.YELLOW ? "CHAMPION" : type.name();
        Dist d = pack == null || pack.roomTypeDur == null ? null : ok(pack.roomTypeDur.get(key));
        if (d != null) return d;
        Double def = DEFAULT_TYPE_DUR.get(type);
        return PlanMath.around(def != null ? def : 400.0);
    }

    // ---- classes

    /** Rooms-owned weight of a class for the clear rooms. */
    public double classOwned(String cls) {
        JsonObject raw = rolesRaw();
        JsonObject c = raw == null ? null : JsonKt.obj(raw, cls);
        JsonObject by = c == null ? null : JsonKt.obj(c, "ownedByType");
        if (by != null) {
            double sum = 0.0;
            for (String k : List.of("NORMAL", "PUZZLE", "TRAP", "CHAMPION")) {
                Double v = JsonKt.dbl(by, k);
                sum += v != null ? v : 0.0;
            }
            return sum > 0 ? sum : DEFAULT_CLASS_OWNED;
        }
        ClassRole role = role(cls);
        if (role != null && role.roomsOwnedMean > 0) return role.roomsOwnedMean;
        return DEFAULT_CLASS_OWNED;
    }

    private ClassRole role(String cls) {
        if (pack == null || pack.classRoles == null || pack.classRoles.classes == null) return null;
        return pack.classRoles.classes.get(cls);
    }

    /** How good a class is at puzzles, 0..1. */
    public double puzzleAffinity(String cls) {
        if (pack == null || pack.classRoles == null || pack.classRoles.classes == null) return 1.0;
        Map<String, ClassRole> roles = pack.classRoles.classes;
        Double bestOrNull = null;
        for (ClassRole r : roles.values()) if (bestOrNull == null || r.puzzlesOwnedMean > bestOrNull) bestOrNull = r.puzzlesOwnedMean;
        double best = bestOrNull != null ? bestOrNull : 0.0;
        if (best <= 0.0) return 1.0;
        ClassRole mine = roles.get(cls);
        return Math.clamp((mine != null ? mine.puzzlesOwnedMean : best) / best, 0.0, 1.0);
    }

    /** Share of games the class opened the wither doors. */
    public double doorOpenerShare(String cls) {
        ClassRole r = role(cls);
        if (r != null) return r.doorOpenerFrac;
        Double d = DEFAULT_OPENER_SHARE.get(cls);
        return d != null ? d : 0.0;
    }

    /** Planning speed of a class while moving, blocks per tick (hops included). */
    public double moveBpt(String cls) {
        ClassRole r = role(cls);
        Double v = r != null ? r.movingSpeedBpt : null;
        return Math.clamp(v != null ? v : BotTuning.DEFAULT_PLAN_BPT, 0.4, BotTuning.MAX_TRAVEL_BPT);
    }

    private static Dist shifted(Dist d, double by) {
        double[] q = new double[d.q.length];
        for (int i = 0; i < q.length; i++) q[i] = d.q[i] + by;
        return new Dist(d.n, d.pct, q, d.min != null ? d.min + by : null, d.max != null ? d.max + by : null);
    }

    // Built-in defaults when a table is missing.
    public static final double[] DEFAULT_DOOR_MEDIANS = new double[]{60.0, 140.0, 210.0, 265.0, 315.0, 365.0};
    public static final double DEFAULT_EXTRA_DOOR_TICKS = 50.0;
    public static final double[] DOOR_SHAPE = new double[]{0.55, 0.65, 0.8, 1.0, 1.3, 1.7, 2.2};
    public static final double DEFAULT_KEY_TO_USE = 6.0;
    public static final double DEFAULT_BLOOD_KEY = 300.0;
    public static final double DEFAULT_DOOR_AFTER_KEY = 6.0;
    /** Measured: the blood key after the last wither door. */
    public static final Dist BLOOD_KEY_AFTER_LAST_DOOR = new Dist(321, new double[]{0.0, 5.0, 10.0, 25.0, 50.0, 75.0, 90.0, 95.0, 100.0},
            new double[]{18.0, 23.0, 26.0, 35.0, 51.0, 72.0, 90.0, 102.0, 178.0}, 18.0, 178.0);
    private static final double[] GAP_PCT = new double[]{0.0, 5.0, 10.0, 25.0, 50.0, 75.0, 90.0, 95.0, 100.0};
    /** Measured: a key taken to another player opening its door. */
    public static final Dist KEY_TO_USE = new Dist(1132, GAP_PCT, new double[]{0.0, 1.0, 1.0, 2.0, 4.0, 15.0, 29.0, 37.0, 120.0}, 0.0, 120.0);
    /** Measured: the first wither door, ticks after T0. */
    public static final Dist FIRST_DOOR = new Dist(321, GAP_PCT, new double[]{24.0, 34.0, 36.0, 45.0, 58.0, 78.0, 107.0, 127.0, 240.0}, 24.0, 240.0);
    /** Measured: the gap from the paid door before to the 2nd, 3rd, 4th and 5th-or-later door. */
    public static final List<Dist> DOOR_GAPS = List.of(
            new Dist(319, GAP_PCT, new double[]{26.0, 35.0, 42.0, 54.0, 71.0, 96.0, 117.0, 137.0, 246.0}, 26.0, 246.0),
            new Dist(273, GAP_PCT, new double[]{28.0, 34.0, 38.0, 48.0, 62.0, 86.0, 120.0, 137.0, 184.0}, 28.0, 184.0),
            new Dist(149, GAP_PCT, new double[]{26.0, 29.0, 34.0, 46.0, 58.0, 68.0, 96.0, 118.0, 231.0}, 26.0, 231.0),
            new Dist(72, GAP_PCT, new double[]{26.0, 29.0, 33.0, 39.0, 52.0, 62.0, 75.0, 82.0, 124.0}, 26.0, 124.0)
    );
    // Invented stand-ins for the blood room's length and the wait after it.
    public static final double DEFAULT_CAMP_TICKS = 1000.0;
    public static final double DEFAULT_PROVEN_TO_MAXOR = 100.0;
    public static final double[] CAMP_SHAPE = new double[]{0.8, 0.85, 0.9, 1.0, 1.1, 1.15, 1.2};
    public static final double DEFAULT_BLOOD_GREEN_FRAC = 0.47;
    public static final double DEFAULT_CLASS_OWNED = 3.5;
    /** Without the class table every class is as likely to be the door crew. */
    public static final Map<String, Double> DEFAULT_OPENER_SHARE = Map.of("HEALER", 0.2, "ARCHER", 0.2, "MAGE", 0.2, "BERSERK", 0.2, "TANK", 0.2);
    public static final Map<RoomType, Double> DEFAULT_TYPE_DUR;

    static {
        EnumMap<RoomType, Double> m = new EnumMap<>(RoomType.class);
        m.put(RoomType.NORMAL, 490.0);
        m.put(RoomType.PUZZLE, 280.0);
        m.put(RoomType.TRAP, 220.0);
        m.put(RoomType.YELLOW, 30.0);
        m.put(RoomType.RARE, 235.0);
        m.put(RoomType.FAIRY, 0.0);
        m.put(RoomType.ENTRANCE, 0.0);
        m.put(RoomType.BLOOD, 1100.0);
        DEFAULT_TYPE_DUR = m;
    }
}
