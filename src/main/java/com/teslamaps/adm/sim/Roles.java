package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/Roles.kt

import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import static com.teslamaps.adm.sim.DungeonClass.ARCHER;
import static com.teslamaps.adm.sim.DungeonClass.BERSERK;
import static com.teslamaps.adm.sim.DungeonClass.HEALER;
import static com.teslamaps.adm.sim.DungeonClass.MAGE;
import static com.teslamaps.adm.sim.DungeonClass.TANK;

public final class Roles {
    private Roles() {}

    public static class Preset {
        public final String name;
        public final Map<DungeonClass, String> roles;
        public final List<String> times;
        public final List<String> moves;
        public final List<Help> helps;
        public final Map<String, Vec3> spots;
        public final Map<String, Spots.Spot> defaults;
        public final Map<String, List<BlockPos>> mines;

        public Preset(String name, Map<DungeonClass, String> roles, List<String> times, List<String> moves, List<Help> helps,
                      Map<String, Vec3> spots, Map<String, Spots.Spot> defaults, Map<String, List<BlockPos>> mines) {
            this.name = name;
            this.roles = roles;
            this.times = times;
            this.moves = moves;
            this.helps = helps;
            this.spots = spots;
            this.defaults = defaults;
            this.mines = mines;
        }

        public Preset(String name, Map<DungeonClass, String> roles, List<String> times, List<String> moves, List<Help> helps,
                      Map<String, Vec3> spots, Map<String, Spots.Spot> defaults) {
            this(name, roles, times, moves, helps, spots, defaults, Collections.emptyMap());
        }

        public Preset(String name, Map<DungeonClass, String> roles, List<String> times, List<String> moves, List<Help> helps,
                      Map<String, Vec3> spots) {
            this(name, roles, times, moves, helps, spots, Collections.emptyMap());
        }

        public Preset(String name, Map<DungeonClass, String> roles, List<String> times, List<String> moves, List<Help> helps) {
            this(name, roles, times, moves, helps, Collections.emptyMap());
        }

        public Preset(String name, Map<DungeonClass, String> roles, List<String> times, List<String> moves) {
            this(name, roles, times, moves, Collections.emptyList());
        }
    }

    public static class Help {
        public final int section;
        public final String yours;
        public final String by;
        public final List<String> jobs;
        public final double at;

