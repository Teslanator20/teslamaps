// Ported from ADM's p3sim/run/RunStateJson.kt
package com.teslamaps.adm.sim.run;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The savepoint form of RunState. Pure. Reading is forgiving: missing keeps, unknown ignored, out-of-plan skipped. */
public final class RunStateJson {
    private RunStateJson() {}

    public static JsonObject write(RunState s) {
        JsonObject o = new JsonObject();
        JsonArray rooms = new JsonArray();
        for (RoomRecord r : s.rooms) {
            JsonObject j = new JsonObject();
            j.addProperty("check", r.check.name());
            nullable(j, "tDiscovered", r.tDiscovered); nullable(j, "tWhite", r.tWhite); nullable(j, "tGreen", r.tGreen); nullable(j, "tRed", r.tRed);
            j.addProperty("secretsFound", r.secretsFound);
            j.addProperty("mobsRequired", r.mobsRequired); j.addProperty("mobsRequiredAlive", r.mobsRequiredAlive);
            j.add("occupants", uuids(r.occupants)); j.add("visitors", uuids(r.visitors));
            rooms.add(j);
        }
        o.add("rooms", rooms);
        JsonArray doors = new JsonArray();
        for (DoorRecord d : s.doors) {
            JsonObject j = new JsonObject();
            j.addProperty("phase", d.phase.name()); nullable(j, "openedAt", d.openedAt);
            if (d.openedBy != null) j.addProperty("openedBy", d.openedBy.toString());
            doors.add(j);
        }
        o.add("doors", doors);
        JsonObject keys = new JsonObject();
        KeyState k = s.keys;
        keys.addProperty("witherHeld", k.witherHeld); keys.addProperty("bloodHeld", k.bloodHeld);
        keys.addProperty("witherObtained", k.witherObtained); keys.addProperty("witherUsed", k.witherUsed);
        keys.addProperty("bloodObtained", k.bloodObtained); keys.addProperty("bloodUsed", k.bloodUsed);
        o.add("keys", keys);
        JsonObject counters = new JsonObject();
        counters.addProperty("opened", s.counters.opened); counters.addProperty("completed", s.counters.completed);
        o.add("counters", counters);
        JsonObject secrets = new JsonObject();
        SecretLedger c = s.secrets;
        secrets.addProperty("teamFound", c.teamFound); secrets.addProperty("teamMax", c.teamMax); secrets.addProperty("crypts", c.crypts);
        secrets.addProperty("mimicKilled", c.mimicKilled); secrets.addProperty("princeKilled", c.princeKilled); secrets.addProperty("batKilled", c.batKilled);
        secrets.addProperty("witherEssence", c.witherEssence);
        JsonObject byPlayer = new JsonObject();
        for (Map.Entry<UUID, Integer> e : c.byPlayer.entrySet()) byPlayer.addProperty(e.getKey().toString(), e.getValue());
        secrets.add("byPlayer", byPlayer);
        JsonObject blessings = new JsonObject();
        for (Map.Entry<String, Integer> e : c.blessings.entrySet()) blessings.addProperty(e.getKey(), e.getValue());
        secrets.add("blessings", blessings);
        o.add("secrets", secrets);
        JsonObject deaths = new JsonObject();
        deaths.addProperty("teamDeaths", s.deaths.teamDeaths);
        JsonArray log = new JsonArray();
        for (Pair<Integer, UUID> p : s.deaths.log) {
            JsonObject e = new JsonObject();
            e.addProperty("t", p.first); e.addProperty("player", p.second.toString());
            log.add(e);
        }
        deaths.add("log", log);
        o.add("deaths", deaths);
        JsonObject score = new JsonObject();
        ScoreState sc = s.score;
        score.addProperty("score", sc.score); score.addProperty("clearedPct", sc.clearedPct); score.addProperty("lastUpdateT", sc.lastUpdateT);
        JsonArray history = new JsonArray();
        for (Pair<Integer, Integer> p : sc.history) {
            JsonArray e = new JsonArray();
            e.add(p.first); e.add(p.second);
            history.add(e);
        }
        score.add("history", history);
        o.add("score", score);
        JsonArray puzzles = new JsonArray();
        for (PuzzleRecord p : s.puzzles) {
            JsonObject j = new JsonObject();
            j.addProperty("room", p.roomIndex); j.addProperty("kind", p.kind); j.addProperty("tabName", p.tabName);
            j.addProperty("status", p.status.name()); j.addProperty("revealed", p.revealed); j.addProperty("failures", p.failures);
            if (p.solvedBy != null) j.addProperty("solvedBy", p.solvedBy.toString());
            puzzles.add(j);
        }
        o.add("puzzles", puzzles);
        JsonArray players = new JsonArray();
        for (PlayerRecord p : s.players.values()) {
            JsonObject j = new JsonObject();
            j.addProperty("uuid", p.uuid.toString()); j.addProperty("name", p.name);
            if (p.dungeonClass != null) j.addProperty("class", p.dungeonClass);
            j.addProperty("bot", p.isBot);
            j.addProperty("hp", p.hpPercent); j.addProperty("ghost", p.ghost); j.addProperty("deaths", p.deaths); j.addProperty("secrets", p.secretsFound);
            if (p.roomIndex != null) j.addProperty("room", p.roomIndex);
            j.addProperty("x", p.x); j.addProperty("y", p.y); j.addProperty("z", p.z);
            j.addProperty("lastHitT", p.lastHitT); nullable(j, "reviveAtT", p.reviveAtT);
            players.add(j);
        }
        o.add("players", players);
        JsonObject milestones = new JsonObject();
        Milestones m = s.milestones;
        nullable(milestones, "bloodKeyT", m.bloodKeyT); nullable(milestones, "bloodOpenT", m.bloodOpenT); nullable(milestones, "watcherDoneT", m.watcherDoneT);
        nullable(milestones, "bossEntryT", m.bossEntryT); nullable(milestones, "bossDefeatedT", m.bossDefeatedT); nullable(milestones, "endT", m.endT);
        o.add("milestones", milestones);
        return o;
    }

