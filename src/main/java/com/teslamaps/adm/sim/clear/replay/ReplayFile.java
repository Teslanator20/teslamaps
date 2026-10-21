// Ported from ADM's p3sim/clear/replay/ReplayFile.kt
package com.teslamaps.adm.sim.clear.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.adm.sim.util.Pair;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * A replay: one recorded run turned into what ADS needs to play it again (made outside the mod from a Dungeon Recorder recording, kept in
 * `<game dir>/config/teslamaps/adm/ads/replays/<layout id>.adsreplay.json.gz`; the files hold real players' names and never ship).
 * Every event is a JSON array `[k, kind, ...]`, k being the server tick counted from T0.
 */
public class ReplayFile {
    public final String id;
    public final String floor;
    public final Person self;
    public final List<Person> party;
    public final Map<Integer, String> idToMate;
    public final List<JsonArray> events;
    public final List<Mob> mobs;

    public static class Person {
        public final String name;
        public final String uuid;
        public final String cls;
        public final String tex;
        public final String sig;

        public Person(String name, String uuid, String cls, String tex, String sig) {
            this.name = name;
            this.uuid = uuid;
            this.cls = cls;
            this.tex = tex;
            this.sig = sig;
        }
    }

    /** One of Hypixel's mobs: its body, where it was first seen (k), its tag without HP and the most HP the tag showed. */
    public static class Mob {
        public final int id;
        public final String type;
        public final int k;
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final String tag;
        public final double hp;

        public Mob(int id, String type, int k, double x, double y, double z, float yaw, String tag, double hp) {
            this.id = id;
            this.type = type;
            this.k = k;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.tag = tag;
            this.hp = hp;
        }
    }

    /** Events by k, in their order. */
    private final Map<Integer, List<JsonArray>> byK;
    public final int firstK;
    public final int lastK;

    public ReplayFile(String id, String floor, Person self, List<Person> party, Map<Integer, String> idToMate, List<JsonArray> events, List<Mob> mobs) {
        this.id = id;
        this.floor = floor;
        this.self = self;
        this.party = party;
        this.idToMate = idToMate;
        this.events = events;
        this.mobs = mobs;
        Map<Integer, List<JsonArray>> m = new LinkedHashMap<>();
        for (JsonArray e : events) m.computeIfAbsent(e.get(0).getAsInt(), k -> new ArrayList<>()).add(e);
        this.byK = m;
        this.firstK = events.isEmpty() ? 0 : events.get(0).get(0).getAsInt();
        this.lastK = events.isEmpty() ? 0 : events.get(events.size() - 1).get(0).getAsInt();
    }

    public ReplayFile(String id, String floor, Person self, List<Person> party, Map<Integer, String> idToMate, List<JsonArray> events) {
        this(id, floor, self, party, idToMate, events, List.of());
    }

    public List<JsonArray> at(int k) {
        List<JsonArray> l = byK.get(k);
        return l != null ? l : List.of();
    }

    /** Every event of kind (the differ's truth). */
    public List<JsonArray> all(String kind) {
        List<JsonArray> out = new ArrayList<>();
        for (JsonArray e : events) if (e.get(1).getAsString().equals(kind)) out.add(e);
        return out;
    }

    private boolean bossKDone = false;
    private Integer bossK = null;

    /** The k the run reaches the boss (Odin's inBoss), or null. */
    public synchronized Integer bossK() {
        if (!bossKDone) {
            for (JsonArray e : events) {
                if (e.get(0).getAsInt() > 0 && e.get(1).getAsString().equals("dung") && e.size() > 3 && e.get(3).isJsonPrimitive() && e.get(3).getAsBoolean()) {
                    bossK = e.get(0).getAsInt();
                    break;
                }
            }
            bossKDone = true;
        }
        return bossK;
    }

    public static Path dir() { return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("config/teslamaps/adm/ads/replays"); }

    public static List<String> list() {
        try (Stream<Path> s = Files.list(dir())) {
            return s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".adsreplay.json.gz"))
                    .map(n -> n.substring(0, n.length() - ".adsreplay.json.gz".length())).sorted().toList();
        } catch (Throwable t) {
            return List.of();
        }
    }

    /** The replay whose id is id or starts with it. */
    public static String resolve(String id) {
        List<String> all = list();
        for (String s : all) if (s.equals(id)) return s;
        String one = null;
        int n = 0;
        for (String s : all) if (s.startsWith(id)) { one = s; n++; }
        return n == 1 ? one : null;
    }

    private static String str(JsonObject p, String k) {
        JsonElement e = p.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static Person person(JsonElement e) {
        if (e == null || !e.isJsonObject()) return null;
        JsonObject p = e.getAsJsonObject();
        String name = str(p, "name");
        if (name == null) return null;
        return new Person(name, str(p, "uuid"), str(p, "cls"), str(p, "tex"), str(p, "sig"));
    }

    private static Double dbl(JsonObject j, String k) {
        JsonElement e = j.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsDouble() : null;
    }

    public static ReplayFile load(String id) throws java.io.IOException {
        Path file = dir().resolve(id + ".adsreplay.json.gz");
        JsonObject o;
        try (Reader r = new InputStreamReader(new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8)) {
            o = JsonParser.parseReader(r).getAsJsonObject();
        }
        Person self = person(o.get("self"));
        if (self == null) throw new IllegalStateException("the replay names no player");
        List<Person> party = new ArrayList<>();
        JsonArray pa = o.getAsJsonArray("party");
        if (pa != null) for (JsonElement e : pa) { Person p = person(e); if (p != null) party.add(p); }
        HashMap<Integer, String> ids = new HashMap<>();
        JsonObject itm = o.getAsJsonObject("idToMate");
        if (itm != null) for (Map.Entry<String, JsonElement> en : itm.entrySet()) {
            Integer k = toIntOrNull(en.getKey());
            if (k != null) ids.put(k, en.getValue().getAsString());
        }
        List<JsonArray> ev = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("ev")) ev.add(e.getAsJsonArray());
        List<Mob> mobs = new ArrayList<>();
        JsonArray ma = o.getAsJsonArray("mobs");
        if (ma != null) for (JsonElement m : ma) {
            JsonObject j = m.getAsJsonObject();
            int mid = j.get("id").getAsInt();
            JsonElement te = j.get("type");
            String type = te != null && te.isJsonPrimitive() ? te.getAsString() : null;
            Double kk = dbl(j, "k");
            Double x = dbl(j, "x");
            if (x == null) continue;
            Double y = dbl(j, "y");
            Double z = dbl(j, "z");
            Double yaw = dbl(j, "yaw");
            JsonElement tg = j.get("tag");
            if (tg == null) continue;
            String tag = ReplayFileKt.trim(tg.getAsString());
            Double hp = dbl(j, "hp");
            mobs.add(new Mob(mid, type, kk != null ? (int) (double) kk : 0, x, y != null ? y : 0.0, z != null ? z : 0.0,
                    yaw != null ? (float) (double) yaw : 0f, tag, hp != null ? hp : 0.0));
        }
        JsonElement fl = o.get("floor");
        List<Mob> all = new ArrayList<>(mobs);
        all.addAll(levelled(ev, mobs));
        return new ReplayFile(o.get("id").getAsString(), fl != null && fl.isJsonPrimitive() ? fl.getAsString() : null, self, party, ids, ev, all);
    }

    private static Integer toIntOrNull(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    /** A levelled room mob's tag: `[Lv173]  Undead Skeleton 360k/360k❤` (icons between). */
    private static final Pattern LEVEL_TAG = Pattern.compile("^\\[Lv\\d+]\\s+\\S*\\s*(Undead Skeleton|Skeletor Prime) [\\d.,]+[kMB]?/([\\d.,]+[kMB]?)❤$");

    /** The levelled room mobs (Undead Skeleton, Skeletor Prime) the recording's mob list leaves out, from its events. */
    public static List<Mob> levelled(List<JsonArray> ev, List<Mob> known) {
        Set<Integer> have = new HashSet<>();
        for (Mob m : known) have.add(m.id);
        HashMap<Integer, JsonArray> first = new HashMap<>();
        LinkedHashMap<Integer, Pair<String, Double>> found = new LinkedHashMap<>();
        for (JsonArray e : ev) {
            String kind = ReplayFileKt.s(e, 1);
            if ("spawn".equals(kind)) {
                Integer id = ReplayFileKt.i(e, 2);
                if (id != null) first.putIfAbsent(id, e);
            } else if ("name".equals(kind)) {
                Integer id = ReplayFileKt.i(e, 2);
                if (id == null) continue;
                String tag = ReplayFileKt.s(e, 3);
                if (tag == null) continue;
                Matcher m = LEVEL_TAG.matcher(tag);
                if (!m.find()) continue;
                int body = id - 1;
                if (have.contains(body)) continue;
                double hp = amount(m.group(2));
                Pair<String, Double> prev = found.get(body);
                if (prev == null || hp > prev.second) found.put(body, Pair.of(m.group(1), hp));
            }
        }
        List<Mob> out = new ArrayList<>();
        for (Map.Entry<Integer, Pair<String, Double>> en : found.entrySet()) {
            int body = en.getKey();
            Pair<String, Double> v = en.getValue();
            JsonArray s = first.get(body);
            if (s == null || "minecraft:armor_stand".equals(ReplayFileKt.s(s, 3))) continue;
            Double x = ReplayFileKt.d(s, 5);
            if (x == null) continue;
            Integer k = ReplayFileKt.i(s, 0);
            Double y = ReplayFileKt.d(s, 6);
            Double z = ReplayFileKt.d(s, 7);
            Double yaw = ReplayFileKt.d(s, 9);
            out.add(new Mob(body, ReplayFileKt.s(s, 3), k != null ? k : 0, x, y != null ? y : 0.0, z != null ? z : 0.0, yaw != null ? (float) (double) yaw : 0f, v.first, v.second));
        }
        return out;
    }

    /** `360k`, `3.1M`, `90,000` as a number. */
    private static double amount(String t) {
        int end = t.length();
        while (end > 0 && (t.charAt(end - 1) == 'k' || t.charAt(end - 1) == 'M' || t.charAt(end - 1) == 'B')) end--;
        double n;
        try { n = Double.parseDouble(t.substring(0, end).replace(",", "")); } catch (NumberFormatException e) { return 0.0; }
        char last = t.isEmpty() ? 0 : t.charAt(t.length() - 1);
        return n * switch (last) { case 'k' -> 1e3; case 'M' -> 1e6; case 'B' -> 1e9; default -> 1.0; };
    }
}
