package com.teslamaps.safari;

// Ported from Sparkling Mutuals' FloorDropPredictor.kt
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A floor drop is three string displays with consecutive entity ids; the id spacing says what it holds. */
public final class FloorDropPredictor {

    public enum Type {
        LIME("Lime Gem"), PURPLE("Purple Gem"), ORANGE("Orange Gem"),
        WORM("Wriggleworm"), BERRY("Yogi Berry"), SEEDS("Bag of Seeds"),
        INCENSE("Soothing Incense"), COIN("Shining Coin"), ICEBREAKER("Icebreaker"),
        GENERIC("XP / Essence / Shard");

        public final String label;
        final Pattern pickup;

        Type(String label) {
            this.label = label;
            this.pickup = Pattern.compile("(?:([0-9]+)x?\\s+)?\\b" + Pattern.quote(label) + "\\b(?! Shard)(?:\\s+x([0-9]+))?",
                    Pattern.CASE_INSENSITIVE);
        }

        public boolean gem() { return this == LIME || this == PURPLE || this == ORANGE; }
        public boolean food() { return this == WORM || this == BERRY || this == SEEDS; }
    }

    private static final Type[] GEMS = {Type.LIME, Type.PURPLE, Type.ORANGE};
    private static final Type[] FOOD = {Type.WORM, Type.BERRY, Type.SEEDS};

    public record Display(int id, double x, double y, double z, String description) {}

    public record Pickup(Type type, int quantity) {
        /** Stripped chat line. */
        public static Pickup parse(String text) {
            if (!text.startsWith("FLOOR DROP!")) return null;
            for (Type t : Type.values()) {
                if (t == Type.GENERIC) continue;
                Matcher m = t.pickup.matcher(text);
                if (!m.find()) continue;
                Integer n = toInt(m.group(1));
                if (n == null) n = toInt(m.group(2));
                return new Pickup(t, Math.max(n == null ? 1 : n, 1));
            }
            return null;
        }

        private static Integer toInt(String s) {
            if (s == null) return null;
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
        }
    }

    public static final class Drop {
        public final int id;
        public final double x, y, z;
        public final SafariData.Biome biome;
        public Type type;
        public boolean live;
        public long seenAt;
        public Pickup actual;