    public static void read(RunState s, JsonObject o) {
        JsonArray rooms = arr(o, "rooms");
        if (rooms != null) {
            for (int i = 0; i < rooms.size(); i++) {
                RoomRecord r = s.room(i);
                if (r == null) continue;
                JsonObject j = asObjOrNull(rooms.get(i));
                if (j == null) continue;
                String n = str(j, "check");
                if (n != null) for (CheckMark c : CheckMark.values()) if (c.name().equals(n)) { r.check = c; break; }
                r.tDiscovered = intOrNull(j, "tDiscovered"); r.tWhite = intOrNull(j, "tWhite"); r.tGreen = intOrNull(j, "tGreen"); r.tRed = intOrNull(j, "tRed");
                Integer v;
                if ((v = intVal(j, "secretsFound")) != null) r.secretsFound = v;
                if ((v = intVal(j, "mobsRequired")) != null) r.mobsRequired = v;
                if ((v = intVal(j, "mobsRequiredAlive")) != null) r.mobsRequiredAlive = v;
                r.occupants.clear(); r.occupants.addAll(readUuids(arr(j, "occupants")));
                r.visitors.clear(); r.visitors.addAll(readUuids(arr(j, "visitors")));
            }
        }
        JsonArray doors = arr(o, "doors");
        if (doors != null) {
            for (int i = 0; i < doors.size(); i++) {
                DoorRecord d = s.door(i);
                if (d == null) continue;
                JsonObject j = asObjOrNull(doors.get(i));
                if (j == null) continue;
                String n = str(j, "phase");
                if (n != null) for (DoorPhase p : DoorPhase.values()) if (p.name().equals(n)) { d.phase = p; break; }
                d.openedAt = intOrNull(j, "openedAt");
                String by = str(j, "openedBy");
                d.openedBy = by != null ? uuidOrNull(by) : null;
            }
        }
        JsonObject keys = obj(o, "keys");
        if (keys != null) {
            KeyState k = s.keys;
            Integer v; Boolean b;
            if ((v = intVal(keys, "witherHeld")) != null) k.witherHeld = v;
            if ((b = bool(keys, "bloodHeld")) != null) k.bloodHeld = b;
            if ((v = intVal(keys, "witherObtained")) != null) k.witherObtained = v;
            if ((v = intVal(keys, "witherUsed")) != null) k.witherUsed = v;
            if ((b = bool(keys, "bloodObtained")) != null) k.bloodObtained = b;
            if ((b = bool(keys, "bloodUsed")) != null) k.bloodUsed = b;
        }
        JsonObject counters = obj(o, "counters");
        if (counters != null) {
            Integer v;
            if ((v = intVal(counters, "opened")) != null) s.counters.opened = v;
            if ((v = intVal(counters, "completed")) != null) s.counters.completed = v;
        }
        JsonObject secrets = obj(o, "secrets");
        if (secrets != null) {
            SecretLedger c = s.secrets;
            Integer v; Boolean b;
            if ((v = intVal(secrets, "teamFound")) != null) c.teamFound = v;
            if ((v = intVal(secrets, "teamMax")) != null) c.teamMax = v;
            if ((v = intVal(secrets, "crypts")) != null) c.crypts = v;
            if ((b = bool(secrets, "mimicKilled")) != null) c.mimicKilled = b;
            if ((b = bool(secrets, "princeKilled")) != null) c.princeKilled = b;
            if ((b = bool(secrets, "batKilled")) != null) c.batKilled = b;
            if ((v = intVal(secrets, "witherEssence")) != null) c.witherEssence = v;
            c.byPlayer.clear();
            JsonObject byPlayer = obj(secrets, "byPlayer");
            if (byPlayer != null) {
                for (Map.Entry<String, JsonElement> e : byPlayer.entrySet()) {
                    UUID u = uuidOrNull(e.getKey());
                    if (u == null) continue;
                    Integer n = asIntOrNull(e.getValue());
                    if (n != null) c.byPlayer.put(u, n);
                }
            }
            c.blessings.clear();
            JsonObject blessings = obj(secrets, "blessings");
            if (blessings != null) {
                for (Map.Entry<String, JsonElement> e : blessings.entrySet()) {
                    Integer n = asIntOrNull(e.getValue());
                    if (n != null) c.blessings.put(e.getKey(), n);
                }
            }
        }
        JsonObject deaths = obj(o, "deaths");
        if (deaths != null) {
            Integer v;
            if ((v = intVal(deaths, "teamDeaths")) != null) s.deaths.teamDeaths = v;
            s.deaths.log.clear();
            JsonArray log = arr(deaths, "log");
            if (log != null) {
                for (JsonElement e : log) {
                    JsonObject x = asObjOrNull(e);
                    if (x == null) continue;
                    Integer t = intVal(x, "t");
                    if (t == null) continue;
                    String ps = str(x, "player");
                    UUID u = ps != null ? uuidOrNull(ps) : null;
                    if (u == null) continue;
                    s.deaths.log.add(Pair.of(t, u));
                }
            }
        }
        JsonObject score = obj(o, "score");
        if (score != null) {
            ScoreState c = s.score;
            Integer v;
            if ((v = intVal(score, "score")) != null) c.score = v;
            if ((v = intVal(score, "clearedPct")) != null) c.clearedPct = v;
            if ((v = intVal(score, "lastUpdateT")) != null) c.lastUpdateT = v;
            c.history.clear();
            JsonArray history = arr(score, "history");
            if (history != null) {
                for (JsonElement e : history) {
                    if (!e.isJsonArray()) continue;
                    JsonArray a = e.getAsJsonArray();
                    if (a.size() >= 2) c.history.add(Pair.of(a.get(0).getAsInt(), a.get(1).getAsInt()));
                }
            }
        }
        JsonArray puzzles = arr(o, "puzzles");
        if (puzzles != null) readPuzzles(s, puzzles);
        JsonArray players = arr(o, "players");
        if (players != null) readPlayers(s, players);
        JsonObject milestones = obj(o, "milestones");
        if (milestones != null) {
            Milestones m = s.milestones;
            m.bloodKeyT = intOrNull(milestones, "bloodKeyT"); m.bloodOpenT = intOrNull(milestones, "bloodOpenT"); m.watcherDoneT = intOrNull(milestones, "watcherDoneT");
            m.bossEntryT = intOrNull(milestones, "bossEntryT"); m.bossDefeatedT = intOrNull(milestones, "bossDefeatedT"); m.endT = intOrNull(milestones, "endT");
        }
    }

