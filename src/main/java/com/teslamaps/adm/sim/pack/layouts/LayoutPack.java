// Ported from ADM's p3sim/pack/layouts/LayoutPack.kt
package com.teslamaps.adm.sim.pack.layouts;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.pack.JsonKt;
import com.teslamaps.adm.sim.pack.PackFiles;
import com.teslamaps.adm.sim.pack.PackFormatException;
import com.teslamaps.adm.sim.pack.PackReport;
import com.teslamaps.adm.sim.pack.PackSource;
import com.teslamaps.adm.sim.pack.Rot;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.pack.rooms.RoomPack;
import com.teslamaps.adm.sim.pack.rooms.RoomPackKt;
import com.teslamaps.adm.sim.util.KRandom;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** The loaded layout pool. */
public class LayoutPack {
    public final int version;
    public final Integer roomsPackVersion;
    public final Map<String, String> aliases;
    public final JsonObject stats;
    /** Sorted by id ascending (string order). */
    public final List<Layout> layouts;

    private final Map<String, Layout> byId;

    public LayoutPack(int version, Integer roomsPackVersion, Map<String, String> aliases, JsonObject stats, List<Layout> layouts) {
        this.version = version;
        this.roomsPackVersion = roomsPackVersion;
        this.aliases = aliases;
        this.stats = stats;
        this.layouts = layouts;
        LinkedHashMap<String, Layout> m = new LinkedHashMap<>();
        for (Layout l : layouts) m.put(l.id, l);
        this.byId = m;
    }

    public Layout layout(String id) { return byId.get(id); }

    /** The family a room name belongs to (this pack's {@code aliases}). */
    public String familyOf(String name) { return aliases.get(name.replace("​", "")); }

    public List<Layout> pool() { return pool(DEFAULT_FILTER); }

    /** The layouts that pass [filter], in id order. */
    public List<Layout> pool(Predicate<Layout> filter) {
        List<Layout> out = new ArrayList<>();
        for (Layout l : layouts) if (filter.test(l)) out.add(l);
        return out;
    }

    public Layout pick(long seed) { return pick(seed, DEFAULT_FILTER); }

    /** The seed's layout: Kotlin's {@code Random(seed)} picks an index into the filtered list sorted by id. Null when the pool is empty. */
    public Layout pick(long seed, Predicate<Layout> filter) {
        List<Layout> pool = pool(filter);
        if (pool.isEmpty()) return null;
        return pool.get(new KRandom(seed).nextInt(pool.size()));
    }

    public static final String FORMAT = "ads-layouts";
    public static final int SUPPORTED_VERSION = 1;

    /** {@code playable && dupOf == null && floor == "F7"}. */
    public static final Predicate<Layout> DEFAULT_FILTER = Layout::inDefaultPool;

    public static LayoutPack load(Path dir) { return load(dir, null); }
    public static LayoutPack load(Path dir, RoomPack roomPack) { return load(dir, roomPack, new PackReport()); }

    /** Loads {@code layouts.json.gz} from [dir]. Null, with the reason in [report]. Never throws. */
    public static LayoutPack load(Path dir, RoomPack roomPack, PackReport report) { return load(PackSource.ofDir(dir), roomPack, report); }

    public static LayoutPack load(PackSource source) { return load(source, null); }
    public static LayoutPack load(PackSource source, RoomPack roomPack) { return load(source, roomPack, new PackReport()); }

    public static LayoutPack load(PackSource source, RoomPack roomPack, PackReport report) {
        Path path = source.file(PackFiles.LAYOUTS);
        if (path == null) { report.error("The layout pool " + PackFiles.LAYOUTS + " is missing from " + source.missingFrom() + "."); return null; }
        JsonObject root;
        try (InputStream in = Files.newInputStream(path)) {
            root = JsonKt.readGzipJson(in);
        } catch (Exception e) {
            report.error("The layout pool " + PackFiles.LAYOUTS + " is unreadable" + source.note(PackFiles.LAYOUTS) + ": " + e.getMessage());
            return null;
        }
        return fromJson(root, roomPack, report);
    }

    public static LayoutPack fromJson(JsonObject root) { return fromJson(root, null); }
    public static LayoutPack fromJson(JsonObject root, RoomPack roomPack) { return fromJson(root, roomPack, new PackReport()); }

    /** Builds the pool from a parsed file (tests, tools). */
    public static LayoutPack fromJson(JsonObject root, RoomPack roomPack, PackReport report) {
        if (!RoomPackKt.checkHeader(root, FORMAT, SUPPORTED_VERSION, PackFiles.LAYOUTS, report)) return null;
        Integer rv = JsonKt.int_(root, "roomsPackVersion");
        if (rv != null && roomPack != null && rv != roomPack.version) {
            report.warn("The layout pool was made for room library version " + rv + " but version " + roomPack.version + " is loaded. Layouts may not match their rooms.");
        }
        ArrayList<Layout> layouts = new ArrayList<>();
        for (JsonObject o : JsonKt.objects(JsonKt.arr(root, "layouts"))) {
            try {
                layouts.add(parseLayout(o));
            } catch (Exception e) {
                String id = JsonKt.str(o, "id");
                report.warn("A layout was skipped (" + (id != null ? id : "no id") + "): " + e.getMessage());
            }
        }
        layouts.sort(Comparator.comparing(l -> l.id));
        if (layouts.isEmpty()) report.warn("The layout pool has no layouts.");
        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        JsonObject al = JsonKt.obj(root, "aliases");
        if (al != null) {
            for (Map.Entry<String, JsonElement> en : al.entrySet()) {
                String v = JsonKt.asStr(en.getValue());
                if (v != null) aliases.put(en.getKey().replace("​", ""), v);
            }
        }
        Integer version = JsonKt.int_(root, "version");
        return new LayoutPack(version != null ? version : 1, rv, aliases, JsonKt.obj(root, "stats"), layouts);
    }

