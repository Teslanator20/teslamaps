// Ported from ADM's p3sim/pack/runstate/RunStatePack.kt
package com.teslamaps.adm.sim.pack.runstate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.teslamaps.adm.sim.pack.JsonKt;
import com.teslamaps.adm.sim.pack.PackFiles;
import com.teslamaps.adm.sim.pack.PackReport;
import com.teslamaps.adm.sim.pack.PackSource;
import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.pack.rooms.RoomPackKt;
import com.teslamaps.adm.sim.util.Pair;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The loaded run-state tables. Typed sections are null when absent or malformed (see [problems]).
 * [raw] is the whole parsed file for fields this class does not model.
 */
public class RunStatePack {
    public final int version;
    public final double[] pct;
    public final Map<String, RoomRun> rooms;
    public final DoorRules doors;
    public final Map<String, Splits> splits;
    public final CheckStates checkStates;
    public final CounterRules counters;
    public final ScoreModel score;
    public final Map<String, Dist> roomTypeDur;
    public final ClassRoles classRoles;
    public final Map<String, Dist> puzzleSolve;
    public final PuzzleOdds puzzles;
    public final List<String> chatFormats;
    public final List<SbStatPreset> sbStatPresets;
    public final DamageRules damage;
    public final LagModel lag;
    public final List<NamedConstant> constants;
    /** One line per section that was missing or malformed. */
    public final List<String> problems;
    public final JsonObject raw;
    /** Trap rooms by in-game name: the chest that finishes them. Empty when the pack has no {@code chestClears} section. */
    public final Map<String, ChestClear> chestClears;

    public RunStatePack(int version, double[] pct, Map<String, RoomRun> rooms, DoorRules doors, Map<String, Splits> splits, CheckStates checkStates,
                        CounterRules counters, ScoreModel score, Map<String, Dist> roomTypeDur, ClassRoles classRoles, Map<String, Dist> puzzleSolve,
                        PuzzleOdds puzzles, List<String> chatFormats, List<SbStatPreset> sbStatPresets, DamageRules damage, LagModel lag,
                        List<NamedConstant> constants, List<String> problems, JsonObject raw, Map<String, ChestClear> chestClears) {
        this.version = version;
        this.pct = pct;
        this.rooms = rooms;
        this.doors = doors;
        this.splits = splits;
        this.checkStates = checkStates;
        this.counters = counters;
        this.score = score;
        this.roomTypeDur = roomTypeDur;
        this.classRoles = classRoles;
        this.puzzleSolve = puzzleSolve;
        this.puzzles = puzzles;
        this.chatFormats = chatFormats;
        this.sbStatPresets = sbStatPresets;
        this.damage = damage;
        this.lag = lag;
        this.constants = constants;
        this.problems = problems;
        this.raw = raw;
        this.chestClears = chestClears;
    }

    public RunStatePack(int version, double[] pct, Map<String, RoomRun> rooms, DoorRules doors, Map<String, Splits> splits, CheckStates checkStates,
                        CounterRules counters, ScoreModel score, Map<String, Dist> roomTypeDur, ClassRoles classRoles, Map<String, Dist> puzzleSolve,
                        PuzzleOdds puzzles, List<String> chatFormats, List<SbStatPreset> sbStatPresets, DamageRules damage, LagModel lag,
                        List<NamedConstant> constants, List<String> problems, JsonObject raw) {
        this(version, pct, rooms, doors, splits, checkStates, counters, score, roomTypeDur, classRoles, puzzleSolve, puzzles, chatFormats,
                sbStatPresets, damage, lag, constants, problems, raw, new LinkedHashMap<>());
    }

    /** The tables for a room (in-game name, spaces; U+200B stripped); null when the pack has none. */
    public RoomRun room(String name) { return rooms.get(name.replace("​", "")); }

    /** The chest that finishes the trap room [name], or null. */
    public ChestClear chestClear(String name) { return chestClears.get(name.replace("​", "")); }

    public NamedConstant constant(String name) {
        for (NamedConstant c : constants) if (c.name.equals(name)) return c;
        return null;
    }

    /** A top-level section this class does not model ({@code hud}, {@code loot}, {@code blessings}, ...), as parsed JSON, or null. */
    public JsonObject section(String name) { return JsonKt.obj(raw, name); }

    public static final String FORMAT = "ads-runstate";
    public static final int SUPPORTED_VERSION = 1;

    public static RunStatePack load(Path dir) { return load(dir, new PackReport()); }

