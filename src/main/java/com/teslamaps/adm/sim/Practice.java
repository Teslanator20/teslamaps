package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/Practice.kt

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.teslamaps.adm.sim.util.Pair;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Practice {
    private Practice() {}

    public static class Task {
        public final String id;
        public final String label;
        public volatile int at = -1;

        public Task(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    public static volatile String mode = null;
    public static volatile int section = 0;
    public static volatile List<Task> tasks = Collections.emptyList();
    public static volatile int ticks = 0;
    public static volatile int endTicks = -1;
    public static volatile long endMs = 0L;

    public static boolean active() { return mode != null; }

    public static class Setup {
        public final String name;
        public final int section;
        public final Spots.Spot start;
        public final List<String> jobs;
        public final List<Spots.Spot> checkpoints;
        public final Double delay;
        public final String item;
        public final List<BlockPos> mines;
        public final long restartMs;

        public Setup(String name, int section, Spots.Spot start, List<String> jobs, List<Spots.Spot> checkpoints, Double delay,
                     String item, List<BlockPos> mines, long restartMs) {
            this.name = name;
            this.section = section;
            this.start = start;
            this.jobs = jobs;
            this.checkpoints = checkpoints;
            this.delay = delay;
            this.item = item;
            this.mines = mines;
            this.restartMs = restartMs;
        }

        public Setup(String name, int section, Spots.Spot start, List<String> jobs, List<Spots.Spot> checkpoints, Double delay, String item, List<BlockPos> mines) {
            this(name, section, start, jobs, checkpoints, delay, item, mines, 250);
        }

        public Setup(String name, int section, Spots.Spot start, List<String> jobs, List<Spots.Spot> checkpoints, Double delay, String item) {
            this(name, section, start, jobs, checkpoints, delay, item, List.of(), 250);
        }

        public Setup(String name, int section, Spots.Spot start, List<String> jobs, List<Spots.Spot> checkpoints, Double delay) {
            this(name, section, start, jobs, checkpoints, delay, null, List.of(), 250);
        }

        public Setup(String name, int section, Spots.Spot start, List<String> jobs, List<Spots.Spot> checkpoints) {
            this(name, section, start, jobs, checkpoints, null, null, List.of(), 250);
        }

        public Setup(String name, int section, Spots.Spot start, List<String> jobs) {
            this(name, section, start, jobs, List.of(), null, null, List.of(), 250);
        }
    }

    public static final String FIRST_BOW = "first bow";
    private static final String BREAKER = "DUNGEONBREAKER";
    private static final String BONZO = "STARRED_BONZO_STAFF";

    private static List<BlockPos> HEE3_MINES() {
        List<BlockPos> out = new ArrayList<>();
        for (int x : new int[] {15, 16}) for (int z : new int[] {93, 94}) out.add(new BlockPos(x, 122, z));
        return out;
    }

    private static Setup coded(int section, String name, String code, String item, List<String> jobs, long restartMs, Spots.Spot start) {
        PracticeCode.Custom c = PracticeCode.decode(code);
        if (c == null) {
            Ec.LOG.warn("[p3sim] practice preset {}: bad code {}", name, code);
            return null;
        }
        List<String> picked = jobs;
        if (picked == null) {
            picked = new ArrayList<>();
            for (String it : P3Plan.allJobs()) if (c.jobs.contains(it)) picked.add(it);
        }
        Spots.Spot st = start != null ? start : c.start;
        if (st == null) return null;
        return new Setup(name, section, st, picked, c.checkpoints, c.delay, item, List.of(), restartMs);
    }

    private static Setup coded(int section, String name, String code, String item) { return coded(section, name, code, item, null, 250, null); }

    private static List<Setup> codedPresets = null;

    private static List<Setup> codedPresets() {
        if (codedPresets != null) return codedPresets;
        List<Setup> all = new ArrayList<>();
        Collections.addAll(all,
            coded(1, "123", "$b4RW/r$6-sy!h8", BREAKER),
            coded(1, "43 bl", "Pu0y8Q2!sz]64YV!", BREAKER),
            coded(1, "i3 21", "yq&oxU6Wr3h49kw", BREAKER),
            coded(1, "i4", "1F?x8!EwzD)T=q", FIRST_BOW, null, 700, p(63.07, 127.00, 35.08, -12.25f, -0.99f)),
            coded(2, "ee3", "4VT^EC9D6Qi+=DN9E5yAi", BONZO),
            coded(2, "cee3", "6tNQ?NDFPO??+/!(m8Y", BONZO, null, 250, p(36.03, 131.00, 139.01, 92.64f, 3.45f)),
            coded(2, "i2 -> gbl 4", "2)n^DVlEAIg)].{gDhkKGT7", BREAKER),
            coded(2, "i2 -> 53", "c0Ogu/8N9bm$+d", BREAKER),
            coded(2, "i2 -> 2", "AwwF19X[hNEXi4GvPpxJ", BONZO),
            coded(2, "ee2 -> 4 rl", "4VV3(]4y?RkzMM!0O2j8W", BREAKER),
            coded(2, "ee2 -> 53", "cVx+bCagN)P7c-", BONZO),
            coded(2, "ee2 -> 23", "cVx+bCagN)P7V}", BREAKER),
            coded(2, "ee2 -> 53 core", "4VV3(]4y?}3?tk}Z1vXtL", BONZO),
            coded(2, "ee2 -> 23 core", "4VV3(]4y?}3?s)SzW9E7R", BREAKER, List.of("S2 T2", "S2 T3"), 250, null),
            coded(2, "3 -> core", "6qVDsKr0.Ni]JM)q5m4", BREAKER),
            coded(2, "2 -> core", "6t431)u?S&6POjy(s7/", BREAKER),
            coded(2, "2 -> 23", "cUIyVshnIF6L[7", BREAKER),
            coded(2, "5 -> 53", "c6Ecf4/uscbw2n", BREAKER),
            coded(4, "1", "A&XXR!e.OWkVS+.gHRxy", BREAKER),
            coded(4, "2", "1FiRWUShfWt!!}", BREAKER),
            coded(4, "3", "1FiRWUShfWt!$F", BREAKER),
            coded(4, "4", "1FiRWUShfWt!$V", BREAKER),
            coded(4, "bl", "cN^89NinzJlbXT", BREAKER),
            coded(4, "3 bl", "!4&OC5EOpIxz5%4", BREAKER),
            coded(4, "4 bl", "!4&OC5EOpIxz8YZ", BREAKER),
            coded(4, "1 -> 2", "1F30?WCBnGmO}}", BREAKER),
            coded(4, "3 -> 3 bl", "!V8vZu}h7d{Y0r.", BREAKER),
            coded(4, "3 -> bl", "!V8vZu}h7d{Y0r.", BONZO, List.of("S4 low lever", "S4 high lever"), 250, null),
            coded(4, "4 -> 4 bl", "!gOrno7IArJ3^&j", BREAKER),
            coded(4, "4 -> bl", "cl7&FG8D3dpRat", BONZO)
        );
        List<Setup> out = new ArrayList<>();
        for (Setup s : all) if (s != null) out.add(s);
        codedPresets = out;
        return out;
    }

    private static volatile Setup setup = null;

    private static boolean custom() { return setup != null; }

    private static List<Spots.Spot> checkpointsNow() {
        Setup s = setup;
        return s != null ? s.checkpoints : List.of();
    }

    private static Spots.Spot p(double x, double y, double z, float yaw, float pitch) { return new Spots.Spot("Start", x, y, z, yaw, pitch); }

    public static List<Setup> presets() {
        List<Setup> out = new ArrayList<>();
        for (Setup s : codedPresets()) if (s.section == 1) out.add(s);
        out.add(new Setup("4 3", 1, p(108.20, 120.00, 94.00, -90.41f, 2.79f), List.of("S1 T4", "S1 T3"), List.of(), 3.0));
        out.add(new Setup("2 1", 1, p(108.20, 120.00, 94.00, -90.41f, 2.79f), List.of("S1 T2", "S1 T1"), List.of(), 3.0));
        out.add(new Setup("ee2", 1, p(108.20, 120.00, 94.00, -90.41f, 2.79f), List.of("S1 west lever", "S1 east lever", "gate 1", "S2 Lights"),
            List.of(new Spots.Spot("EE2", 60.53, 132.00, 138.98, 0f)), 3.0));
        for (Setup s : codedPresets()) if (s.section == 2) out.add(s);
        out.add(new Setup("2 -> 1", 2, p(59.47, 120.00, 125.98, -179.50f, 23.01f), List.of("S2 T1"), List.of(new Spots.Spot("Checkpoint", 69.70, 109.00, 122.43, 0f)), 0.0));
        out.add(new Setup("2 -> 3", 2, p(59.47, 120.00, 125.98, -179.50f, 23.01f), List.of("S2 T3"), List.of(), 0.0));
        out.add(new Setup("1 bl", 3, p(2.00, 109.00, 102.00, -175.24f, 5.83f), List.of("S3 T1", "S3 west lever", "S3 east lever"), List.of(), 0.0, BREAKER));
        out.add(new Setup("4 bl", 3, p(2.00, 109.00, 102.00, -175.24f, 5.83f), List.of("S3 T4", "S3 west lever", "S3 east lever", "gate 3"), List.of(), 0.0, BREAKER));
        out.add(new Setup("2 dev", 3, p(2.00, 109.00, 102.00, -175.24f, 5.83f), List.of("S3 T2", "S3 Arrows"), List.of(), 0.0, BREAKER));
        out.add(new Setup("3 dev", 3, p(2.00, 109.00, 102.00, -175.24f, 5.83f), List.of("S3 T3", "S3 Arrows"), List.of(new Spots.Spot("CEE3", 18.70, 121.50, 91.30, 0f)), 0.0, BONZO));
        out.add(new Setup("c -> 1", 3, p(18.70, 121.50, 91.30, 1.15f, -7.73f), List.of("S3 T1"), List.of(), 0.0, BREAKER, HEE3_MINES()));
        out.add(new Setup("c -> 2", 3, p(18.70, 121.50, 91.30, 1.15f, -7.73f), List.of("S3 T2"), List.of(), 0.0, BONZO, HEE3_MINES()));
        out.add(new Setup("c -> i3 4", 3, p(18.70, 121.50, 91.30, 1.15f, -7.73f), List.of("S3 Arrows", "S3 T4"), List.of(), 0.0, BONZO, HEE3_MINES()));
        out.add(new Setup("c -> 3 bl", 3, p(18.70, 121.50, 91.30, 1.15f, -7.73f), List.of("S3 T3", "S3 west lever", "S3 east lever"), List.of(), 0.0, BREAKER, HEE3_MINES()));
        out.add(new Setup("c -> i3 4 bl", 3, p(18.70, 121.50, 91.30, 1.15f, -7.73f), List.of("S3 Arrows", "S3 T4", "S3 west lever", "S3 east lever"), List.of(), 0.0, BONZO, HEE3_MINES()));
        out.add(new Setup("4 -> 4 BL", 3, p(0.97, 109.00, 77.89, 96.99f, 19.23f), List.of("S3 T4", "S3 west lever", "S3 east lever"), List.of(), 0.0, BREAKER));
        out.add(new Setup("4 -> BL", 3, p(0.97, 109.00, 77.89, 96.99f, 19.23f), List.of("S3 west lever", "S3 east lever"), List.of(), 0.0, BONZO));
        for (Setup s : codedPresets()) if (s.section > 3) out.add(s);
        return out;
    }

    private static Map<Integer, List<Pair<String, List<String>>>> GROUPS() {
        Map<Integer, List<Pair<String, List<String>>>> m = new LinkedHashMap<>();
        m.put(1, List.of(Pair.of("Terms", List.of("123", "2 1", "4 3", "43 bl")), Pair.of("Devs / EE", List.of("i4", "i3 21", "ee2"))));
        m.put(2, List.of(
            Pair.of("EE2 ->", List.of("ee2 -> 23", "ee2 -> 53", "ee2 -> 4 rl", "ee2 -> 23 core", "ee2 -> 53 core")),
            Pair.of("i2 ->", List.of("i2 -> 2", "i2 -> 53", "i2 -> gbl 4")),
            Pair.of("To EE3", List.of("ee3", "cee3")),
            Pair.of("Term ->", List.of("2 -> 1", "2 -> 3", "2 -> 23", "5 -> 53", "2 -> core", "3 -> core"))
        ));
        m.put(3, List.of(
            Pair.of("EE3 ->", List.of("1 bl", "4 bl", "2 dev", "3 dev")),
            Pair.of("CEE3 ->", List.of("c -> 1", "c -> 2", "c -> i3 4", "c -> 3 bl", "c -> i3 4 bl")),
            Pair.of("4 ->", List.of("4 -> 4 BL", "4 -> BL"))
        ));
        m.put(4, List.of(
            Pair.of("Core ->", List.of("1", "2", "3", "4")),
            Pair.of("Core -> BL", List.of("bl", "3 bl", "4 bl")),
            Pair.of("Term ->", List.of("1 -> 2", "3 -> 3 bl", "3 -> bl", "4 -> 4 bl", "4 -> bl"))
        ));
        return m;
    }

    public static List<Pair<String, List<Setup>>> presetGroups(int s) {
        List<Setup> all = new ArrayList<>();
        for (Setup it : presets()) if (it.section == s) all.add(it);
        Map<String, Setup> byName = new LinkedHashMap<>();
        for (Setup it : all) byName.put(it.name, it);
        List<Pair<String, List<Setup>>> groups = new ArrayList<>();
        List<Pair<String, List<String>>> g = GROUPS().get(s);
        if (g != null) for (Pair<String, List<String>> hn : g) {
            List<Setup> l = new ArrayList<>();
            for (String n : hn.second) { Setup x = byName.get(n); if (x != null) l.add(x); }
            if (!l.isEmpty()) groups.add(Pair.of(hn.first, l));
        }
        Set<Setup> listed = new HashSet<>();
        for (Pair<String, List<Setup>> it : groups) listed.addAll(it.second);
        List<Setup> rest = new ArrayList<>();
        for (Setup it : all) if (!listed.contains(it)) rest.add(it);
        if (rest.isEmpty()) return groups;
        List<Pair<String, List<Setup>>> out = new ArrayList<>(groups);
        out.add(Pair.of("Other", rest));
        return out;
    }

    public static void startPreset(String name) {
        for (Setup it : presets()) if (it.name.equals(name)) { launch(it); return; }
    }

    private static P3Plan.EarlyEnter ee = null;

    public static void start(int s) { setup = null; launch("S" + s, s); }

    public static void startCustom() {
        Spots.Spot at = customStart;
        if (at == null || customSection < 1 || customSection > 4) { Sim.note("Set the custom practice's §fStart Position§7 first (Practice tab)."); return; }
        List<String> jobs = new ArrayList<>();
        for (int s = 1; s <= 4; s++) for (String it : P3Plan.jobsIn(s)) if (customJobs.contains(it)) jobs.add(it);
        int s = customSection;
        for (String it : jobs) { int x = sectionOf(it); if (x >= 1 && x <= 4) s = Math.min(s, x); }
        launch(new Setup("Custom", s, at, jobs, new ArrayList<>(checkpoints)));
    }

    private static void launch(Setup set) { setup = set; launch(set.name, set.section); }

    private static void launch(String m, int s) {
        mode = m; section = s;
        tasks = Collections.emptyList(); ticks = 0; endTicks = -1;
        SimHooks.termClearTime();
        startedMs = System.currentTimeMillis();
        Fight.start(List.of(Fight.Start.S1, Fight.Start.S2, Fight.Start.S3, Fight.Start.S4).get(s - 1), true);
        Setup set = setup;
        if (set != null) for (BlockPos it : set.mines) Blocks.set(it, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        Fight.later(NO_BREAK_TICKS + 1, "practice blocks back", () -> SimItems.unbreakAll());
    }

    private static volatile long startedMs = 0L;
    private static final long NO_BREAK_MS = 150L;
    private static final int NO_BREAK_TICKS = 3;

    public static boolean noBreaking() { return active() && System.currentTimeMillis() - startedMs < NO_BREAK_MS; }

    public static void restart() {
        Setup set = setup;
        if (set != null && set.name.equals("Custom")) startCustom();
        else if (set != null) launch(set);
        else if (section >= 1 && section <= 4) start(section);
    }

    public static boolean clickRestarts() {
        Setup set = setup;
        return active() && endTicks >= 0 && System.currentTimeMillis() - endMs >= (set != null ? set.restartMs : 250);
    }

    public static void exit() { mode = null; section = 0; setup = null; tasks = Collections.emptyList(); endTicks = -1; }

    public static List<String> jobs(int s) {
        Map<String, Pair<Integer, Double>> times = P3Plan.plan().times;
        List<String> out = new ArrayList<>();
        for (String it : P3Plan.allJobs()) {
            if (!P3Plan.isMine(it)) continue;
            Pair<Integer, Double> t = times.get(it);
            int sec = t != null ? t.first : sectionOf(it);
            if (sec == s) out.add(it);
        }
        out.sort(Comparator.comparingDouble(it -> { Pair<Integer, Double> t = times.get(it); return t != null ? t.second : 99.0; }));
        return out;
    }

    public static List<String> practiceJobs() {
        Setup set = setup;
        return set != null ? set.jobs : jobs(section);
    }

    public static int lastSection() {
        int m = section;
        for (String it : practiceJobs()) { int x = sectionOf(it); if (x >= 1 && x <= 4) m = Math.max(m, x); }
        return m;
    }

    private static Integer toIntOrNull(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static int sectionOf(String job) {
        Integer g = toIntOrNull(job.startsWith("gate ") ? job.substring(5) : job);
        if (g != null) return g;
        String r = job.startsWith("S") ? job.substring(1) : job;
        int sp = r.indexOf(' ');
        Integer s = toIntOrNull(sp < 0 ? r : r.substring(0, sp));
        return s != null ? s : 0;
    }

    public static Spots.Spot startSpot() {
        Setup set = setup;
        if (set != null && set.start != null) return set.start;
        int s = section;
        if (s == 1) {
            Spots.Spot c = P3Plan.customSpot("spawn");
            return c != null ? c : Spots.p3Start(1);
        }
        for (P3Plan.EarlyEnter it : P3Plan.earlyEnters) {
            if (it.into == s && (s != 4 || it.on())) return P3Plan.eeSpot(it);
        }
        return Spots.p3Start(s);
    }

    public static volatile double startDelay = 0.0;

    public static int holdTicks() {
        Setup set = setup;
        return (int) Math.round((set != null && set.delay != null ? set.delay : startDelay) * 20);
    }

    public static void setStartDelay(double v) { startDelay = Math.clamp(v, 0.0, 5.0); save(); }

    private static int startT = 0;

    public static void hold(int n) { tasks = Collections.emptyList(); ticks = -n; endTicks = -1; }

    public static void holdTick(int t, int n) { ticks = t - n; }

    public static void begin(int s, int t) {
        startT = t;
        List<Task> list = new ArrayList<>();
        for (String it : practiceJobs()) list.add(new Task(it, label(it)));
        ee = null;
        if (custom()) {
            List<Spots.Spot> cps = checkpointsNow();
            for (int i = 0; i < cps.size(); i++) {
                Spots.Spot c = cps.get(i);
                list.add(new Task("cp " + i, c.name.startsWith("Checkpoint") || c.name.equals("set") ? "Checkpoint " + (i + 1) : c.name));
            }
        } else {
            P3Plan.EarlyEnter e = P3Plan.ee(s == 4 ? 5 : s + 1);
            ee = e != null && e.byYou() ? e : null;
            if (ee != null) list.add(new Task("ee " + ee.key, ee.label));
        }
        tasks = list; ticks = 0; endTicks = -1;
        if (list.isEmpty()) Sim.note("Practice §f" + mode + "§7: nothing to do in it.");
        else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) { if (i > 0) sb.append("§8, §7"); sb.append(list.get(i).label); }
            Sim.note("Practice §f" + mode + "§7: " + sb + ".");
        }
    }

    private static String label(String job) {
        if (job.startsWith("gate ")) return "Gate";
        int sp = job.indexOf(' ');
        String r = sp < 0 ? job : job.substring(sp + 1);
        if (r.isEmpty()) return r;
        return r.substring(0, 1).toUpperCase(Locale.ROOT) + r.substring(1);
    }

    public static void tick(GoldorPhase phase) {
        if (endTicks >= 0 || tasks.isEmpty()) return;
        int now = phase.t - startT;
        ticks = now;
        ServerPlayer p = Sim.player();
        List<Task> ts = tasks;
        for (int i = 0; i < ts.size(); i++) {
            Task task = ts.get(i);
            if (task.at >= 0) continue;
            boolean done;
            if (task.id.startsWith("gate ")) done = phase.gateIsDown(Integer.parseInt(task.id.substring(5)));
            else if (task.id.startsWith("ee ")) {
                boolean allOthers = true;
                for (Task it : ts) if (it != task && it.at < 0) { allOthers = false; break; }
                P3Plan.EarlyEnter e = ee;
                done = p != null && allOthers && e != null && onSpot(P3Plan.eeSpot(e), 1.5, p.getX(), p.getY(), p.getZ());
            } else if (task.id.startsWith("cp ")) {
                boolean noneBefore = true;
                for (Task it : ts.subList(0, i)) if (it.id.startsWith("cp ") && it.at < 0) { noneBefore = false; break; }
                List<Spots.Spot> cps = checkpointsNow();
                int idx = Integer.parseInt(task.id.substring(3));
                Spots.Spot cp = idx >= 0 && idx < cps.size() ? cps.get(idx) : null;
                done = p != null && noneBefore && cp != null && onSpot(cp, 1.0, p.getX(), p.getY(), p.getZ());
            } else {
                Station st = null;
                for (Station it : phase.stations) if (it.id().equals(task.id)) { st = it; break; }
                done = st != null && st.done;
            }
            if (done) task.at = now;
        }
        boolean all = true;
        for (Task it : ts) if (it.at < 0) { all = false; break; }
        if (all) finish();
    }

    private static boolean onSpot(Spots.Spot s, double r, double x, double y, double z) {
        double dx = x - s.x, dz = z - s.z;
        return dx * dx + dz * dz <= r * r && (long) Math.floor(y * 100 + 1e-6) == (long) Math.floor(s.y * 100 + 1e-6);
    }

    private static void finish() {
        int end = Integer.MIN_VALUE;
        for (Task it : tasks) end = Math.max(end, it.at);
        if (tasks.isEmpty()) throw new java.util.NoSuchElementException();
        ticks = end; endTicks = end; endMs = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        List<Task> ts = tasks;
        for (int i = 0; i < ts.size(); i++) { if (i > 0) sb.append("§8, §7"); sb.append(ts.get(i).label).append(" §f").append(secs(ts.get(i).at)); }
        Sim.note("Practice §f" + mode + "§7 " + secs(end) + "s" + Stats.best("Practice " + mode, end) + "§7: " + sb);
    }

    public static String secs(int ticks) { return String.format(Locale.ROOT, "%.2f", ticks / 20.0); }

    public static Spots.Spot leapSpot(DungeonClass c) {
        int s = Fight.phase instanceof GoldorPhase g ? g.section : section;
        s = Math.clamp(s, 1, 4);
        return leapSpots().get(s - 1).get(c);
    }

    private static List<Map<DungeonClass, Spots.Spot>> leapSpots() {
        return List.of(
            Map.of(
                DungeonClass.ARCHER, new Spots.Spot("Archer", 94.04, 112.00, 99.26, 60.01f, -3.13f),
                DungeonClass.BERSERK, new Spots.Spot("Berserk", 93.56, 112.00, 93.29, 103.97f, 15.53f),
                DungeonClass.MAGE, new Spots.Spot("Mage", 106.77, 122.00, 111.70, 8.30f, -19.24f),
                DungeonClass.TANK, new Spots.Spot("Tank", 110.70, 113.00, 75.34, -178.85f, 22.77f),
                DungeonClass.HEALER, new Spots.Spot("Healer", 108.30, 120.00, 94.01, -90.08f, -0.99f)
            ),
            Map.of(
                DungeonClass.ARCHER, new Spots.Spot("Archer", 39.12, 109.00, 139.87, -6.83f, 33.13f),
                DungeonClass.BERSERK, new Spots.Spot("Berserk", 40.78, 109.00, 123.30, -176.88f, 26.63f),
                DungeonClass.MAGE, new Spots.Spot("Mage", 59.46, 120.00, 125.78, -179.76f, 22.20f),
                DungeonClass.TANK, new Spots.Spot("Tank", 68.46, 109.00, 125.21, -179.43f, 18.33f),
                DungeonClass.HEALER, new Spots.Spot("Healer", 24.95, 131.06, 138.57, 90.40f, 18.91f)
            ),
            Map.of(
                DungeonClass.ARCHER, new Spots.Spot("Archer", 1.01, 109.00, 77.81, 95.00f, 19.81f),
                DungeonClass.BERSERK, new Spots.Spot("Berserk", 15.67, 123.00, 93.65, -90.00f, 19.40f),
                DungeonClass.MAGE, new Spots.Spot("Mage", 18.70, 121.50, 91.30, 0.58f, -4.61f),
                DungeonClass.TANK, new Spots.Spot("Tank", 1.35, 109.00, 112.32, 87.20f, 18.41f),
                DungeonClass.HEALER, new Spots.Spot("Healer", 0.87, 119.00, 93.41, 89.42f, 19.15f)
            ),
            Map.of(
                DungeonClass.ARCHER, new Spots.Spot("Archer", 44.45, 121.00, 31.81, -177.77f, 30.98f),
                DungeonClass.BERSERK, new Spots.Spot("Berserk", 67.23, 109.00, 33.09, -178.02f, 18.65f),
                DungeonClass.MAGE, new Spots.Spot("Mage", 54.53, 115.06, 50.51, -179.57f, 2.38f),
                DungeonClass.TANK, new Spots.Spot("Tank", 41.49, 109.00, 33.04, 179.43f, 18.16f),
                DungeonClass.HEALER, new Spots.Spot("Healer", 72.46, 115.00, 45.79, 0.18f, 27.20f)
            )
        );
    }

    public static volatile Spots.Spot customStart = null;
    public static volatile String customItem = null;
    public static volatile int customSection = 0;
    public static final LinkedHashSet<String> customJobs = new LinkedHashSet<>();
    public static final ArrayList<Spots.Spot> checkpoints = new ArrayList<>();

    public static int sectionAt(double x, double y, double z) {
        Vec3 v = new Vec3(x, y, z);
        int zone = GoldorPhase.dtZone(v);
        if (zone >= 1 && zone <= 4) return zone;
        Pair<Integer, AABB> best = null;
        double bestD = 0;
        for (Pair<Integer, AABB> it : GoldorPhase.DT_ZONES) {
            double d = it.second.distanceToSqr(v);
            if (best == null || d < bestD) { best = it; bestD = d; }
        }
        if (best == null) throw new java.util.NoSuchElementException();
        return best.first;
    }

    public static Set<String> customJobsIn(int s) {
        Set<String> out = new LinkedHashSet<>();
        for (String it : P3Plan.jobsIn(s)) if (customJobs.contains(it)) out.add(it);
        return out;
    }

    public static int setStart(Spots.Spot spot) { return setStart(spot, null); }

    public static int setStart(Spots.Spot spot, String item) {
        int s = sectionAt(spot.x, spot.y, spot.z);
        customSection = s;
        customStart = spot;
        customItem = item;
        save();
        return s;
    }

    public static void toggleJob(String job) {
        if (!customJobs.remove(job)) customJobs.add(job);
        save();
    }

    public static void clearCustom() {
        customStart = null; customItem = null; customSection = 0; customJobs.clear(); checkpoints.clear(); leapWaypoints.clear(); startDelay = 0.0;
        save();
    }

    public static String itemKey(ItemStack s) {
        if (s.isEmpty()) return null;
        String id = SimItems.idOf(s);
        return id != null ? id : "name:" + s.getHoverName().getString();
    }

    public static void holdStartItem(ServerPlayer player) {
        Setup set = setup;
        if (set == null) return;
        String key = set.name.equals("Custom") ? customItem : set.item;
        if (key == null) return;
        Inventory inv = player.getInventory();
        Integer slot = null;
        for (int it = 0; it <= 8; it++) {
            ItemStack s = inv.getItem(it);
            boolean match = key.equals(FIRST_BOW) ? s.getItem() instanceof BowItem : key.equals(itemKey(s));
            if (match) { slot = it; break; }
        }
        if (slot == null) return;
        inv.setSelectedSlot(slot);
        player.connection.send(new ClientboundSetHeldSlotPacket(slot));
    }

    public static String exportCode() {
        return PracticeCode.encode(new PracticeCode.Custom(customStart, new LinkedHashSet<>(customJobs), new ArrayList<>(checkpoints), new ArrayList<>(leapWaypoints), startDelay));
    }

    public static boolean importCode(String code) {
        PracticeCode.Custom c = PracticeCode.decode(code);
        if (c == null) return false;
        customStart = c.start;
        customItem = null;
        customSection = c.start != null ? sectionAt(c.start.x, c.start.y, c.start.z) : 0;
        customJobs.clear(); customJobs.addAll(c.jobs);
        checkpoints.clear(); checkpoints.addAll(c.checkpoints);
        leapWaypoints.clear(); leapWaypoints.addAll(c.leaps);
        startDelay = c.delay;
        save();
        return true;
    }

    public static final ArrayList<Spots.Spot> leapWaypoints = new ArrayList<>();

    public static int addLeapWaypoint(Spots.Spot spot) {
        if (leapWaypoints.size() >= 4) return 0;
        leapWaypoints.add(spot);
        save();
        return leapWaypoints.size();
    }

    public static void removeLeapWaypoint() {
        if (!leapWaypoints.isEmpty()) leapWaypoints.remove(leapWaypoints.size() - 1);
        save();
    }

    public static Spots.Spot leapWaypoint(int i) {
        Setup set = setup;
        if (set != null && set.name.equals("Custom")) return i >= 0 && i < leapWaypoints.size() ? leapWaypoints.get(i) : null;
        return null;
    }

    public static void addCheckpoint(Spots.Spot spot) { checkpoints.add(spot); save(); }

    public static void removeCheckpoint() {
        if (!checkpoints.isEmpty()) checkpoints.remove(checkpoints.size() - 1);
        save();
    }

    private static class Saved {
        List<Double> start = null;
        Integer section = null;
        List<String> jobs = null;
        List<List<Double>> checkpoints = null;
        Double startDelay = null;
        List<List<Double>> leaps = null;
        String item = null;
        String menuTab = null;
        Integer menuSection = null;

        Saved() {}

        Saved(List<Double> start, Integer section, List<String> jobs, List<List<Double>> checkpoints, Double startDelay, List<List<Double>> leaps, String item,
              String menuTab, Integer menuSection) {
            this.start = start;
            this.section = section;
            this.jobs = jobs;
            this.checkpoints = checkpoints;
            this.startDelay = startDelay;
            this.leaps = leaps;
            this.item = item;
            this.menuTab = menuTab;
            this.menuSection = menuSection;
        }
    }

    public static volatile String menuTab = null;
    public static volatile int menuSection = 1;

    public static void saveMenu(String tab, int section) {
        if (java.util.Objects.equals(tab, menuTab) && section == menuSection) return;
        menuTab = tab; menuSection = section; save();
    }

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static File file() { return Ec.configDir().resolve("p3sim-practice.json").toFile(); }

    private static List<Double> list(Spots.Spot p) { return List.of(p.x, p.y, p.z, (double) p.yaw, (double) p.pitch); }

    private static Spots.Spot spot(String name, List<Double> v) { return new Spots.Spot(name, v.get(0), v.get(1), v.get(2), v.get(3).floatValue(), v.get(4).floatValue()); }

    public static void load() {
        Ec.safely("p3sim practice load", () -> {
            File f = file();
            if (!f.exists()) return;
            Saved s;
            try {
                s = gson.fromJson(Files.readString(f.toPath(), StandardCharsets.UTF_8), Saved.class);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            if (s == null) return;
            customStart = s.start != null && s.start.size() >= 5 ? spot("Start", s.start) : null;
            customSection = s.section != null ? s.section : 0;
            customItem = s.item;
            customJobs.clear(); if (s.jobs != null) customJobs.addAll(s.jobs);
            startDelay = s.startDelay != null ? s.startDelay : 0.0;
            checkpoints.clear();
            if (s.checkpoints != null) for (int i = 0; i < s.checkpoints.size(); i++) {
                List<Double> v = s.checkpoints.get(i);
                if (v.size() >= 5) checkpoints.add(spot("Checkpoint " + (i + 1), v));
            }
            leapWaypoints.clear();
            if (s.leaps != null) for (int i = 0; i < s.leaps.size(); i++) {
                List<Double> v = s.leaps.get(i);
                if (v.size() >= 5 && i < 4) leapWaypoints.add(spot("Leap " + (i + 1), v));
            }
            menuTab = s.menuTab;
            menuSection = s.menuSection != null && s.menuSection >= 1 && s.menuSection <= 4 ? s.menuSection : 1;
        });
    }

    private static void save() {
        Ec.safely("p3sim practice save", () -> {
            File f = file();
            f.getParentFile().mkdirs();
            List<List<Double>> cps = new ArrayList<>();
            for (Spots.Spot it : checkpoints) cps.add(list(it));
            List<List<Double>> lps = new ArrayList<>();
            for (Spots.Spot it : leapWaypoints) lps.add(list(it));
            Spots.Spot cs = customStart;
            String json = gson.toJson(new Saved(cs != null ? list(cs) : null, customSection, new ArrayList<>(customJobs), cps, startDelay, lps, customItem,
                menuTab, menuSection));
            try {
                Files.writeString(f.toPath(), json, StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }
}