    private static <T> T or(T v, T def) { return v != null ? v : def; }

    private static Layout parseLayout(JsonObject o) {
        String id = JsonKt.str(o, "id");
        if (id == null) throw new PackFormatException("no id");
        List<LayoutRoom> rooms = new ArrayList<>();
        for (JsonObject r : JsonKt.objects(JsonKt.arr(o, "rooms"))) {
            Integer i = JsonKt.int_(r, "i");
            if (i == null) throw new PackFormatException("a room has no index");
            String nm = JsonKt.str(r, "name");
            String name = (nm != null ? nm : "").replace("​", "");
            String fam = JsonKt.str(r, "family");
            String family = fam != null ? fam : (nm != null ? nm : "").replace(' ', '_');
            rooms.add(new LayoutRoom(
                    i,
                    name,
                    family,
                    JsonKt.str(r, "design"),
                    JsonKt.str(r, "designBy"),
                    JsonKt.int_(r, "core"),
                    or(JsonKt.str(r, "type"), "normal"),
                    or(JsonKt.str(r, "shape"), "1x1"),
                    JsonKt.asTiles(r.get("tiles")),
                    Rot.fromWord(JsonKt.str(r, "rot")),
                    JsonKt.str(r, "rotSource"),
                    JsonKt.str(r, "rotOdin"),
                    JsonKt.int_(r, "secretsMax"),
                    JsonKt.int_(r, "secretsFound"),
                    JsonKt.int_(r, "tSeen"), JsonKt.int_(r, "tNone"), JsonKt.int_(r, "tWhite"), JsonKt.int_(r, "tGreen"), JsonKt.int_(r, "tRed")));
        }
        rooms.sort(Comparator.comparingInt(r -> r.i));
        List<LayoutDoor> doors = new ArrayList<>();
        for (JsonObject d : JsonKt.objects(JsonKt.arr(o, "doors"))) {
            String ax = JsonKt.str(d, "axis");
            if (ax == null || ax.isEmpty()) continue;
            char axis = ax.charAt(0);
            Tile a = JsonKt.asTile(d.get("a"));
            if (a == null) continue;
            Tile b = JsonKt.asTile(d.get("b"));
            if (b == null) continue;
            doors.add(new LayoutDoor(axis, a, b, or(JsonKt.int_(d, "ra"), -1), or(JsonKt.int_(d, "rb"), -1), JsonKt.str(d, "read"),
                    DoorKind.fromWord(JsonKt.str(d, "kind")), or(JsonKt.bool(d, "fix"), false), JsonKt.int_(d, "tOpen")));
        }
        List<Integer> path = null;
        if (JsonKt.arr(o, "path") != null) {
            path = new ArrayList<>();
            for (int v : JsonKt.ints(JsonKt.arr(o, "path"))) path.add(v);
        }
        List<LayoutKey> keys = new ArrayList<>();
        for (JsonObject k : JsonKt.objects(JsonKt.arr(o, "keys"))) {
            keys.add(new LayoutKey(or(JsonKt.str(k, "kind"), "wither"), JsonKt.int_(k, "tSpawn"), or(JsonKt.dbl(k, "x"), 0.0), or(JsonKt.dbl(k, "z"), 0.0),
                    JsonKt.int_(k, "room"), JsonKt.int_(k, "tPickup"), JsonKt.int_(k, "tGone")));
        }
        Integer st = JsonKt.int_(o, "secretsTotal");
        int secretsTotal;
        if (st != null) secretsTotal = st;
        else {
            secretsTotal = 0;
            for (LayoutRoom r : rooms) secretsTotal += r.secretsMax != null ? r.secretsMax : 0;
        }
        LinkedHashMap<String, Integer> rotSummary = new LinkedHashMap<>();
        JsonObject rs = JsonKt.obj(o, "rotSummary");
        if (rs != null) {
            for (Map.Entry<String, JsonElement> en : rs.entrySet()) {
                Integer v = JsonKt.asIntOrNull(en.getValue());
                if (v != null) rotSummary.put(en.getKey(), v);
            }
        }
        return new Layout(
                id,
                or(JsonKt.str(o, "floor"), "F7"),
                JsonKt.str(o, "date"),
                JsonKt.str(o, "source"),
                rooms,
                doors,
                or(JsonKt.bool(o, "doorsComplete"), false),
                path,
                keys,
                JsonKt.strings(JsonKt.arr(o, "puzzles")),
                secretsTotal,
                JsonKt.str(o, "t0Basis"),
                JsonKt.int_(o, "endTick"),
                JsonKt.strings(JsonKt.arr(o, "party")),
                JsonKt.bool(o, "buildable"),
                JsonKt.bool(o, "playable"),
                JsonKt.str(o, "dupOf"),
                rotSummary);
    }
}