    /** A record whose room and kind match is updated in place, any other is rebuilt. */
    private static void readPuzzles(RunState s, JsonArray a) {
        List<PuzzleRecord> out = new ArrayList<>();
        for (int i = 0; i < a.size(); i++) {
            JsonObject j = asObjOrNull(a.get(i));
            if (j == null) continue;
            Integer room = intVal(j, "room");
            if (room == null) continue;
            String kind = str(j, "kind");
            if (kind == null) continue;
            String tabRaw = str(j, "tabName");
            String tab = tabRaw != null ? tabRaw : kind;
            PuzzleRecord old = i < s.puzzles.size() ? s.puzzles.get(i) : null;
            PuzzleRecord rec = old != null && old.roomIndex == room && old.kind.equals(kind) && old.tabName.equals(tab) ? old : new PuzzleRecord(room, kind, tab);
            String n = str(j, "status");
            if (n != null) for (PuzzleStatus st : PuzzleStatus.values()) if (st.name().equals(n)) { rec.status = st; break; }
            Boolean b; Integer v;
            if ((b = bool(j, "revealed")) != null) rec.revealed = b;
            if ((v = intVal(j, "failures")) != null) rec.failures = v;
            String by = str(j, "solvedBy");
            rec.solvedBy = by != null ? uuidOrNull(by) : null;
            out.add(rec);
        }
        s.puzzles.clear(); s.puzzles.addAll(out);
    }

