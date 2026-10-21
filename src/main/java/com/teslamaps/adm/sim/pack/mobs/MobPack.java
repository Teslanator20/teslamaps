// Ported from ADM's p3sim/pack/mobs/MobPack.kt
package com.teslamaps.adm.sim.pack.mobs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.pack.JsonKt;
import com.teslamaps.adm.sim.pack.PackFiles;
import com.teslamaps.adm.sim.pack.PackFormatException;
import com.teslamaps.adm.sim.pack.PackReport;
import com.teslamaps.adm.sim.pack.PackSource;
import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.pack.rooms.RoomPackKt;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/** The loaded mob tables. */
public class MobPack {
    public final int version;
    public final List<MobArchetype> archetypes;
    public final List<MobModifier> modifiers;
    public final Map<String, HpScaleTable> hpScale;
    public final List<RoomMobs> rooms;
    public final List<Miniboss> minibosses;
    public final PlayerDamageTable playerDamage;
    public final List<String> gaps;

    private final Map<String, MobArchetype> byArchetypeId;
    private final Map<String, MobArchetype> byArchetypeName;
    private final Map<String, RoomMobs> byRoomName;
    private final Map<String, RoomMobs> byNormRoomName;
    private final Map<String, RoomMobs> byRoomAlias;

    public MobPack(int version, List<MobArchetype> archetypes, List<MobModifier> modifiers, Map<String, HpScaleTable> hpScale, List<RoomMobs> rooms,
                   List<Miniboss> minibosses, PlayerDamageTable playerDamage, List<String> gaps) {
        this.version = version;
        this.archetypes = archetypes;
        this.modifiers = modifiers;
        this.hpScale = hpScale;
        this.rooms = rooms;
        this.minibosses = minibosses;
        this.playerDamage = playerDamage;
        this.gaps = gaps;
        LinkedHashMap<String, MobArchetype> bid = new LinkedHashMap<>();
        LinkedHashMap<String, MobArchetype> bname = new LinkedHashMap<>();
        for (MobArchetype a : archetypes) { bid.put(a.id, a); bname.put(a.name, a); }
        this.byArchetypeId = bid;
        this.byArchetypeName = bname;
        LinkedHashMap<String, RoomMobs> brn = new LinkedHashMap<>();
        LinkedHashMap<String, RoomMobs> bnrn = new LinkedHashMap<>();
        LinkedHashMap<String, RoomMobs> bra = new LinkedHashMap<>();
        for (RoomMobs r : rooms) { brn.put(r.name, r); bnrn.put(norm(r.name), r); }
        for (RoomMobs r : rooms) for (String a : r.aliases) bra.putIfAbsent(a.replace("​", ""), r);
        this.byRoomName = brn;
        this.byNormRoomName = bnrn;
        this.byRoomAlias = bra;
    }

    public MobArchetype archetype(int index) { return index >= 0 && index < archetypes.size() ? archetypes.get(index) : null; }
    public MobArchetype archetypeById(String id) { return byArchetypeId.get(id); }
    public MobArchetype byName(String name) { return byArchetypeName.get(name); }

    public RoomMobs room(String name) { return room(name, null); }

    /**
     * The spawn data of a room by name: exact (after stripping U+200B), then through [aliasOf], then the normalised key.
     * Null when the room has none.
     */
    public RoomMobs room(String name, Function<String, String> aliasOf) {
        String n = name.replace("​", "");
        RoomMobs r = byRoomName.get(n);
        if (r != null) return r;
        r = byRoomAlias.get(n);
        if (r != null) return r;
        if (aliasOf != null) {
            String a = aliasOf.apply(n);
            if (a != null) {
                r = byRoomName.get(a);
                if (r != null) return r;
                r = byRoomAlias.get(a);
                if (r != null) return r;
                r = byNormRoomName.get(norm(a));
                if (r != null) return r;
            }
        }
        return byNormRoomName.get(norm(n));
    }

    public MobModifier modifier(String id) {
        for (MobModifier m : modifiers) if (m.id.equals(id)) return m;
        return null;
    }

    public HpScaleTable hpScaleFor(String floor) { return hpScale.get(floor); }

    public List<Miniboss> minibossesOf(String room) {
        List<Miniboss> out = new ArrayList<>();
        for (Miniboss m : minibosses) if (m.room.equals(room)) out.add(m);
        return out;
    }