        Drop(int id, double x, double y, double z, SafariData.Biome biome) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.biome = biome;
        }
    }

    private record Layout(int base, int coins, int ice) {
        Type kind(int id) {
            int delta = id - base;
            if (delta < 0) return null;
            if (delta >= (20 + coins + ice) * 3) return Type.GENERIC;
            if (delta % 3 != 0) return null;
            int n = delta / 3;
            if (n < 6) return GEMS[n % 3];
            if (n < 15) return FOOD[(n - 6) / 3];
            if (n < 20) return Type.INCENSE;
            return n < 20 + coins ? Type.COIN : Type.ICEBREAKER;
        }
    }

    private final TreeMap<Integer, Drop> groups = new TreeMap<>();

    public java.util.Collection<Drop> known() {
        return groups.values();
    }

    public Drop get(int id) {
        return groups.get(id);
    }

    public void reset() {
        groups.clear();
    }

    public List<Drop> scan(List<Display> displays, long now) {
        for (Drop d : groups.values()) d.live = false;

        List<Display> strings = new ArrayList<>();
        for (Display d : displays) {
            if (d.description().equals("minecraft:string") || d.description().startsWith("minecraft:string:")) strings.add(d);
        }
        Map<Integer, Display> byId = new HashMap<>();
        for (Display d : strings) byId.put(d.id(), d);
        strings.sort((a, b) -> Integer.compare(a.id(), b.id()));

        Set<Integer> used = new HashSet<>();
        for (Display d : strings) {
            if (used.contains(d.id())) continue;
            Display b = byId.get(d.id() + 1);
            if (b == null) continue;
            Display c = byId.get(d.id() + 2);
            if (c == null) continue;
            Display[] trio = {d, b, c};
            if (used.contains(b.id()) || used.contains(c.id())) continue;
            if (spread(trio)) continue;
            if (!d.description().equals(b.description()) || !d.description().equals(c.description())) continue;
            used.add(d.id());
            used.add(b.id());
            used.add(c.id());

            double x = round2((d.x() + b.x() + c.x()) / 3);
            double y = round2((d.y() + b.y() + c.y()) / 3);
            double z = round2((d.z() + b.z() + c.z()) / 3);
            Drop drop = groups.get(d.id());
            if (drop == null) {
                SafariData.Biome biome = floorBiome(x, y, z);
                if (biome == null) biome = biomeAt(x, z);
                drop = new Drop(d.id(), x, y, z, biome);
                groups.put(d.id(), drop);
            }
            drop.live = true;
            drop.seenAt = now;
        }

        Integer first = groups.isEmpty() ? null : groups.firstKey();
        if (first != null && fullLayout(first)) decodeLayout(first);
        else decodeByBiome();

        List<Drop> live = new ArrayList<>();
        for (Drop d : groups.values()) if (d.live) live.add(d);
        return live;
    }

    private static boolean spread(Display[] trio) {
        for (Display a : trio) {
            for (Display b : trio) {
                double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
                if (dx * dx + dy * dy + dz * dz > 2.25) return true;
            }
        }
        return false;
    }

    private static double round2(double v) {
        return Math.rint(v * 100) / 100;
    }

    private boolean fullLayout(int first) {
        for (int i = 0; i < 20; i++) {
            Drop d = groups.get(first + i * 3);
            SafariData.Biome want = i < 6 ? SafariData.Biome.CAVERN : i < 15 ? SafariData.Biome.FOREST : SafariData.Biome.HAUNTED;
            if (d == null || d.biome != want) return false;
        }
        return true;
    }

    private void decodeLayout(int first) {
        List<Layout> layouts = new ArrayList<>();
        for (int coins = 3; coins <= 6; coins++) {
            for (int ice = 4; ice <= 5; ice++) layouts.add(new Layout(first, coins, ice));
        }

        List<Layout> consistent = new ArrayList<>();
        for (Layout l : layouts) {
            boolean ok = true;
            for (Drop d : groups.values()) {
                Type k = l.kind(d.id);
                if (k == null || (k != Type.GENERIC && !fitsBiome(k, d.biome))) { ok = false; break; }
            }
            if (ok) consistent.add(l);
        }

        List<Layout> confirmed = new ArrayList<>();
        for (Layout l : consistent) {
            boolean ok = true;
            for (Drop d : groups.values()) {
                if (d.actual != null && d.actual.type() != l.kind(d.id)) { ok = false; break; }
            }
            if (ok) confirmed.add(l);
        }
        List<Layout> candidates = confirmed.isEmpty() ? consistent : confirmed;

        List<Layout> ice5 = new ArrayList<>();
        for (Layout l : candidates) if (l.ice() == 5) ice5.add(l);
        if (ice5.isEmpty()) ice5 = candidates;

        Layout low = new Layout(first, 3, 4);
        Layout high = new Layout(first, 6, 5);
        for (Drop d : groups.values()) {
            Type only = null;
            boolean single = true;
            boolean any = false;
            for (Layout l : ice5) {
                Type k = l.kind(d.id);
                if (!any) { only = k; any = true; }
                else if (k != only) { single = false; break; }
            }
            if (any && single && only != null) {
                d.type = only;
            } else {
                Type lo = low.kind(d.id);
                d.type = lo != null && lo == high.kind(d.id) ? lo : null;
            }
        }
    }

    private static boolean fitsBiome(Type k, SafariData.Biome biome) {
        if (k.gem()) return biome == SafariData.Biome.CAVERN;
        if (k.food()) return biome == SafariData.Biome.FOREST;
        if (k == Type.ICEBREAKER) return biome == SafariData.Biome.ICY;
        return biome == SafariData.Biome.HAUNTED;
    }

    private void decodeByBiome() {
        SafariData.Biome[] order = {SafariData.Biome.CAVERN, SafariData.Biome.FOREST, SafariData.Biome.HAUNTED, SafariData.Biome.ICY};
        int maxIdx = 0;
        Integer base = null;
        boolean broken = false;
        for (Drop d : groups.values()) {
            int idx = -1;
            for (int i = 0; i < order.length; i++) if (order[i] == d.biome) idx = i;
            if (idx < 0) {
                d.type = null;
                continue;
            }
            if (idx > maxIdx && !broken) {
                maxIdx = idx;
                base = null;
            }
            if (idx != maxIdx) broken = true;
            if (base == null) base = d.id;
            int n = (d.id - base) / 3;
            if (broken) {
                d.type = Type.GENERIC;
                continue;
            }
            Type t = switch (d.biome) {
                case CAVERN -> n < 6 ? GEMS[n % 3] : null;
                case FOREST -> n / 3 < FOOD.length ? FOOD[n / 3] : null;
                case HAUNTED -> n < 5 ? Type.INCENSE : n < 11 ? Type.COIN : null;
                case ICY -> n < 5 ? Type.ICEBREAKER : null;
            };
            if (t == null) {
                t = Type.GENERIC;
                broken = true;
            }
            d.type = t;
        }
    }

    /** Nearest unclaimed drop seen in the last 3s within 5 blocks; a tie claims nothing. */
    public Drop pickup(Pickup reward, double x, double y, double z, long now) {
        List<Drop> nearby = new ArrayList<>();
        for (Drop d : groups.values()) {
            if (d.actual != null) continue;
            long age = now - d.seenAt;
            if (age < 0 || age > 3000) continue;
            if (dist(d, x, y, z) <= 25.0) nearby.add(d);
        }
        if (nearby.isEmpty()) return null;
        nearby.sort((a, b) -> Double.compare(dist(a, x, y, z), dist(b, x, y, z)));
        Drop best = nearby.get(0);
        if (nearby.size() > 1 && Math.abs(dist(best, x, y, z) - dist(nearby.get(1), x, y, z)) < 0.01) return null;
        best.actual = reward;
        return best;
    }

    private static double dist(Drop d, double x, double y, double z) {
        double dx = d.x - x, dy = d.y - y, dz = d.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Coordinate quadrants of the Safari, fallback when a site is not in the bundled list. */
    public static SafariData.Biome biomeAt(double x, double z) {
        if (x < -200.0 || x > 100.0 || z < -150.0 || z > 150.0) return null;
        if (x >= -50.0 && z >= 0.0) return SafariData.Biome.FOREST;
        if (x >= -50.0) return SafariData.Biome.HAUNTED;
        return z >= 0.0 ? SafariData.Biome.CAVERN : SafariData.Biome.ICY;
    }

    private static Map<Long, SafariData.Biome> sites;

    private static SafariData.Biome floorBiome(double x, double y, double z) {
        if (sites == null) sites = loadSites();
        return sites.get(key((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
    }

    private static long key(int x, int y, int z) {
        return net.minecraft.core.BlockPos.asLong(x, y, z);
    }

    private static Map<Long, SafariData.Biome> loadSites() {
        Map<Long, SafariData.Biome> out = new HashMap<>();
        try (InputStream in = FloorDropPredictor.class.getResourceAsStream("/assets/teslamaps/safari/floor_sites.json")) {
            if (in == null) return out;
            var obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : obj.entrySet()) {
                SafariData.Biome biome = SafariData.Biome.valueOf(e.getKey());
                for (JsonElement el : e.getValue().getAsJsonArray()) {
                    String[] p = el.getAsString().split(",");
                    out.put(key((int) Math.floor(Double.parseDouble(p[0])), (int) Math.floor(Double.parseDouble(p[1])),
                            (int) Math.floor(Double.parseDouble(p[2]))), biome);
                }
            }
        } catch (Exception e) {
            com.teslamaps.TeslaMaps.LOGGER.warn("floor_sites.json: {}", e.toString());
        }
        return out;
    }
}
