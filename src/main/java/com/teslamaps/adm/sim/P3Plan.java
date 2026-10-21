package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/P3Plan.kt

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.util.Pair;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class P3Plan {
    private P3Plan() {}

    public static final int RANDOM = 3;
    public static final List<String> SKILLS = skills();
    public static final int THEORETICAL = SKILLS.indexOf("Theoretical");
    private static final int PROTOTYPE_1 = SKILLS.indexOf("Prototype 1");
    public static final int NEAR_OPTIMAL = SKILLS.indexOf("Near Optimal");

    private static List<String> skills() {
        List<String> out = new ArrayList<>();
        List<Roles.Preset> presets = Roles.PRESETS;
        for (int i = 0; i < Math.min(RANDOM, presets.size()); i++) out.add(presets.get(i).name);
        out.add("Random");
        for (int i = RANDOM; i < presets.size(); i++) out.add(presets.get(i).name);
        return Collections.unmodifiableList(out);
    }

    public static class EarlyEnter {
        public final String key;
        public final String label;
        public final int into;
        public Vec3 spot;
        public float yaw;
        public float pitch;

        public EarlyEnter(String key, String label, int into, Vec3 spot, float yaw, float pitch) {
            this.key = key;
            this.label = label;
            this.into = into;
            this.spot = spot;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public EarlyEnter(String key, String label, int into, Vec3 spot, float yaw) { this(key, label, into, spot, yaw, 0f); }

        public EarlyEnter(String key, String label, int into, Vec3 spot) { this(key, label, into, spot, 0f, 0f); }

        public DungeonClass owner() { return plan().ee.get(into == 6 ? 5 : into); }

        public boolean on() { return owner() != null; }

        public boolean byYou() { return owner() != null && owner() == P3Sim.ownClass(); }
    }

    public static List<EarlyEnter> defaultEarlyEnters() {
        return List.of(
            new EarlyEnter("ee2", "EE2", 2, new Vec3(60.6, 132.0, 139.0)),
            new EarlyEnter("ee3", "EE3", 3, new Vec3(1.9, 109.0, 104.6)),
            new EarlyEnter("ee4", "EE4", 4, new Vec3(54.5, 115.06, 50.5)),
            new EarlyEnter("core", "Core", 5, new Vec3(54.5, 115.06, 50.5)),
            new EarlyEnter("recore", "Recore", 6, new Vec3(54.4, 115.0, 57.6))
        );
    }

    public static int skill = 1;
    private static final LinkedHashSet<String> mine = new LinkedHashSet<>();
    private static String mineFor = "";
    public static double botMin = 1.0;
    public static double botMax = 9.0;
    public static final List<EarlyEnter> earlyEnters = defaultEarlyEnters();
    public static boolean waitForYou = true;
    public static boolean helper = false;
    public static double leapGap = 0.5;
    public static volatile List<DungeonClass> leapOrder = Collections.emptyList();
    private static final Object leapLock = new Object();
    public static final LinkedHashSet<String> ghosts = new LinkedHashSet<>();

    private static HashMap<String, Spots.Spot> custom = new HashMap<>();

    private static HashMap<String, Spots.Spot> spots() {
        if (custom == null) custom = new HashMap<>();
        return custom;
    }

    private static String customKey(String key) { return skill + "/" + P3Sim.ownClass().name() + "/" + key; }

    public static Spots.Spot customSpot(String key) {
        Spots.Spot s = spots().get(customKey(key));
        return s != null ? s : defaultSpot(P3Sim.ownClass(), key);
    }

    public static boolean hasCustomSpot(String key) { return spots().containsKey(customKey(key)); }

    public static void setCustomSpot(String key, Spots.Spot p) {
        if (p == null) spots().remove(customKey(key)); else spots().put(customKey(key), p);
        save();
    }

    public static Spots.Spot defaultSpot(DungeonClass c, String key) {
        Spots.Spot d = preset().defaults.get(key + "/" + (c != null ? c.name() : "null"));
        if (d != null) return d;
        d = preset().defaults.get(key);
        if (d != null) return d;
        return switch (key) {
            case "spawn" -> {
                if (c == DungeonClass.BERSERK) yield new Spots.Spot("Spawn", 63.5, 127.0, 35.5, -11.18f, 0.33f);
                Spots.Spot early = earlyDeviceSpawn(c);
                yield early != null ? early : new Spots.Spot("Spawn", 108.3, 120.0, 94.0, 269.6f, 0.57f);
            }
            case "ee2" -> new Spots.Spot("EE2", 60.5, 132.0, 139.0, 90.47f, 1.64f);
            case "ee3" -> new Spots.Spot("EE3", 2.0, 109.0, 102.0, -175.24f, 5.83f);
            case "ee4" -> new Spots.Spot("EE4", 54.5, 115.06, 50.5, 179.91f, 0.57f);
            case "core" -> new Spots.Spot("Core", 54.5, 115.06, 50.5, 179.91f, 0.57f);
            case "recore" -> new Spots.Spot("Recore", 52.5, 115.0, 57.6, -159.12f, 2.63f);
            default -> null;
        };
    }

    private static Spots.Spot earlyDeviceSpawn(DungeonClass c) {
        Roles.Plan p = plan();
        String dev = null;
        for (String j : List.of("S4 Target", "S3 Arrows")) {
            Pair<Integer, Double> time = p.times.get(j);
            if (time == null || time.first != 1) continue;
            boolean ok;
            if (c == P3Sim.ownClass()) ok = isMine(j);
            else {
                List<DungeonClass> owners = p.owners.get(j);
                ok = owners != null && owners.contains(c);
            }
            if (ok) { dev = j; break; }
        }
        if (dev == null) return null;
        Vec3 at = Party.STANDS.get(dev);
        if (at == null) return null;
        Pair<Float, Float> look = Spots.startLook(dev);
        return new Spots.Spot("Spawn", at.x, at.y, at.z, look.first, look.second);
    }

    public static Spots.Spot eeSpot(EarlyEnter e) {
        Spots.Spot c = customSpot(e.key);
        if (c != null) return c;
        Vec3 ps = preset().spots.get(e.key);
        Vec3 it = ps != null ? ps : e.spot;
        return new Spots.Spot(e.label, it.x, it.y, it.z, e.yaw, e.pitch);
    }

    public static boolean ghostOn(DungeonClass c) { return ghosts.contains(c.name()); }

    public static void toggleGhost(DungeonClass c) {
        if (!ghosts.remove(c.name())) ghosts.add(c.name());
        save();
    }

    public static Roles.Preset preset() {
        int i;
        if (skill == RANDOM) i = 1;
        else if (skill > RANDOM) i = Math.min(skill - 1, Roles.PRESETS.size() - 1);
        else i = Math.max(skill, 0);
        return Roles.PRESETS.get(i);
    }

    public static Roles.Plan plan() { return Roles.plan(preset()); }

    public static String skillName() { return SKILLS.get(Math.clamp(skill, 0, SKILLS.size() - 1)); }

    public static Set<String> mine() {
        String key = skill + "/" + P3Sim.ownClass();
        if (!mineFor.equals(key)) {
            mineFor = key;
            mine.clear();
            List<String> own = ownMine;
            if (own != null && key.equals(ownMineFor)) mine.addAll(own);
            else {
                List<String> jobs = plan().jobsOf.get(P3Sim.ownClass());
                if (jobs != null) mine.addAll(jobs);
            }
        }
        return mine;
    }

    public static boolean isMine(String job) { return mine().contains(job); }

    public static void toggle(String job) {
        mine();
        if (!mine.remove(job)) mine.add(job);
        save();
    }

    public static void resetMine() {
        mineFor = "";
        mine();
        String prefix = skill + "/" + P3Sim.ownClass().name() + "/";
        pickMap().keySet().removeIf(it -> it.startsWith(prefix));
        stackSet().removeIf(it -> it.startsWith(prefix));
        save();
    }

    public static void chooseSkill(int i) {
        skill = Math.clamp(i, 0, SKILLS.size() - 1);
        mine();
        save();
    }

    public static EarlyEnter ee(int into) {
        for (EarlyEnter e : earlyEnters) if (e.into == into && e.on()) return e;
        return null;
    }

    public static boolean isStack(String job) {
        List<DungeonClass> o = plan().owners.get(job);
        return (o != null ? o.size() : 0) > 1;
    }

    public static DungeonClass doer(String job) {
        if (isMine(job)) return P3Sim.ownClass();
        DungeonClass p = picked(job);
        return p != null ? p : roleDoer(job);
    }

    public static DungeonClass roleDoer(String job) {
        List<DungeonClass> o = plan().owners.get(job);
        if (o == null) return null;
        for (DungeonClass c : o) if (c != P3Sim.ownClass()) return c;
        return null;
    }

    public static DungeonClass roleDoerBot(String job) {
        List<DungeonClass> o = plan().owners.get(job);
        if (o == null) return null;
        for (DungeonClass c : o) if (!Players.isPlayerClass(c)) return c;
        return null;
    }

    private static HashMap<String, String> picks = new HashMap<>();
    private static HashSet<String> stacked = new HashSet<>();

    private static HashMap<String, String> pickMap() {
        if (picks == null) picks = new HashMap<>();
        return picks;
    }

    private static HashSet<String> stackSet() {
        if (stacked == null) stacked = new HashSet<>();
        return stacked;
    }

    private static String jobKey(String job) { return skill + "/" + P3Sim.ownClass().name() + "/" + job; }

    private static DungeonClass picked(String job) {
        String n = pickMap().get(jobKey(job));
        if (n == null) return null;
        for (DungeonClass c : Party.CLASSES()) if (c.name().equals(n) && c != P3Sim.ownClass()) return c;
        return null;
    }

    public static void cycleDoer(String job) {
        List<DungeonClass> bots = new ArrayList<>();
        for (DungeonClass c : Party.CLASSES()) if (c != P3Sim.ownClass()) bots.add(c);
        DungeonClass next = bots.get((bots.indexOf(doer(job)) + 1) % bots.size());
        if (next == roleDoer(job)) pickMap().remove(jobKey(job)); else pickMap().put(jobKey(job), next.name());
        save();
    }

    public static boolean isStacked(String job) {
        return isStack(job) ? !stackSet().contains("!" + jobKey(job)) : stackSet().contains(jobKey(job));
    }

    public static void toggleStacked(String job) {
        String key = isStack(job) ? "!" + jobKey(job) : jobKey(job);
        if (!stackSet().remove(key)) stackSet().add(key);
        save();
    }

    public static DungeonClass helperOf(String job) {
        DungeonClass p = picked(job);
        if (p != null) return p;
        DungeonClass r = roleDoer(job);
        if (r != null) return r;
        for (DungeonClass c : Party.CLASSES()) if (c != P3Sim.ownClass()) return c;
        return null;
    }

    public static List<String> allJobs() {
        List<String> out = new ArrayList<>();
        for (int s = 1; s <= 4; s++) out.addAll(jobsIn(s));
        return out;
    }

    public static List<String> jobsIn(int s) {
        List<String> out = new ArrayList<>();
        for (Station st : Station.all()) if (st.section == s) out.add(st.id());
        if (s <= 3) out.add("gate " + s);
        return out;
    }

    public static String short_(String job) {
        if (job.startsWith("gate")) return "G";
        if (Roles.LEFT.containsValue(job)) return "L";
        if (Roles.RIGHT.containsValue(job)) return "R";
        int sp = job.indexOf(' ');
        String name = sp < 0 ? job : job.substring(sp + 1);
        if (name.length() > 1 && name.charAt(0) == 'T') {
            boolean digits = true;
            for (int i = 1; i < name.length(); i++) if (!Character.isDigit(name.charAt(i))) { digits = false; break; }
            if (digits) return name.substring(1);
        }
        return "D";
    }

    public static List<DungeonClass> botOrder() {
        DungeonClass mineClass = P3Sim.ownClass();
        List<DungeonClass> order;
        synchronized (leapLock) {
            List<DungeonClass> cur = leapOrder;
            LinkedHashSet<DungeonClass> distinct = new LinkedHashSet<>(cur);
            distinct.addAll(Party.CLASSES());
            List<DungeonClass> it = new ArrayList<>();
            for (DungeonClass c : distinct) if (c != mineClass) it.add(c);
            if (!it.equals(cur)) leapOrder = it;
            order = it;
        }
        if (odinSort) {
            List<DungeonClass> o = odinOrder(order);
            return o != null ? o : order;
        }
        return order;
    }

    public static List<DungeonClass> orderFor(DungeonClass c) {
        if (c == P3Sim.ownClass()) return botOrder();
        List<DungeonClass> o = new ArrayList<>();
        for (DungeonClass x : Party.CLASSES()) if (x != c) o.add(x);
        ServerPlayer pl = Players.playerOf(c);
        if (pl != null && !Sim.isHost(pl)) {
            List<DungeonClass> their = Players.profile(pl).order;
            if (their != null && their.size() == o.size() && new HashSet<>(their).equals(new HashSet<>(o))) return their;
        }
        List<DungeonClass> odin = odinOrder(o);
        return odin != null ? odin : o;
    }

    public static boolean odinSort = true;

    private static List<DungeonClass> odinOrder(List<DungeonClass> classes) {
        List<DungeonClass> res;
        try {
            List<SimDungeonPlayer> players = new ArrayList<>();
            for (DungeonClass it : classes) players.add(new SimDungeonPlayer(Roles.label(it), it, 50, null));
            players.sort(Comparator.comparingInt(it -> it.clazz.priority));
            res = new ArrayList<>();
            for (SimDungeonPlayer p : Arrays.asList(OdinCompat.odinSorting(players))) if (classes.contains(p.clazz)) res.add(p.clazz);
        } catch (Throwable t) {
            res = null;
        }
        if (res == null) return null;
        if (res.size() == classes.size() && new HashSet<>(res).equals(new HashSet<>(classes))) return res;
        return null;
    }

    public static void cycleSlot(int slot) { cycleSlot(slot, false); }

    public static void cycleSlot(int slot, boolean back) {
        synchronized (leapLock) {
            List<DungeonClass> order = new ArrayList<>(botOrder());
            odinSort = false;
            int i = slot - 1;
            int j = (i + (back ? order.size() - 1 : 1)) % order.size();
            DungeonClass t = order.get(i);
            order.set(i, order.get(j));
            order.set(j, t);
            leapOrder = new ArrayList<>(order);
        }
        save();
    }

    private static class Saved {
        Integer skill = null;
        List<String> mine = null;
        String mineFor = null;
        Double botMin = null;
        Double botMax = null;
        Boolean waitForYou = null;
        Double leapGap = null;
        List<String> leapOrder = null;
        Boolean odinSort = null;
        Map<String, List<Double>> spots = null;
        Boolean helper = null;
        List<String> ghosts = null;
        Map<String, List<Double>> custom = null;
        Map<String, String> picks = null;
        List<String> stacked = null;

        Saved() {}

        Saved(Integer skill, List<String> mine, String mineFor, Double botMin, Double botMax, Boolean waitForYou, Double leapGap,
              List<String> leapOrder, Boolean odinSort, Map<String, List<Double>> spots, Boolean helper, List<String> ghosts,
              Map<String, List<Double>> custom, Map<String, String> picks, List<String> stacked) {
            this.skill = skill;
            this.mine = mine;
            this.mineFor = mineFor;
            this.botMin = botMin;
            this.botMax = botMax;
            this.waitForYou = waitForYou;
            this.leapGap = leapGap;
            this.leapOrder = leapOrder;
            this.odinSort = odinSort;
            this.spots = spots;
            this.helper = helper;
            this.ghosts = ghosts;
            this.custom = custom;
            this.picks = picks;
            this.stacked = stacked;
        }
    }

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static File file() { return Ec.configDir().resolve("p3sim-plan.json").toFile(); }

    private static boolean loaded = false;
    private static boolean backedUp = false;

    public static void load() {
        if (loaded) return;
        loaded = true;
        Ec.safely("p3sim plan load", () -> {
            File f = file();
            if (!f.exists()) return;
            Saved s;
            try {
                s = gson.fromJson(Files.readString(f.toPath(), StandardCharsets.UTF_8), Saved.class);
            } catch (Throwable t) {
                PlayerFiles.setAside(f);
                if (t instanceof RuntimeException re) throw re;
                if (t instanceof Error er) throw er;
                throw new RuntimeException(t);
            }
            if (s == null) return;
            if (s.skill != null) skill = Math.clamp(s.skill == PROTOTYPE_1 ? THEORETICAL : s.skill, 0, SKILLS.size() - 1);
            if (s.mine != null && s.mineFor != null && !Objects.equals(s.skill, PROTOTYPE_1)) { mine.clear(); mine.addAll(s.mine); mineFor = s.mineFor; }
            if (s.botMin != null) botMin = s.botMin;
            if (s.botMax != null) botMax = s.botMax;
            if (s.waitForYou != null) waitForYou = s.waitForYou;
            if (s.helper != null) helper = s.helper;
            if (s.ghosts != null) { ghosts.clear(); ghosts.addAll(s.ghosts); }
            if (s.custom != null) s.custom.forEach((k, v) -> {
                if (v.size() >= 5) spots().put(k, new Spots.Spot(k, v.get(0), v.get(1), v.get(2), v.get(3).floatValue(), v.get(4).floatValue()));
            });
            if (s.picks != null) { pickMap().clear(); pickMap().putAll(s.picks); }
            if (s.stacked != null) { stackSet().clear(); stackSet().addAll(s.stacked); }
            if (s.leapGap != null) leapGap = s.leapGap;
            if (s.odinSort != null) odinSort = s.odinSort;
            if (s.leapOrder != null) {
                List<String> names = s.leapOrder;
                synchronized (leapLock) {
                    List<DungeonClass> l = new ArrayList<>();
                    for (String n : names) for (DungeonClass c : Party.CLASSES()) if (c.name().equals(n)) { l.add(c); break; }
                    leapOrder = l;
                }
            }
            if (s.spots != null) s.spots.forEach((k, v) -> {
                for (EarlyEnter it : earlyEnters) {
                    if (!it.key.equals(k)) continue;
                    if (v.size() >= 3 && !(k.equals("ee2") && v.equals(List.of(69.0, 109.0, 124.7)))) it.spot = new Vec3(v.get(0), v.get(1), v.get(2));
                    if (v.size() >= 5) { it.yaw = v.get(3).floatValue(); it.pitch = v.get(4).floatValue(); }
                    break;
                }
            });
        });
    }

    public static JsonObject sharedJson() {
        JsonObject it = new JsonObject();
        it.addProperty("skill", skill); it.addProperty("botMin", botMin); it.addProperty("botMax", botMax);
        it.addProperty("waitForYou", waitForYou); it.addProperty("helper", helper); it.addProperty("leapGap", leapGap);
        return it;
    }

    public static void applyShared(JsonObject o, boolean save) {
        JsonElement e;
        if ((e = o.get("skill")) != null) skill = Math.clamp(e.getAsInt(), 0, SKILLS.size() - 1);
        if ((e = o.get("botMin")) != null) botMin = e.getAsDouble();
        if ((e = o.get("botMax")) != null) botMax = e.getAsDouble();
        if ((e = o.get("waitForYou")) != null) waitForYou = e.getAsBoolean();
        if ((e = o.get("helper")) != null) helper = e.getAsBoolean();
        if ((e = o.get("leapGap")) != null) leapGap = e.getAsDouble();
        if (save) save();
    }

    private static JsonObject ownShared = null;
    private static String partyShared = null;

    private static List<String> ownMine = null;
    private static String ownMineFor = null;

    public static void receiveShared(JsonObject o) {
        if (ownShared == null) { ownShared = sharedJson(); ownMine = new ArrayList<>(mine()); ownMineFor = mineFor; }
        applyShared(o, false);
        partyShared = sharedJson().toString();
    }

    public static void leaveParty() {
        if (ownShared != null) applyShared(ownShared, false);
        List<String> om = ownMine;
        String omf = ownMineFor;
        if (om != null && omf != null && !omf.equals(mineFor)) { mine.clear(); mine.addAll(om); mineFor = omf; }
        ownShared = null; partyShared = null; ownMine = null; ownMineFor = null;
    }

    public static JsonObject partyJson() {
        JsonObject o = new JsonObject();
        Set<String> m = mine();
        JsonArray mineArr = new JsonArray();
        for (String it : m) mineArr.add(it);
        o.add("mine", mineArr);
        o.addProperty("mineFor", mineFor);
        JsonArray order = new JsonArray();
        for (DungeonClass it : botOrder()) order.add(it.name());
        o.add("order", order);
        JsonObject st = new JsonObject();
        for (String j : m) {
            if (!isStacked(j)) continue;
            DungeonClass h = helperOf(j);
            if (h != null) st.addProperty(j, h.name());
        }
        o.add("stacks", st);
        JsonObject s = new JsonObject();
        spots().forEach((k, p) -> {
            JsonArray a = new JsonArray();
            a.add(p.x); a.add(p.y); a.add(p.z); a.add(p.yaw); a.add(p.pitch);
            s.add(k, a);
        });
        o.add("spots", s);
        JsonObject sh = sharedJson();
        if (partyShared != null && !sh.toString().equals(partyShared)) o.add("shared", sh);
        return o;
    }

    public static void save() {
        Ec.safely("p3sim plan save", () -> {
            JsonObject own = ownShared;
            int skill = own != null && own.get("skill") != null ? own.get("skill").getAsInt() : P3Plan.skill;
            double botMin = own != null && own.get("botMin") != null ? own.get("botMin").getAsDouble() : P3Plan.botMin;
            double botMax = own != null && own.get("botMax") != null ? own.get("botMax").getAsDouble() : P3Plan.botMax;
            boolean waitForYou = own != null && own.get("waitForYou") != null ? own.get("waitForYou").getAsBoolean() : P3Plan.waitForYou;
            boolean helper = own != null && own.get("helper") != null ? own.get("helper").getAsBoolean() : P3Plan.helper;
            double leapGap = own != null && own.get("leapGap") != null ? own.get("leapGap").getAsDouble() : P3Plan.leapGap;
            boolean keepOwn = own != null && ownMineFor != null && !ownMineFor.equals(mineFor);
            botOrder();
            List<String> leapNames = new ArrayList<>();
            for (DungeonClass it : leapOrder) leapNames.add(it.name());
            Map<String, List<Double>> eeSpots = new LinkedHashMap<>();
            for (EarlyEnter it : earlyEnters) eeSpots.put(it.key, List.of(it.spot.x, it.spot.y, it.spot.z, (double) it.yaw, (double) it.pitch));
            Map<String, List<Double>> customOut = new LinkedHashMap<>();
            spots().forEach((k, p) -> customOut.put(k, List.of(p.x, p.y, p.z, (double) p.yaw, (double) p.pitch)));
            Saved s = new Saved(skill, keepOwn ? (ownMine != null ? ownMine : new ArrayList<>()) : new ArrayList<>(mine), keepOwn ? ownMineFor : mineFor,
                botMin, botMax, waitForYou, leapGap, leapNames, odinSort, eeSpots, helper, new ArrayList<>(ghosts),
                customOut, new HashMap<>(pickMap()), new ArrayList<>(stackSet()));
            File f = file();
            f.getParentFile().mkdirs();
            if (!backedUp && f.exists()) {
                backedUp = true;
                try {
                    Files.copy(f.toPath(), new File(f.getPath() + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            PlayerFiles.writeAtomic(f, gson.toJson(s));
        });
    }
}