    public static final String FORMAT = "ads-mobs";
    public static final int SUPPORTED_VERSION = 1;

    /** Fallback join key. */
    public static String norm(String n) { return n.replace("​", "").replace(" ", "_").replace("-", "_"); }

    public static MobPack load(Path dir) { return load(dir, new PackReport()); }

    /** Loads {@code mobs.json.gz} from [dir]. Null, with the reason in [report]. Never throws. */
    public static MobPack load(Path dir, PackReport report) { return load(PackSource.ofDir(dir), report); }

    public static MobPack load(PackSource source) { return load(source, new PackReport()); }

    public static MobPack load(PackSource source, PackReport report) {
        Path path = source.file(PackFiles.MOBS);
        if (path == null) { report.error("The mob tables " + PackFiles.MOBS + " are missing from " + source.missingFrom() + "."); return null; }
        JsonObject root;
        try (InputStream in = Files.newInputStream(path)) {
            root = JsonKt.readGzipJson(in);
        } catch (Exception e) {
            report.error("The mob tables " + PackFiles.MOBS + " are unreadable" + source.note(PackFiles.MOBS) + ": " + e.getMessage());
            return null;
        }
        return fromJson(root, report);
    }

    public static MobPack fromJson(JsonObject root) { return fromJson(root, new PackReport()); }

    private static <T> T or(T v, T def) { return v != null ? v : def; }

    public static MobPack fromJson(JsonObject root, PackReport report) {
        if (!RoomPackKt.checkHeader(root, FORMAT, SUPPORTED_VERSION, PackFiles.MOBS, report)) return null;
        ArrayList<MobArchetype> archetypes = new ArrayList<>();
        List<JsonObject> archObjs = JsonKt.objects(JsonKt.arr(root, "archetypes"));
        for (int i = 0; i < archObjs.size(); i++) {
            JsonObject a = archObjs.get(i);
            try {
                archetypes.add(parseArchetype(i, a));
            } catch (Exception e) {
                // Keep the index space intact: rooms refer to archetypes by position.
                report.warn("Mob archetype #" + i + " was skipped (" + or(JsonKt.str(a, "id"), "no id") + "): " + e.getMessage());
                archetypes.add(new MobArchetype(i, or(JsonKt.str(a, "id"), "broken_" + i), or(JsonKt.str(a, "name"), "?"), "special", "minecraft:zombie",
                        false, null, null, null, new LinkedHashMap<>(), false, null, "static", null, null, new LinkedHashMap<>()));
            }
        }
        List<MobModifier> modifiers = new ArrayList<>();
        for (JsonObject m : JsonKt.objects(JsonKt.arr(root, "modifiers"))) {
            modifiers.add(new MobModifier(or(JsonKt.str(m, "id"), ""), doubleMap(JsonKt.obj(m, "weight")), or(JsonKt.dbl(m, "hpFactor"), 1.0),
                    JsonKt.str(m, "nameColor"), JsonKt.str(m, "hpColor"), or(JsonKt.bool(m, "headReset"), true), JsonKt.str(m, "hpColorHurt"),
                    JsonKt.dbl(m, "hpThreshold")));
        }
        HashMap<String, HpScaleTable> hpScale = new HashMap<>();
        JsonObject hs = JsonKt.obj(root, "hpScale");
        if (hs != null) {
            for (Map.Entry<String, JsonElement> en : hs.entrySet()) {
                JsonObject o = JsonKt.asObj(en.getValue());
                if (o == null) continue;
                double[] values = JsonKt.doubles(JsonKt.arr(o, "values"));
                int[] counts = JsonKt.ints(JsonKt.arr(o, "counts"));
                if (values.length > 0 && values.length == counts.length) {
                    Integer n = JsonKt.int_(o, "n");
                    int sum = 0;
                    for (int c : counts) sum += c;
                    hpScale.put(en.getKey(), new HpScaleTable(n != null ? n : sum, values, counts));
                }
            }
        }
        ArrayList<RoomMobs> rooms = new ArrayList<>();
        for (JsonObject r : JsonKt.objects(JsonKt.arr(root, "rooms"))) {
            try {
                rooms.add(parseRoom(r, archetypes.size()));
            } catch (Exception e) {
                report.warn("Mob data of room " + or(JsonKt.str(r, "name"), "?") + " was skipped: " + e.getMessage());
            }
        }
        List<Miniboss> minibosses = new ArrayList<>();
        for (JsonObject m : JsonKt.objects(JsonKt.arr(root, "minibosses"))) {
            String room = JsonKt.str(m, "room");
            if (room == null) continue;
            Integer arch = JsonKt.int_(m, "arch");
            if (arch == null) continue;
            Pos cell = JsonKt.asPos(m.get("cell"));
            String src = JsonKt.str(m, "src");
            Double hpFactor = JsonKt.dbl(m, "hpFactor");
            JsonArray l = JsonKt.arr(m, "loop");
            List<Pos> loop = null;
            if (l != null) {
                loop = new ArrayList<>();
                for (JsonElement e : l) { Pos p = JsonKt.asPos(e); if (p != null) loop.add(p); }
            }
            minibosses.add(new Miniboss(room, arch, cell, src, hpFactor, loop));
        }
        if (archetypes.isEmpty()) report.warn("The mob tables have no archetypes.");
        JsonObject pd = JsonKt.obj(root, "playerDamage");
        Integer version = JsonKt.int_(root, "version");
        return new MobPack(version != null ? version : 1, archetypes, modifiers, hpScale, rooms, minibosses, pd != null ? parseDamage(pd) : null,
                JsonKt.strings(JsonKt.arr(root, "gaps")));
    }