    private static void readPlayers(RunState s, JsonArray a) {
        LinkedHashMap<UUID, PlayerRecord> keep = new LinkedHashMap<>();
        for (JsonElement e : a) {
            JsonObject j = asObjOrNull(e);
            if (j == null) continue;
            String us = str(j, "uuid");
            UUID u = us != null ? uuidOrNull(us) : null;
            if (u == null) continue;
            PlayerRecord rec = s.players.get(u);
            if (rec == null) {
                String name = str(j, "name");
                Boolean bot = bool(j, "bot");
                rec = new PlayerRecord(u, name != null ? name : "?", str(j, "class"), bot != null ? bot : false);
            }
            Double d; Boolean b; Integer v;
            if ((d = dbl(j, "hp")) != null) rec.hpPercent = d;
            if ((b = bool(j, "ghost")) != null) rec.ghost = b;
            if ((v = intVal(j, "deaths")) != null) rec.deaths = v;
            if ((v = intVal(j, "secrets")) != null) rec.secretsFound = v;
            rec.roomIndex = intOrNull(j, "room");
            if ((d = dbl(j, "x")) != null) rec.x = d;
            if ((d = dbl(j, "y")) != null) rec.y = d;
            if ((d = dbl(j, "z")) != null) rec.z = d;
            if ((v = intVal(j, "lastHitT")) != null) rec.lastHitT = v;
            rec.reviveAtT = intOrNull(j, "reviveAtT");
            keep.put(u, rec);
        }
        s.players.clear(); s.players.putAll(keep);
    }

    // ---- small JSON helpers ----

    private static void nullable(JsonObject o, String k, Integer v) { if (v != null) o.addProperty(k, v); }

    private static JsonArray uuids(Collection<UUID> c) {
        JsonArray a = new JsonArray();
        for (UUID u : c) a.add(u.toString());
        return a;
    }

    private static List<UUID> readUuids(JsonArray a) {
        List<UUID> out = new ArrayList<>();
        if (a == null) return out;
        for (JsonElement e : a) {
            if (!e.isJsonPrimitive()) continue;
            UUID u = uuidOrNull(e.getAsString());
            if (u != null) out.add(u);
        }
        return out;
    }

    private static UUID uuidOrNull(String s) {
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    private static JsonObject asObjOrNull(JsonElement e) { return e.isJsonObject() ? e.getAsJsonObject() : null; }

    private static Integer asIntOrNull(JsonElement e) {
        return e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : null;
    }

    private static JsonArray arr(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
    }

    private static JsonObject obj(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static Integer intVal(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null ? asIntOrNull(e) : null;
    }

    private static Integer intOrNull(JsonObject o, String k) { return intVal(o, k); }

    private static Double dbl(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : null;
    }

    private static Boolean bool(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : null;
    }
}