    /** Loads {@code runstate.json.gz} from [dir]. Null, with the reason in [report]. Never throws. */
    public static RunStatePack load(Path dir, PackReport report) { return load(PackSource.ofDir(dir), report); }

    public static RunStatePack load(PackSource source) { return load(source, new PackReport()); }

    public static RunStatePack load(PackSource source, PackReport report) {
        Path path = source.file(PackFiles.RUNSTATE);
        if (path == null) { report.error("The run-state tables " + PackFiles.RUNSTATE + " are missing from " + source.missingFrom() + "."); return null; }
        JsonObject root;
        try (InputStream in = Files.newInputStream(path)) {
            root = JsonKt.readGzipJson(in);
        } catch (Exception e) {
            report.error("The run-state tables " + PackFiles.RUNSTATE + " are unreadable" + source.note(PackFiles.RUNSTATE) + ": " + e.getMessage());
            return null;
        }
        return fromJson(root, report);
    }

    public static RunStatePack fromJson(JsonObject root) { return fromJson(root, new PackReport()); }

    private static <T> T or(T v, T def) { return v != null ? v : def; }

    /** A section: parsed when present; a missing or malformed one is reported by name and null. */
    private static <T> T section(JsonObject root, List<String> problems, String name, boolean required, Function<JsonObject, T> parse) {
        JsonObject o = JsonKt.obj(root, name);
        if (o == null) {
            if (required) problems.add("section '" + name + "' is missing");
            return null;
        }
        try {
            T r = parse.apply(o);
            if (r == null) problems.add("section '" + name + "' is malformed");
            return r;
        } catch (Exception e) {
            problems.add("section '" + name + "' is malformed: " + e.getMessage());
            return null;
        }
    }