    private static Map<String, Double> doubleMap(JsonObject o) {
        LinkedHashMap<String, Double> out = new LinkedHashMap<>();
        if (o == null) return out;
        for (Map.Entry<String, JsonElement> en : o.entrySet()) out.put(en.getKey(), JsonKt.asDoubleOrNull(en.getValue()));
        return out;
    }

    private static MobArchetype parseArchetype(int index, JsonObject a) {
        String id = JsonKt.str(a, "id");
        if (id == null) throw new PackFormatException("no id");
        JsonObject t = JsonKt.obj(a, "tag");
        MobTag tag = t == null ? null : new MobTag(or(JsonKt.int_(t, "idOffset"), 1), or(JsonKt.dbl(t, "dy"), 2.09), or(JsonKt.str(t, "head"), ""),
                or(JsonKt.str(t, "nameColor"), "§c"), or(JsonKt.bool(t, "bold"), false), or(JsonKt.bool(t, "modSplit"), false));
        JsonObject s = JsonKt.obj(a, "skin");
        MobSkin skin = s != null ? parseSkin(s, true) : null;
        JsonObject e = JsonKt.obj(a, "equipment");
        MobEquipment equipment = e != null ? new MobEquipment(JsonKt.str(e, "mainhand"), JsonKt.str(e, "mainhandName"), JsonKt.str(e, "feet")) : null;
        LinkedHashMap<String, Long> hp = new LinkedHashMap<>();
        JsonObject hpo = JsonKt.obj(a, "hp");
        if (hpo != null) for (Map.Entry<String, JsonElement> en : hpo.entrySet()) {
            Long v = JsonKt.asLongOrNull(en.getValue());
            if (v != null) hp.put(en.getKey(), v);
        }
        JsonObject w = JsonKt.obj(a, "wake");
        MobWake wake = w != null ? new MobWake(or(JsonKt.dbl(w, "radius"), 5.0), or(JsonKt.str(w, "trigger"), "player-body")) : null;
        LinkedHashMap<String, Double> modifierWeights = null;
        JsonObject mw = JsonKt.obj(a, "modifierWeights");
        if (mw != null) {
            modifierWeights = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> en : mw.entrySet()) {
                Double v = JsonKt.asDoubleOrNull(en.getValue());
                if (v != null) modifierWeights.put(en.getKey(), v);
            }
        }
        LinkedHashMap<String, Integer> seen = new LinkedHashMap<>();
        JsonObject so = JsonKt.obj(a, "seen");
        if (so != null) for (Map.Entry<String, JsonElement> en : so.entrySet()) {
            Integer v = JsonKt.asIntOrNull(en.getValue());
            if (v != null) seen.put(en.getKey(), v);
        }
        return new MobArchetype(index, id, or(JsonKt.str(a, "name"), id), or(JsonKt.str(a, "cls"), "clear"), or(JsonKt.str(a, "entity"), "minecraft:zombie"),
                or(JsonKt.bool(a, "disguise"), false), skin, JsonKt.str(a, "bodyName"), equipment, hp, or(JsonKt.bool(a, "star"), true), tag,
                or(JsonKt.str(a, "movement"), "static"), wake, modifierWeights, seen);
    }

    private static MobSkin parseSkin(JsonObject s, boolean withVariants) {
        String value = JsonKt.str(s, "value");
        boolean slim = false;
        if (value != null) {
            try {
                slim = new String(java.util.Base64.getDecoder().decode(value), StandardCharsets.UTF_8).contains("\"slim\"");
            } catch (Exception e) {
                slim = false;
            }
        }
        List<MobSkin> variants = new ArrayList<>();
        if (withVariants) for (JsonObject v : JsonKt.objects(JsonKt.arr(s, "variants"))) variants.add(parseSkin(v, false));
        return new MobSkin(or(JsonKt.str(s, "id"), ""), value, JsonKt.str(s, "signature"), slim, variants);
    }

    private static Integer toIntOrNull(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double toDoubleOrNull(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static CountStats stats(JsonObject o) {
        if (o == null) return null;
        LinkedHashMap<Integer, Integer> hist = new LinkedHashMap<>();
        JsonObject h = JsonKt.obj(o, "hist");
        if (h != null) for (Map.Entry<String, JsonElement> en : h.entrySet()) {
            Integer kk = toIntOrNull(en.getKey());
            if (kk == null) continue;
            Integer v = JsonKt.asIntOrNull(en.getValue());
            if (v == null) continue;
            hist.put(kk, v);
        }
        return new CountStats(or(JsonKt.dbl(o, "mean"), 0.0), or(JsonKt.dbl(o, "p50"), 0.0), or(JsonKt.dbl(o, "p90"), 0.0), or(JsonKt.dbl(o, "max"), 0.0), hist);
    }

    private static RoomMobs parseRoom(JsonObject r, int archetypeCount) {
        String nm = JsonKt.str(r, "name");
        if (nm == null) throw new PackFormatException("no name");
        String name = nm.replace("​", "");
        List<MobCell> cells = new ArrayList<>();
        JsonArray ca = JsonKt.arr(r, "cells");
        if (ca != null) {
            for (JsonElement c : ca) {
                JsonArray a = JsonKt.asArr(c);
                if (a == null) continue;
                if (a.size() < 6) continue;
                Integer arch = JsonKt.asIntOrNull(a.get(3));
                if (arch == null) continue;
                if (arch < 0 || arch >= archetypeCount) continue;
                Integer x = JsonKt.asIntOrNull(a.get(0));
                if (x == null) continue;
                Integer y = JsonKt.asIntOrNull(a.get(1));
                if (y == null) continue;
                Integer z = JsonKt.asIntOrNull(a.get(2));
                if (z == null) continue;
                cells.add(new MobCell(x, y, z, arch, or(JsonKt.asIntOrNull(a.get(4)), 0) != 0, or(JsonKt.asIntOrNull(a.get(5)), 1)));
            }
        }
        List<MobLayout> layouts = new ArrayList<>();
        for (JsonObject l : JsonKt.objects(JsonKt.arr(r, "layouts"))) {
            List<MobSpawn> mobs = new ArrayList<>();
            JsonArray ma = JsonKt.arr(l, "m");
            if (ma != null) {
                for (JsonElement m : ma) {
                    JsonArray a = JsonKt.asArr(m);
                    if (a == null) continue;
                    if (a.size() < 5) continue;
                    Integer arch = JsonKt.asIntOrNull(a.get(3));
                    if (arch == null) continue;
                    if (arch < 0 || arch >= archetypeCount) continue;
                    Integer x = JsonKt.asIntOrNull(a.get(0));
                    if (x == null) continue;
                    Integer y = JsonKt.asIntOrNull(a.get(1));
                    if (y == null) continue;
                    Integer z = JsonKt.asIntOrNull(a.get(2));
                    if (z == null) continue;
                    mobs.add(new MobSpawn(x, y, z, arch, or(JsonKt.asIntOrNull(a.get(4)), 0) != 0));
                }
            }
            layouts.add(new MobLayout(or(JsonKt.str(l, "src"), "adr"), or(JsonKt.str(l, "fl"), "?"), mobs));
        }
        JsonObject visits = JsonKt.obj(r, "visits");
        Integer adr = visits != null ? JsonKt.int_(visits, "adr") : null;
        Integer cd = visits != null ? JsonKt.int_(visits, "cd") : null;
        List<String> aliases = new ArrayList<>();
        for (String s : JsonKt.strings(JsonKt.arr(r, "aliases"))) aliases.add(s.replace("​", ""));
        return new RoomMobs(name, JsonKt.strings(JsonKt.arr(r, "designs")), or(JsonKt.str(r, "clear"), "none"), adr != null ? adr : 0, cd != null ? cd : 0,
                stats(JsonKt.obj(r, "starred")), stats(JsonKt.obj(r, "unstarred")), cells, layouts, aliases, JsonKt.str(r, "type"));
    }

    private static HitQuantiles hit(JsonObject o) {
        if (o == null) return null;
        return new HitQuantiles(or(JsonKt.int_(o, "n"), 0), or(JsonKt.dbl(o, "p10"), 0.0), or(JsonKt.dbl(o, "p50"), 0.0), or(JsonKt.dbl(o, "p90"), 0.0));
    }

    private static MeleeTable meleeTable(JsonObject o) {
        TreeMap<Double, Double> q = new TreeMap<>();
        JsonObject qo = JsonKt.obj(o, "quantiles");
        if (qo != null) for (Map.Entry<String, JsonElement> en : qo.entrySet()) {
            Double kk = toDoubleOrNull(en.getKey());
            Double vv = JsonKt.asDoubleOrNull(en.getValue());
            if (kk != null && vv != null) q.put(kk, vv);
        }
        List<MeleeTable.Atom> atoms = new ArrayList<>();
        for (JsonObject a : JsonKt.objects(JsonKt.arr(o, "atoms"))) {
            Double value = JsonKt.dbl(a, "value");
            if (value == null) continue;
            Double p = JsonKt.dbl(a, "p");
            if (p == null) continue;
            atoms.add(new MeleeTable.Atom(value, p));
        }
        return new MeleeTable(or(JsonKt.int_(o, "n"), 0), or(JsonKt.dbl(o, "perMinute"), 0.0), q, atoms, or(JsonKt.bool(o, "provisional"), false));
    }

    private static PlayerDamageTable parseDamage(JsonObject o) {
        LinkedHashMap<String, MeleeTable> melee = new LinkedHashMap<>();
        JsonObject mo = JsonKt.obj(o, "melee");
        if (mo != null) for (Map.Entry<String, JsonElement> en : mo.entrySet()) {
            JsonObject v = JsonKt.asObj(en.getValue());
            if (v != null) melee.put(en.getKey(), meleeTable(v));
        }
        LinkedHashMap<String, Map<String, HitQuantiles>> byArch = new LinkedHashMap<>();
        JsonObject ba = JsonKt.obj(o, "meleeByArchetype");
        if (ba != null) for (Map.Entry<String, JsonElement> en : ba.entrySet()) {
            JsonObject fl = JsonKt.asObj(en.getValue());
            if (fl == null) continue;
            LinkedHashMap<String, HitQuantiles> inner = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> t : fl.entrySet()) {
                HitQuantiles h = hit(JsonKt.asObj(t.getValue()));
                if (h != null) inner.put(t.getKey(), h);
            }
            byArch.put(en.getKey(), inner);
        }
        LinkedHashMap<String, HitQuantiles> projectile = new LinkedHashMap<>();
        JsonObject po = JsonKt.obj(o, "projectile");
        if (po != null) for (Map.Entry<String, JsonElement> en : po.entrySet()) {
            HitQuantiles h = hit(JsonKt.asObj(en.getValue()));
            if (h != null) projectile.put(en.getKey(), h);
        }
        JsonObject trap = JsonKt.obj(o, "trap");
        Double percent = trap != null ? JsonKt.dbl(trap, "percent") : null;
        return new PlayerDamageTable(JsonKt.str(o, "unit"), or(JsonKt.int_(o, "immunityTicks"), 10), melee, byArch, projectile,
                percent != null ? percent : 15.0, JsonKt.strings(trap != null ? JsonKt.arr(trap, "kinds") : null));
    }
}