        public Help(int section, String yours, String by, List<String> jobs, double at) {
            this.section = section;
            this.yours = yours;
            this.by = by;
            this.jobs = jobs;
            this.at = at;
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, V> LinkedHashMap<K, V> linked(Object... kv) {
        LinkedHashMap<K, V> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((K) kv[i], (V) kv[i + 1]);
        return m;
    }

    private static List<BlockPos> quartzPlatform() {
        List<BlockPos> out = new ArrayList<>();
        for (int x : new int[]{15, 16}) for (int z : new int[]{93, 94}) out.add(new BlockPos(x, 122, z));
        return out;
    }

    public static final Preset PF = new Preset(
            "PF",
            linked(
                    MAGE, "bl ee2 / dev 2 / core / recore",
                    ARCHER, "43 / 4 rl / 4 bl / 2",
                    HEALER, "ss / ll ee3 / 2 dev / 4 bl",
                    TANK, "21 / 13 / 1 bl / 1",
                    BERSERK, "i4 / 53 / 3 / 3 bl"
            ),
            List.of(
                    "ll 0.35 | rl 2.15 | gate 2.85 | i4 5.1 | lights 7.1 | terms 9.0 | ss 9.6",
                    "ll 2.8 | 1 4 5 3.0 | gate 3.4 | 2 3 rl 7.0",
                    "1 4 3.0 | 2 3 5.0 | levers gate 8.0 | dev 9.0",
                    "1 2 4.0 | 3 4 6.0 | levers 8.0"
            ),
            List.of(
                    "i4 leaps 5.8 | ee2 spot 7.8 | leap ee2 9.4",
                    "ee3 spot 5.0 | ee3 waits 1 2 3 4",
                    "", ""
            )
    );

    public static final Preset QUALITY_PF = new Preset(
            "Quality PF",
            linked(
                    MAGE, "bl ee2 / dev 2 3 / core / recore",
                    ARCHER, "43 / 4 rl / 4 bl / 2",
                    HEALER, "ss / (leap to 2nd term) 1 / 2 dev / 4 bl",
                    TANK, "21 / ll ee3 / 1 bl / 1",
                    BERSERK, "i4 / 53 / 3 dev / 3 bl"
            ),
            List.of(
                    "ll 0.15 | rl 1.75 | gate 2.05 | i4 3.4 | lights 5.1 | terms 6.0 | ss 8.2",
                    "ll 0.8 | gate 1.0 | 1 4 5 2.1 | 2 3 rl 5.0",
                    "1 3 4 2.1 | 2 4.0 | levers gate 6.0 | dev 6.5",
                    "1 3 4 2.1 | 2 4.0 | levers 5.2"
            ),
            List.of(
                    "i4 leaps 4.0 | ee2 spot 5.8 | leap ee2 6.3 | ee2 waits ee3 4 5",
                    "ee3 spot 3.2 | ee3 waits 1 2 3 4",
                    "", ""
            ),
            List.of(
                    new Help(3, "S3 T4", "S3 T1", List.of("S3 east lever"), 4.8),
                    new Help(3, "S3 T1", "S3 T4", List.of("S3 west lever", "gate 3"), 4.8),
                    new Help(4, "S4 T3", "S4 T4", List.of("S4 high lever"), 4.2),
                    new Help(4, "S4 T4", "S4 T3", List.of("S4 low lever"), 4.2)
            )
    );

    public static final Preset ADVANCED_PF = new Preset(
            "Advanced PF",
            linked(
                    MAGE, "bl ee2 / dev 53 / core / recore",
                    ARCHER, "43 / 2 / 4 bl / 2",
                    HEALER, "ss / 1 / 2 dev / 4 bl",
                    TANK, "21 / ll ee3 / 1 bl / 1",
                    BERSERK, "i4 / 4 rl / 3 dev / 3 bl"
            ),
            List.of(
                    "ll 0.15 | rl 1.75 | gate 2.05 | i4 3.4 | lights 5.1 | terms 6.0 | ss 8.2",
                    "ll 0.8 | gate 1.0 | 1 2 4 5 2.1 | 3 3.8 | rl 4.0",
                    "1 2 3 4 2.1 | levers gate 4.2 | dev 5.0",
                    "1 2 3 4 2.1 | levers 4.0"
            ),
            List.of(
                    "i4 leaps 4.0 | ee2 spot 5.8 | leap ee2 6.3 | ee2 waits ee3 2 4",
                    "ee3 spot 3.6 | ee3 waits 1 2 3 4",
                    "", ""
            )
    );

    public static final Preset NEAR_OPTIMAL = new Preset(
            "Near Optimal",
            linked(
                    MAGE, "bl ee2 / dev 53 / core / 4 recore",
                    ARCHER, "43 / 2 / i3 4 bl / 2 recore",
                    HEALER, "ss / 1 / 1 / 1 recore",
                    TANK, "21 / hee3 / 3 bl / bl",
                    BERSERK, "i4 / bl 4 / 2 / 3 recore"
            ),
            List.of(
                    "ll 0.15 | 2 1.3 | rl 1.5 | gate 1.8 | 4 2.95 | 1 3.0 | i4 3.0 | lights 4.6 | 3 5.0 | ss 7.8",
                    "ll 0.25 | rl 1.05 | gate 1.25 | 2 1.25 | 5 1.6 | 1 2.9 | 3 2.95 | 4 4.0 | arrows 4.3",
                    "3 1.25 | 4 2.0 | 1 2.95 | 2 3.35 | ll 3.6 | gate 3.8 | rl 4.4",
                    "2 1.9 | 4 1.9 | 1 2.0 | 3 2.1 | ll 2.4 | rl 3.2"
            ),
            List.of(
                    "i4 leaps 3.4 | ee2 spot 5.0 | leap ee2 5.6 | ee2 waits 2 4",
                    "ee3 spot 2.5 | ee3 waits 1 2",
                    "", ""
            ),
            Collections.emptyList(),
            Collections.emptyMap(),
            linked("ee3", new Spots.Spot("EE3", 18.70, 121.50, 91.30, 0.82f, -8.64f)),
            linked("ee3", quartzPlatform())
    );

    public static final Preset PROTOTYPE_1 = new Preset(
            "Prototype 1",
            linked(
                    HEALER, "ss (leap archer) / 1 (leap bers) / 1 (leap mage) / 1 recore",
                    MAGE, "bl 43 / 53 / / 4 recore",
                    BERSERK, "i4 / ee3 / 3 bl / bl",
                    ARCHER, "21 / 2 / dev 4 (leap healer) / 2 recore",
                    TANK, "ee2 / dev bl 4 / 2 / 3 recore"
            ),
            List.of(
                    "ll 0.1 | lights 0.1 | rl 0.45 | gate 0.6 | 2 2.75 | i4 3.05 | 4 3.35 | 1 4.85 | 3 5.45 | ss 8.1",
                    "ll 0.1 | rl 0.8 | gate 1.0 | 5 2 2.1 | 1 3.0 | arrows 3.6 | 3 4.3 | 4 4.4",
                    "4 2.1 | 3 2.15 | 1 2.45 | 2 2.95 | ll 4.0 | gate 4.2 | rl 4.4",
                    "ll 1.1 | rl 1.75 | terms 2.1"
            ),
            List.of(
                    "ee2 waits 2 5 ee3",
                    "ee3 waits 1 2 4",
                    "",
                    ""
            ),
            Collections.emptyList(),
            linked(
                    "ee2", new Vec3(34.05, 131.0, 139.05),
                    "ee3", new Vec3(16.5, 123.0, 93.7)
            )
    );

    public static final Preset THEORETICAL = new Preset(
            "Theoretical",
            linked(
                    HEALER, "ss / 1 / 1 / 1",
                    BERSERK, "i4 / ee3 / 3 bl / bl",
                    TANK, "21 / 2 / i3 4 / 2",
                    MAGE, "i2 gate ee2 / bl 4 / 2 / 3",
                    ARCHER, "bl 43 / 53 / ee4 / 4"
            ),
            List.of(
                    "ll 0.05 | lights gate 0.2 | rl 0.7 | 2 1.3 | i4 2.05 | 4 2.95 | 1 3.0 | 3 5.05 | ss 7.8",
                    "gate 0.05 | ll 0.25 | rl 1.05 | 2 1.25 | 5 1.6 | 1 2.9 | 3 2.95 | 4 4.15 | arrows 4.5",
                    "3 1.25 | 4 2.75 | 1 2.95 | 2 3.35 | gate 3.4 | ll 3.85 | rl 4.5",
                    "3 1.7 | 2 1.85 | 1 2.0 | 4 2.6 | ll 2.2 | rl 3.0"
            ),
            List.of(
                    "ee2 spot 1.85 | leap ee2 3.5 | ee2 waits ee3 2 5",
                    "ee3 waits 1 2 4",
                    "ee4 spot 2.95 | ee4 waits 1 3 | 2 onto 1",
                    ""
            ),
            Collections.emptyList(),
            Collections.emptyMap(),
            linked(
                    "ee2", new Spots.Spot("i2", 35.99, 131.0, 138.96, 92.20f, 3.86f),
                    "ee3", new Spots.Spot("EE3", 18.70, 121.50, 91.30, 0.82f, -8.64f),
                    "spawn/MAGE", new Spots.Spot("Spawn", 60.5, 132.0, 139.0, 90.47f, 1.64f),
                    "spawn/ARCHER", new Spots.Spot("Spawn", 107.53, 123.06, 112.45, 47.59f, 19.81f),
                    "stand/S2 T2", new Spots.Spot("S2 T2", 59.50, 120.00, 125.20, -179.66f, 23.96f)
            ),
            linked("ee3", quartzPlatform())
    );

    public static final List<Preset> PRESETS = List.of(PF, QUALITY_PF, ADVANCED_PF, PROTOTYPE_1, THEORETICAL, NEAR_OPTIMAL);

    public static final Map<Integer, String> LEFT = linked(1, "S1 east lever", 2, "S2 high lever", 3, "S3 west lever", 4, "S4 low lever");
    public static final Map<Integer, String> RIGHT = linked(1, "S1 west lever", 2, "S2 low lever", 3, "S3 east lever", 4, "S4 high lever");

    public static class Move {
        public final int section;
        public final String kind;
        public final String who;
        public final Double at;
        public final List<String> args;

        public Move(int section, String kind, String who, Double at, List<String> args) {
            this.section = section;
            this.kind = kind;
            this.who = who;
            this.at = at;
            this.args = args;
        }

        public Move(int section, String kind, String who, Double at) {
            this(section, kind, who, at, Collections.emptyList());
        }
    }

    public static class Plan {
        public final Map<String, List<DungeonClass>> owners;
        public final Map<DungeonClass, List<String>> jobsOf;
        public final Map<Integer, DungeonClass> ee;
        public final Map<String, Pair<Integer, Double>> times;
        public final List<Move> moves;

        public Plan(Map<String, List<DungeonClass>> owners, Map<DungeonClass, List<String>> jobsOf, Map<Integer, DungeonClass> ee,
                    Map<String, Pair<Integer, Double>> times, List<Move> moves) {
            this.owners = owners;
            this.jobsOf = jobsOf;
            this.ee = ee;
            this.times = times;
            this.moves = moves;
        }
    }

    private static final HashMap<Preset, Plan> cache = new HashMap<>();

    public static Plan plan(Preset p) {
        Plan got = cache.get(p);
        if (got == null) {
            got = parse(p);
            cache.put(p, got);
        }
        return got;
    }

    private static String device(int s) {
        return switch (s) {
            case 1 -> "S1 SS";
            case 2 -> "S2 Lights";
            case 3 -> "S3 Arrows";
            default -> "S4 Target";
        };
    }

    private static List<String> terms(int s) {
        List<String> out = new ArrayList<>();
        int last = s == 2 ? 5 : 4;
        for (int i = 1; i <= last; i++) out.add("S" + s + " T" + i);
        return out;
    }

    private static boolean allDigits(String t) {
        for (int i = 0; i < t.length(); i++) if (!Character.isDigit(t.charAt(i))) return false;
        return true;
    }

    private static Integer toIntOrNull(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double toDoubleOrNull(String s) {
        if (s == null) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> jobs(String token, int s) {
        if (allDigits(token)) {
            List<String> ts = terms(s);
            List<String> out = new ArrayList<>();
            for (int i = 0; i < token.length(); i++) {
                String j = "S" + s + " T" + token.charAt(i);
                if (ts.contains(j)) out.add(j);
            }
            return out;
        }
        if (token.equals("ss")) return List.of("S1 SS");
        if (token.equals("i4")) return List.of("S4 Target");
        if (token.equals("i2")) return List.of("S2 Lights");
        if (token.equals("i3")) return List.of("S3 Arrows");
        if (token.equals("lights")) return List.of("S2 Lights");
        if (token.equals("arrows")) return List.of("S3 Arrows");
        if (token.equals("dev")) return List.of(device(s));
        if (token.equals("ll")) return List.of(getValue(LEFT, s));
        if (token.equals("rl")) return List.of(getValue(RIGHT, s));
        if (token.equals("bl") || token.equals("levers")) return List.of(getValue(LEFT, s), getValue(RIGHT, s));
        if (token.equals("terms")) return terms(s);
        if (token.equals("gate") && s <= 3) return List.of("gate " + s);
        return Collections.emptyList();
    }

    private static String getValue(Map<Integer, String> m, int k) {
        String v = m.get(k);
        if (v == null) throw new java.util.NoSuchElementException("Key " + k + " is missing in the map.");
        return v;
    }

    private static final Pattern PARENS = Pattern.compile("\\([^)]*\\)");
    private static final Pattern WS = Pattern.compile("\\s+");

    private static List<String> tokens(String line) {
        String cleaned = PARENS.matcher(line).replaceAll(" ").trim();
        List<String> out = new ArrayList<>();
        for (String t : WS.split(cleaned, -1)) if (!t.isEmpty()) out.add(t.toLowerCase(Locale.ROOT));
        return out;
    }

    private static List<String> splitKeep(String s, char c) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        out.add(s.substring(start));
        return out;
    }

    private static Plan parse(Preset p) {
        LinkedHashMap<String, List<DungeonClass>> owners = new LinkedHashMap<>();
        LinkedHashMap<DungeonClass, List<String>> jobsOf = new LinkedHashMap<>();
        HashMap<Integer, DungeonClass> ee = new HashMap<>();
        HashMap<Integer, List<DungeonClass>> leverFirst = new HashMap<>();
        HashMap<Integer, List<DungeonClass>> leverRight = new HashMap<>();
        for (Map.Entry<DungeonClass, String> e : p.roles.entrySet()) {
            DungeonClass c = e.getKey();
            String line = e.getValue();
            List<String> mine = jobsOf.computeIfAbsent(c, k -> new ArrayList<>());
            List<String> parts = splitKeep(line, '/');
            for (int i = 0; i < parts.size(); i++) {
                int s = i + 1;
                if (s > 4) continue;
                for (String t : tokens(parts.get(i))) {
                    if (t.startsWith("ee") && toIntOrNull(t.substring(2)) != null) ee.put(toIntOrNull(t.substring(2)), c);
                    else if (t.startsWith("hee") && toIntOrNull(t.substring(3)) != null) ee.put(toIntOrNull(t.substring(3)), c);
                    else if (t.equals("core")) ee.put(5, c);
                    if (t.equals("ll") || t.equals("bl")) leverFirst.computeIfAbsent(s, k -> new ArrayList<>()).add(c);
                    if (t.equals("rl")) leverRight.computeIfAbsent(s, k -> new ArrayList<>()).add(c);
                    for (String j : jobs(t, s)) {
                        if (!mine.contains(j)) mine.add(j);
                        List<DungeonClass> o = owners.computeIfAbsent(j, k -> new ArrayList<>());
                        if (!o.contains(c)) o.add(c);
                    }
                }
            }
        }
        for (int s = 1; s <= 3; s++) {
            if (owners.containsKey("gate " + s)) continue;
            List<DungeonClass> who = leverFirst.get(s);
            if (who == null) who = leverRight.get(s);
            if (who == null) continue;
            List<DungeonClass> distinct = new ArrayList<>();
            for (DungeonClass c : who) if (!distinct.contains(c)) distinct.add(c);
            for (DungeonClass c : distinct) {
                List<String> js = jobsOf.get(c);
                if (js == null) throw new java.util.NoSuchElementException("Key " + c + " is missing in the map.");
                js.add("gate " + s);
                owners.computeIfAbsent("gate " + s, k -> new ArrayList<>()).add(c);
            }
        }
        HashMap<String, Pair<Integer, Double>> times = new HashMap<>();
        for (int i = 0; i < p.times.size(); i++) {
            int s = i + 1;
            for (String group : splitKeep(p.times.get(i), '|')) {
                List<String> ts = tokens(group);
                Double at = ts.isEmpty() ? null : toDoubleOrNull(ts.get(ts.size() - 1));
                if (at == null) continue;
                for (String tok : ts.subList(0, ts.size() - 1))
                    for (String j : jobs(tok, s)) times.put(j, Pair.of(s, at));
            }
        }
        ArrayList<Move> moves = new ArrayList<>();
        for (int i = 0; i < p.moves.size(); i++) {
            int s = i + 1;
            for (String group : splitKeep(p.moves.get(i), '|')) {
                List<String> ts = tokens(group);
                if (ts.size() >= 2 && ts.get(0).equals("leap")) moves.add(new Move(s, "preleap", ts.get(1), ts.size() > 2 ? toDoubleOrNull(ts.get(2)) : null));
                else if (ts.size() >= 2 && ts.get(0).equals("hold")) moves.add(new Move(s, "hold", ts.get(1), null));
                else if (ts.size() >= 2) moves.add(new Move(s, ts.get(1), ts.get(0), ts.size() > 2 ? toDoubleOrNull(ts.get(2)) : null, new ArrayList<>(ts.subList(2, ts.size()))));
            }
        }
        return new Plan(owners, jobsOf, ee, times, moves);
    }

    public static DungeonClass whoIs(Plan plan, String token) {
        if (token.startsWith("ee")) {
            Integer k = toIntOrNull(token.substring(2));
            return k == null ? null : plan.ee.get(k);
        }
        if (token.equals("core")) return plan.ee.get(5);
        List<String> js = jobs(token, 1);
        if (js.isEmpty()) return null;
        List<DungeonClass> o = plan.owners.get(js.get(0));
        return o == null || o.isEmpty() ? null : o.get(0);
    }

    public static String label(DungeonClass c) {
        String s = c.name().toLowerCase(Locale.ROOT);
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