    public static RunStatePack fromJson(JsonObject root, PackReport report) {
        if (!RoomPackKt.checkHeader(root, FORMAT, SUPPORTED_VERSION, PackFiles.RUNSTATE, report)) return null;
        ArrayList<String> problems = new ArrayList<>();
        double[] pct = null;
        JsonArray pa = JsonKt.arr(root, "pct");
        if (pa != null) {
            double[] d = JsonKt.doubles(pa);
            if (d.length > 0) pct = d;
        }
        if (pct == null) pct = Dist.DEFAULT_PCT;
        final double[] fpct = pct;

        Map<String, RoomRun> rooms = or(section(root, problems, "rooms", true, o -> parseRooms(o, fpct)), new LinkedHashMap<>());
        DoorRules doors = section(root, problems, "doors", true, o -> parseDoors(o, fpct));
        Map<String, Splits> splits = or(section(root, problems, "splits", true, s -> {
            LinkedHashMap<String, Splits> m = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> en : s.entrySet()) {
                JsonObject v = JsonKt.asObj(en.getValue());
                if (v != null) m.put(en.getKey(), parseSplits(v, fpct));
            }
            return m;
        }), new LinkedHashMap<>());
        CheckStates checkStates = section(root, problems, "checkStates", true, o -> parseCheckStates(o, fpct));
        CounterRules counters = section(root, problems, "counters", true,
                o -> new CounterRules(or(JsonKt.int_(o, "totalUnits"), 36), or(JsonKt.bool(o, "excludeEntrance"), true), or(JsonKt.int_(o, "tabRefreshTicks"), 20), o));
        ScoreModel score = section(root, problems, "score", true, RunStatePack::parseScore);
        JsonObject timing = JsonKt.obj(root, "timing");
        if (timing == null) problems.add("section 'timing' is missing");
        Map<String, Dist> roomTypeDur = new LinkedHashMap<>();
        JsonObject rtd = timing != null ? JsonKt.obj(timing, "roomTypeDur") : null;
        if (rtd != null) for (Map.Entry<String, JsonElement> en : rtd.entrySet()) {
            Dist d = Dist.parse(en.getValue(), fpct);
            if (d != null) roomTypeDur.put(en.getKey(), d);
        }
        ClassRoles classRoles = null;
        JsonObject cr = timing != null ? JsonKt.obj(timing, "classRoles") : null;
        if (cr != null) {
            try {
                classRoles = parseClassRoles(cr, fpct);
            } catch (Exception e) {
                problems.add("timing.classRoles is malformed: " + e.getMessage());
                classRoles = null;
            }
        }
        Map<String, Dist> puzzleSolve = new LinkedHashMap<>();
        JsonObject ps = timing != null ? JsonKt.obj(timing, "puzzleSolve") : null;
        if (ps != null) for (Map.Entry<String, JsonElement> en : ps.entrySet()) {
            Dist d = Dist.parse(en.getValue(), fpct);
            if (d != null) puzzleSolve.put(en.getKey(), d);
        }
        PuzzleOdds puzzles = section(root, problems, "puzzles", true, RunStatePack::parsePuzzles);
        List<String> chatFormats = JsonKt.strings(JsonKt.arr(root, "chatFormats"));
        List<SbStatPreset> sbPresets = new ArrayList<>();
        JsonObject sb = JsonKt.obj(root, "sbStats");
        for (JsonObject p : JsonKt.objects(sb != null ? JsonKt.arr(sb, "presets") : null)) {
            String pid = JsonKt.str(p, "id");
            sbPresets.add(new SbStatPreset(or(pid, "?"), or(JsonKt.str(p, "label"), or(pid, "?")), or(JsonKt.int_(p, "floor"), 7), or(JsonKt.int_(p, "maxHp"), 0),
                    or(JsonKt.int_(p, "defense"), 0), or(JsonKt.int_(p, "maxMana"), 0), or(JsonKt.int_(p, "speed"), 0), JsonKt.int_(p, "vitality")));
        }
        DamageRules damage = section(root, problems, "damage", true,
                o -> new DamageRules(or(JsonKt.dbl(o, "trapPctOfMaxHp"), 0.15), JsonKt.str(o, "defenseFormula"), o));
        LagModel lag = section(root, problems, "lag", true, RunStatePack::parseLag);
        Map<String, ChestClear> chestClears = or(section(root, problems, "chestClears", false, o -> parseChestClears(o, fpct)), new LinkedHashMap<>());
        List<NamedConstant> constants = new ArrayList<>();
        JsonObject co = JsonKt.obj(root, "constants");
        for (JsonObject c : JsonKt.objects(co != null ? JsonKt.arr(co, "UNMEASURED") : null)) {
            constants.add(new NamedConstant(or(JsonKt.str(c, "name"), "?"), primitive(c.get("value")), JsonKt.str(c, "why")));
        }
        for (String p : problems) report.warn("Run-state tables: " + p + ". The feature that needs it uses its built-in defaults.");
        Integer version = JsonKt.int_(root, "version");
        return new RunStatePack(version != null ? version : 1, fpct, rooms, doors, splits, checkStates, counters, score, roomTypeDur, classRoles, puzzleSolve,
                puzzles, chatFormats, sbPresets, damage, lag, constants, problems, root, chestClears);
    }

    private static Object primitive(JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) return null;
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isNumber()) return p.getAsDouble();
        if (p.isBoolean()) return p.getAsBoolean();
        return p.getAsString();
    }

    private static Map<String, Dist> dists(JsonObject o, double[] pct) {
        LinkedHashMap<String, Dist> out = new LinkedHashMap<>();
        if (o == null) return out;
        for (Map.Entry<String, JsonElement> en : o.entrySet()) {
            Dist d = Dist.parse(en.getValue(), pct);
            if (d != null) out.put(en.getKey(), d);
        }
        return out;
    }

    private static Integer toIntOrNull(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<Integer, Integer> intMap(JsonObject o) {
        LinkedHashMap<Integer, Integer> out = new LinkedHashMap<>();
        if (o == null) return out;
        for (Map.Entry<String, JsonElement> en : o.entrySet()) {
            Integer kk = toIntOrNull(en.getKey());
            if (kk == null) continue;
            Integer v = JsonKt.asIntOrNull(en.getValue());
            if (v == null) continue;
            out.put(kk, v);
        }
        return out;
    }

    private static Map<String, RoomRun> parseRooms(JsonObject o, double[] pct) {
        LinkedHashMap<String, RoomRun> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> en : o.entrySet()) {
            JsonObject r = JsonKt.asObj(en.getValue());
            if (r == null) continue;
            String key = en.getKey().replace("​", "");
            out.put(key, new RoomRun(
                    key, or(JsonKt.str(r, "type"), "normal"), or(JsonKt.str(r, "shape"), "1x1"), or(JsonKt.int_(r, "tiles"), 1), or(JsonKt.int_(r, "secretsMax"), 0),
                    JsonKt.str(r, "secretsMaxSource"), JsonKt.int_(r, "secretsDb"), JsonKt.int_(r, "cryptsPotential"), JsonKt.str(r, "clear"),
                    Dist.parse(r.get("dur"), pct), dists(JsonKt.obj(r, "byClass"), pct), Dist.parse(r.get("toWhite"), pct), Dist.parse(r.get("toGreen"), pct)));
        }
        return out;
    }

    private static Map<String, ChestClear> parseChestClears(JsonObject o, double[] pct) {
        JsonObject rooms = JsonKt.obj(o, "rooms");
        if (rooms == null) return new LinkedHashMap<>();
        LinkedHashMap<String, ChestClear> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> en : rooms.entrySet()) {
            JsonObject r = JsonKt.asObj(en.getValue());
            if (r == null) continue;
            double[] c = JsonKt.doubles(JsonKt.arr(r, "cell"));
            String key = en.getKey().replace("​", "");
            out.put(key, new ChestClear(key, JsonKt.str(r, "secret"), c.length == 3 ? new Pos((int) c[0], (int) c[1], (int) c[2]) : null,
                    Dist.parse(r.get("afterOpenTicks"), pct), or(JsonKt.int_(r, "visitsWithWhite"), 0)));
        }
        return out;
    }

    private static DoorRules parseDoors(JsonObject o, double[] pct) {
        JsonObject vol = JsonKt.obj(o, "volume");
        JsonObject fall = JsonKt.obj(o, "fall");
        JsonObject ent = JsonKt.obj(o, "entrance");
        JsonObject pick = JsonKt.obj(o, "pickup");
        JsonObject stand = JsonKt.obj(o, "stand");
        JsonObject nk = JsonKt.obj(o, "noKeyMessage");
        JsonObject snd = nk != null ? JsonKt.obj(nk, "sound") : null;
        JsonObject blood = JsonKt.obj(o, "blood");
        JsonObject wpl = JsonKt.obj(o, "witherPerLayout");
        JsonObject pkr = JsonKt.obj(o, "paidKeysPerRun");
        return new DoorRules(
                or(vol != null ? JsonKt.int_(vol, "w") : null, 3), or(vol != null ? JsonKt.int_(vol, "h") : null, 4),
                or(vol != null ? JsonKt.int_(vol, "d") : null, 3), or(vol != null ? JsonKt.int_(vol, "y0") : null, 69),
                or(fall != null ? JsonKt.int_(fall, "barrierTicks") : null, 14), JsonKt.doubles(fall != null ? JsonKt.arr(fall, "barrierTicksQ") : null),
                ent != null ? JsonKt.int_(ent, "startLeadTicks") : null, Dist.parse(ent != null ? ent.get("fallAfterStart1Ticks") : null, pct),
                or(ent != null ? JsonKt.int_(ent, "freeFairyDoorWithinTicks") : null, 2),
                intMap(wpl != null ? JsonKt.obj(wpl, "counts") : null), intMap(pkr != null ? JsonKt.obj(pkr, "counts") : null),
                Dist.parse(o.get("keyObtainToUseTicks"), pct), or(pick != null ? JsonKt.dbl(pick, "instantFrac") : null, 0.63),
                Dist.parse(pick != null ? pick.get("delayedTicks") : null, pct), or(pick != null ? JsonKt.int_(pick, "autoCollectTicks") : null, 200),
                or(stand != null ? JsonKt.dbl(stand, "witherY") : null, 68.531), or(stand != null ? JsonKt.dbl(stand, "bloodY") : null, 67.531),
                or(stand != null ? JsonKt.str(stand, "nameWither") : null, "§8Wither Key"), or(stand != null ? JsonKt.str(stand, "nameBlood") : null, "§cBlood Key"),
                Dist.parse(stand != null ? stand.get("lifetimeTicks") : null, pct),
                or(nk != null ? JsonKt.str(nk, "chat") : null, "You do not have the key for this door!"),
                or(snd != null ? JsonKt.str(snd, "id") : null, "entity.enderman.teleport"), or(snd != null ? JsonKt.dbl(snd, "vol") : null, 8.0),
                or(snd != null ? JsonKt.dbl(snd, "pitch") : null, 0.0),
                Dist.parse(blood != null ? blood.get("keyAfterStartTicks") : null, pct), Dist.parse(blood != null ? blood.get("doorAfterKeyTicks") : null, pct), o);
    }

    private static Splits parseSplits(JsonObject o, double[] pct) { return new Splits(dists(o, pct), o); }

    private static CheckStates parseCheckStates(JsonObject o, double[] pct) {
        LinkedHashMap<String, TypeStates> types = new LinkedHashMap<>();
        JsonObject to = JsonKt.obj(o, "types");
        if (to != null) for (Map.Entry<String, JsonElement> en : to.entrySet()) {
            JsonObject t = JsonKt.asObj(en.getValue());
            if (t == null) continue;
            types.put(en.getKey(), new TypeStates(intMapStr(JsonKt.obj(t, "paths")), dists(JsonKt.obj(t, "ticks"), pct)));
        }
        return new CheckStates(types, or(JsonKt.bool(o, "greenNeedsAllSecrets"), true), or(JsonKt.bool(o, "zeroSecretGoesStraightToGreen"), true), o);
    }

    private static Map<String, Integer> intMapStr(JsonObject o) {
        LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
        if (o == null) return out;
        for (Map.Entry<String, JsonElement> en : o.entrySet()) {
            Integer v = JsonKt.asIntOrNull(en.getValue());
            if (v != null) out.put(en.getKey(), v);
        }
        return out;
    }

    private static ScoreModel parseScore(JsonObject o) {
        JsonObject terms = JsonKt.obj(o, "terms");
        JsonObject secret = terms != null ? JsonKt.obj(terms, "secret") : null;
        JsonObject rooms = terms != null ? JsonKt.obj(terms, "rooms") : null;
        JsonObject bonus = terms != null ? JsonKt.obj(terms, "bonus") : null;
        JsonObject speed = terms != null ? JsonKt.obj(terms, "speed") : null;
        JsonObject deaths = terms != null ? JsonKt.obj(terms, "deaths") : null;
        JsonObject puzzles = terms != null ? JsonKt.obj(terms, "puzzles") : null;
        JsonObject crypts = bonus != null ? JsonKt.obj(bonus, "crypts") : null;
        JsonObject mimic = bonus != null ? JsonKt.obj(bonus, "mimic") : null;
        JsonObject prince = bonus != null ? JsonKt.obj(bonus, "prince") : null;
        JsonObject bat = bonus != null ? JsonKt.obj(bonus, "bat") : null;
        List<Pair<Double, Double>> pointsSec = new ArrayList<>();
        JsonArray psa = speed != null ? JsonKt.arr(speed, "pointsSec") : null;
        if (psa != null) {
            for (JsonElement p : psa) {
                JsonArray a = JsonKt.asArr(p);
                if (a != null && a.size() >= 2) {
                    Double x = JsonKt.asDoubleOrNull(a.get(0));
                    if (x == null) continue;
                    Double y = JsonKt.asDoubleOrNull(a.get(1));
                    if (y == null) continue;
                    pointsSec.add(Pair.of(x, y));
                }
            }
        }
        return new ScoreModel(
                or(JsonKt.int_(o, "periodTicks"), 200), or(JsonKt.int_(o, "firstUpdateTick"), 200), JsonKt.str(o, "endScreen"),
                or(secret != null ? JsonKt.dbl(secret, "maxSecretPoints") : null, 40.0), or(secret != null ? JsonKt.int_(secret, "secretsPctDecimals") : null, 1),
                JsonKt.doubles(rooms != null ? JsonKt.arr(rooms, "table") : null), rooms != null ? JsonKt.str(rooms, "interp") : null,
                rooms != null ? JsonKt.str(rooms, "rounding") : null,
                deaths != null ? JsonKt.str(deaths, "perDeathFormula") : null, puzzles != null ? JsonKt.dbl(puzzles, "perUnsolved") : null,
                or(crypts != null ? JsonKt.dbl(crypts, "w") : null, 1.0), or(crypts != null ? JsonKt.int_(crypts, "cap") : null, 5),
                or(mimic != null ? JsonKt.dbl(mimic, "w") : null, 2.0), or(prince != null ? JsonKt.dbl(prince, "w") : null, 1.0),
                or(bat != null ? JsonKt.dbl(bat, "w") : null, 1.0),
                or(bonus != null ? JsonKt.bool(bonus, "weightsFitted") : null, false),
                pointsSec,
                or(speed != null ? JsonKt.bool(speed, "fitted") : null, false), or(terms != null ? JsonKt.dbl(terms, "constant") : null, 0.0), o,
                or(secret != null ? JsonKt.dbl(secret, "weight") : null, 1.0), or(JsonKt.int_(o, "clockOriginTicksAfterT0"), 0), JsonKt.str(o, "evaluation"));
    }

    private static ClassRoles parseClassRoles(JsonObject o, double[] pct) {
        HashMap<String, ClassRole> classes = new HashMap<>();
        for (String c : List.of("HEALER", "MAGE", "BERSERK", "ARCHER", "TANK")) {
            JsonObject r = JsonKt.obj(o, c);
            if (r == null) continue;
            classes.put(c, new ClassRole(or(JsonKt.dbl(r, "roomsOwnedMean"), 0.0), or(JsonKt.dbl(r, "puzzlesOwnedMean"), 0.0), or(JsonKt.dbl(r, "doorOpenerFrac"), 0.0),
                    Dist.parse(r.get("distanceBlocksQ"), pct), JsonKt.dbl(r, "movingSpeedBpt")));
        }
        return new ClassRoles(or(JsonKt.int_(o, "n"), 0), classes, JsonKt.doubles(JsonKt.arr(o, "ownedRoomsSortedMean")), JsonKt.dbl(o, "standingFrac"),
                JsonKt.dbl(o, "firstEntrantTopLeaderFrac"), o);
    }

    private static PuzzleOdds parsePuzzles(JsonObject o) {
        JsonObject odds = JsonKt.obj(o, "countOdds");
        List<PuzzleOdds.PuzzleEntry> pool = new ArrayList<>();
        for (JsonObject p : JsonKt.objects(JsonKt.arr(o, "pool"))) {
            String code = JsonKt.str(p, "code");
            if (code == null) continue;
            pool.add(new PuzzleOdds.PuzzleEntry(code, JsonKt.strings(JsonKt.arr(p, "rooms")), or(JsonKt.dbl(p, "mapShare"), 0.0), or(JsonKt.bool(p, "exclusivePair"), false)));
        }
        return new PuzzleOdds(or(odds != null ? JsonKt.int_(odds, "n") : null, 0), intMap(odds != null ? JsonKt.obj(odds, "counts") : null), pool, o);
    }

    private static LagModel parseLag(JsonObject o) {
        LinkedHashMap<String, LagTier> tiers = new LinkedHashMap<>();
        JsonObject to = JsonKt.obj(o, "tiers");
        if (to != null) for (Map.Entry<String, JsonElement> en : to.entrySet()) {
            JsonObject t = JsonKt.asObj(en.getValue());
            if (t == null) continue;
            tiers.put(en.getKey(), new LagTier(or(JsonKt.dbl(t, "good"), 0.0), or(JsonKt.dbl(t, "typical"), 0.0), or(JsonKt.dbl(t, "bad"), 0.0),
                    JsonKt.dbl(t, "mean"), JsonKt.dbl(t, "slotOver200"), JsonKt.dbl(t, "slotMultiplier")));
        }
        JsonObject run = JsonKt.obj(o, "runLevelModel");
        JsonObject fr = JsonKt.obj(o, "freezes");
        JsonObject tick = fr != null ? JsonKt.obj(fr, "tickIntervalMsQ") : null;
        JsonObject catchup = fr != null ? JsonKt.obj(fr, "catchup") : null;
        List<Pair<Double, Double>> exceed = new ArrayList<>();
        for (JsonObject e : JsonKt.objects(fr != null ? JsonKt.arr(fr, "exceedPerHour") : null)) {
            Double gap = JsonKt.dbl(e, "gapMs");
            if (gap == null) continue;
            Double ph = JsonKt.dbl(e, "perHour");
            if (ph == null) continue;
            exceed.add(Pair.of(gap, ph));
        }
        JsonObject rtt = JsonKt.obj(o, "rttMs");
        return new LagModel(
                or(JsonKt.dbl(o, "typicalLossPerMin"), 30.3), tiers,
                run != null ? JsonKt.dbl(run, "median") : null, run != null ? JsonKt.dbl(run, "sigma") : null,
                run != null ? JsonKt.dbl(run, "p10") : null, run != null ? JsonKt.dbl(run, "p90") : null,
                exceed,
                JsonKt.doubles(tick != null ? JsonKt.arr(tick, "pct") : null), JsonKt.doubles(tick != null ? JsonKt.arr(tick, "q") : null),
                catchup != null ? JsonKt.dbl(catchup, "recoverFracWithin5s") : null, catchup != null ? JsonKt.dbl(catchup, "burstRateTicksPerSec") : null,
                rtt != null ? JsonKt.dbl(rtt, "median") : null, o);
    }
}
