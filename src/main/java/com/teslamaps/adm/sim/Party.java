package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/Party.kt

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class Party {
    private Party() {}

    private static List<DungeonClass> classes;

    public static synchronized List<DungeonClass> CLASSES() {
        if (classes == null) classes = List.of(DungeonClass.HEALER, DungeonClass.BERSERK, DungeonClass.ARCHER, DungeonClass.TANK, DungeonClass.MAGE);
        return classes;
    }

    private static final double WALK = 0.85;
    private static final double BLINK = 10.0;
    private static final double DIG = 0.6;
    private static final double CORE_N = 118.0;

    public static class Bot {
        public final DungeonClass clazz;
        public final int slot;
        public final String name;
        public Mannequin entity = null;
        public Vec3 pos = Vec3.ZERO;
        public float yaw = 0f;
        public float pitch = 0f;
        public Pair<Float, Float> face = null;
        public Vec3 to = null;
        public int goAt = 0;
        public int due = -1;
        public Station working = null;
        public int inSection = 0;
        public boolean hold = false;
        public double vy = 0.0;
        public int blinkReady = 0;
        public int blinkAt = -100;
        public int leaptAt = -100;
        public String heldKey = "";
        public GhostPlayer ghost = null;
        public int lead = 0;
        public Routes.Route route = null;
        public Vec3 routeTo = null;
        public double routeT = 0.0;
        public String routeHeld = null;
        public int routeHeldUntil = -1;
        public ArrayDeque<Vec3> dig = null;
        public Vec3 leadTo = null;
        public List<GhostRun.Frame> leadFrames = null;
        public Legs legs = null;

        public Bot(DungeonClass clazz, int slot) {
            this.clazz = clazz;
            this.slot = slot;
            this.name = Roles.label(clazz);
        }
    }

    public static class Piece {
        public final String type;
        public final List<GhostRun.Frame> frames;
        public final String id;
        public final int k;
        public final List<GhostRun.Frame> land;

        public Piece(String type, List<GhostRun.Frame> frames, String id, int k, List<GhostRun.Frame> land) {
            this.type = type;
            this.frames = frames;
            this.id = id;
            this.k = k;
            this.land = land;
        }

        public Piece(String type, List<GhostRun.Frame> frames, String id, int k) {
            this(type, frames, id, k, Collections.emptyList());
        }
    }

    public static class Legs {
        public final List<Piece> pieces;
        public int i = 0;
        public double t = 0.0;
        public Vec3 from = null;
        public int land = 0;
        public final HashSet<String> leaptOnto = new HashSet<>();

        public Legs(List<Piece> pieces) { this.pieces = pieces; }

        public boolean expectsLeap(String c) {
            for (int k = i; k < pieces.size(); k++) {
                Piece p = pieces.get(k);
                if (p.type.equals("leap") && p.id.equals(c)) return true;
            }
            return false;
        }
    }

    public static class Ee {
        public final int into;
        public final String label;
        public final Vec3 spot;
        public final float yaw;
        public final float pitch;
        public final DungeonClass owner;

        public Ee(int into, String label, Vec3 spot, float yaw, float pitch, DungeonClass owner) {
            this.into = into;
            this.label = label;
            this.spot = spot;
            this.yaw = yaw;
            this.pitch = pitch;
            this.owner = owner;
        }

        public boolean byYou() { return owner != null && Players.isPlayerClass(owner); }

        public ServerPlayer player() { return Players.playerOf(owner); }
    }

    public static class Mate {
        public final String name;
        public final DungeonClass clazz;
        public final ServerPlayer player;
        public final Bot bot;

        public Mate(String name, DungeonClass clazz, ServerPlayer player, Bot bot) {
            this.name = name;
            this.clazz = clazz;
            this.player = player;
            this.bot = bot;
        }

        public Vec3 pos() { return player != null ? player.position() : bot.pos; }

        public float yaw() { return player != null ? player.getYRot() : bot.yaw; }

        public float pitch() {
            if (player != null) return player.getXRot();
            if (bot.entity != null) return bot.entity.getXRot();
            return bot.pitch;
        }

        public boolean here() { return player != null || (bot != null && bot.entity != null); }

        public GameProfile profile() { return player != null ? player.getGameProfile() : Party.profile(bot); }
    }

    public static List<Mate> mates(ServerPlayer viewer) {
        boolean classless = viewer != null && !Players.hasClass(viewer);
        DungeonClass mine = viewer != null ? Players.classOf(viewer) : P3Sim.ownClass();
        List<Bot> all = bots();
        List<Mate> out = new ArrayList<>();
        for (DungeonClass c : (classless ? CLASSES() : P3Plan.orderFor(mine))) {
            ServerPlayer pl = Players.playerOf(c);
            if (pl == viewer) pl = null;
            if (pl != null) out.add(new Mate(pl.getGameProfile().name(), c, pl, null));
            else {
                for (Bot b : all) if (b.clazz == c) { out.add(new Mate(b.name, c, null, b)); break; }
            }
        }
        return out;
    }

    public static void rosterChanged() {
        bots();
        if (Fight.phase instanceof P1Maxor p1) p1.botsChanged();
        if (Fight.phase instanceof P2Storm p2) p2.botsChanged();
        if (!(Fight.phase instanceof GoldorPhase ph)) return;
        if (!planOn() || botsFor != ph) return;
        for (DungeonClass c : P3Plan.botOrder()) {
            if (!Players.isPlayerClass(c)) {
                Bot had = botOf(c);
                if (had == null || had.entity == null) {
                    List<String> js = P3Plan.plan().jobsOf.get(c);
                    takeOver(ph, c, null, js == null ? new LinkedHashSet<>() : new LinkedHashSet<>(js));
                }
            }
        }
        adoptOrphans(ph);
    }

    private static GoldorPhase botsFor = null;

    private static Station st(GoldorPhase phase, String id) {
        for (Station s : phase.stations) if (s.id().equals(id)) return s;
        return null;
    }

    private static Integer toIntOrNull(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String removePrefix(String s, String prefix) {
        return s.startsWith(prefix) ? s.substring(prefix.length()) : s;
    }

    private static Integer gateNum(String job) { return toIntOrNull(removePrefix(job, "gate ")); }

    private static void adoptOrphans(GoldorPhase phase) {
        Set<String> ghostJobs = new LinkedHashSet<>();
        for (Bot b : bots) if (b.ghost != null) ghostJobs.addAll(b.ghost.jobs());
        List<Bot> crew = new ArrayList<>();
        for (Bot b : bots) if (b.ghost == null && b.entity != null && !b.hold) crew.add(b);
        if (crew.isEmpty()) return;
        Roles.Plan plan = P3Plan.plan();
        for (String job : P3Plan.allJobs()) {
            if (Players.isPlayerJob(job) || ghostJobs.contains(job) || jobs.stream().anyMatch(j -> j.job.equals(job))) continue;
            Station st = st(phase, job);
            if (st != null && st.done) continue;
            if (st == null && job.startsWith("gate")) {
                Integer g = gateNum(job);
                if (phase.gateIsDown(g != null ? g : 0)) continue;
            }
            Integer sObj = st != null ? Integer.valueOf(st.section) : gateNum(job);
            if (sObj == null) continue;
            int s = sObj;
            if (s < phase.section) continue;
            Bot bot = null;
            DungeonClass d = P3Plan.doer(job);
            if (d != null) for (Bot c : crew) if (c.clazz == d) { bot = c; break; }
            if (bot == null) {
                List<DungeonClass> owners = plan.owners.get(job);
                if (owners != null) {
                    outer:
                    for (DungeonClass oc : owners) for (Bot c : crew) if (c.clazz == oc) { bot = c; break outer; }
                }
            }
            if (bot == null) {
                int best = Integer.MAX_VALUE;
                for (Bot c : crew) {
                    int cnt = 0;
                    for (Job j : jobs) if (j.bot == c) cnt++;
                    if (cnt < best) { best = cnt; bot = c; }
                }
            }
            if (bot == null) continue;
            Pair<Integer, Double> t = plan.times.get(job);
            if (t == null) t = Pair.of(s, 5.0);
            int ts = t.first;
            double sec = t.second;
            if (ts <= phase.section) {
                Job j = new Job(job, bot, phase.section, 0.0);
                j.at = phase.n() + 20;
                jobs.add(j);
            } else jobs.add(new Job(job, bot, ts, sec));
            dbg("§e" + bot.name + "§7 picks up " + job + " (nobody had it)");
            walkOn(bot, phase.n());
        }
    }

    private static Vec3 posOf(DungeonClass c) {
        ServerPlayer p = Players.playerOf(c);
        return p != null ? p.position() : null;
    }

    private static final Ee[] ees = new Ee[7];

    public static Ee eeOf(int into) { return into >= 0 && into < ees.length ? ees[into] : null; }

    private static void resolveEes() {
        Roles.Plan plan = P3Plan.plan();
        for (int into = 2; into <= 6; into++) {
            int k = into == 6 ? 5 : into;
            P3Plan.EarlyEnter base = null;
            for (P3Plan.EarlyEnter e : P3Plan.earlyEnters) if (e.into == into) { base = e; break; }
            DungeonClass planned = plan.ee.get(k);
            Set<String> playerNames = new LinkedHashSet<>();
            for (DungeonClass c : Players.playerClasses()) playerNames.add(c.name());
            List<String> ghostsThere = new ArrayList<>();
            for (Bot b : bots) if (b.ghost != null && b.ghost.earlyEnters(k)) ghostsThere.add(b.clazz.name());
            Set<String> ghosts = new LinkedHashSet<>();
            for (Bot b : bots) if (b.ghost != null) ghosts.add(b.clazz.name());
            String owner = GhostPlayer.eeOwner(planned != null ? planned.name() : null, playerNames, ghostsThere, ghosts);
            DungeonClass c = null;
            for (DungeonClass cc : CLASSES()) if (cc.name().equals(owner)) { c = cc; break; }
            Spots.Spot spot = base != null ? P3Plan.eeSpot(base) : null;
            GhostPlayer g = null;
            for (Bot b : bots) if (b.clazz == c) { g = b.ghost; break; }
            GhostRun.Frame f = into == 6 || g == null ? null : g.eeSpot(k);
            Spots.Spot js = null;
            if (base != null) {
                ServerPlayer pl = Players.playerOf(c);
                if (pl != null && !Sim.isHost(pl)) js = Players.spotOf(pl, base.key);
            }
            Ee result;
            if (c == null) result = null;
            else if (f != null) result = new Ee(into, base != null ? base.label : "EE" + into, new Vec3(f.p.x, f.p.y, f.p.z), f.yaw, f.pitch, c);
            else if (base != null && js != null) result = new Ee(into, base.label, new Vec3(js.x, js.y, js.z), js.yaw, js.pitch, c);
            else if (base != null && spot != null) result = new Ee(into, base.label, new Vec3(spot.x, spot.y, spot.z), spot.yaw, spot.pitch, c);
            else result = null;
            ees[into] = result;
        }
    }

    private static final ArrayList<Bot> bots = new ArrayList<>();

    private static int dbgN = 0;
    private static int dbgS = 0;
    private static GoldorPhase curPhase = null;

    private static void dbg(String text) {
        double sec = (dbgN - sectionN[Math.clamp(dbgS, 0, 5)]) / 20.0;
        String line = String.format(Locale.ROOT, "§8[bots S" + dbgS + " %.2f] §7" + text, sec);
        if (P3Sim.debugBots()) Sim.chat(line);
        else Recorder.event(line);
    }

    private static JsonArray v(Vec3 p) {
        if (p == null) return null;
        JsonArray a = new JsonArray();
        a.add(Recorder.r(p.x));
        a.add(Recorder.r(p.y));
        a.add(Recorder.r(p.z));
        return a;
    }

    public static void record(JsonObject o) {
        JsonArray arr = new JsonArray();
        for (Bot b : bots) {
            JsonObject jo = new JsonObject();
            jo.addProperty("name", b.name);
            jo.add("pos", v(b.pos));
            if (b.to != null) {
                jo.add("to", v(b.to));
                jo.addProperty("goAt", b.goAt);
                jo.addProperty("due", b.due);
            }
            jo.addProperty("in", b.inSection);
            if (b.hold) jo.addProperty("hold", true);
            if (b.working != null) jo.addProperty("working", b.working.id());
            JsonArray ja = new JsonArray();
            for (Job j : jobs) if (j.bot == b) ja.add(j.job + "@" + j.at);
            jo.add("jobs", ja);
            arr.add(jo);
        }
        o.add("bots", arr);
        o.addProperty("ee", "arrived=" + Arrays.toString(eeArrived) + " youOn=" + Arrays.toString(youOn) + " preleapAt=" + Arrays.toString(preleapAt) + " spotBy=" + Arrays.toString(eeSpotBy));
        if (!leaps.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < leaps.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(leaps.get(i).bot.name).append("@").append(leaps.get(i).at);
            }
            o.addProperty("leaps", sb.toString());
        }
    }

    private static String shortStr(Vec3 v) {
        return String.format(Locale.ROOT, "%.1f %.1f %.1f", v.x, v.y, v.z);
    }

    private static void go(Bot b, Vec3 to, int at, int due, String why, Pair<Float, Float> face, int lead) {
        if (b.legs != null) return;
        b.face = face;
        b.lead = lead;
        if (!Objects.equals(b.to, to)) dbg("§e" + b.name + "§7 -> " + why + " (" + shortStr(to) + ", " + String.format(Locale.ROOT, "%.0f", b.pos.distanceTo(to)) + " blocks"
                + (due >= 0 ? ", due in " + ((due - dbgN) / 20.0) + "s" : "") + (at > dbgN ? ", leaves in " + ((at - dbgN) / 20.0) + "s" : "") + ")");
        b.to = to;
        b.goAt = at;
        b.due = due;
    }

    private static void go(Bot b, Vec3 to, int at, int due, String why, Pair<Float, Float> face) { go(b, to, at, due, why, face, 0); }

    private static void go(Bot b, Vec3 to, int at, int due, String why) { go(b, to, at, due, why, null, 0); }

    private static List<DungeonClass> clazzes(List<Bot> list) {
        List<DungeonClass> out = new ArrayList<>();
        for (Bot b : list) out.add(b.clazz);
        return out;
    }

    public static List<Bot> bots() {
        List<DungeonClass> order = P3Plan.botOrder();
        List<DungeonClass> want = new ArrayList<>();
        for (DungeonClass c : order) if (!Players.isPlayerClass(c)) want.add(c);
        if (clazzes(bots).equals(want)) return bots;
        boolean midP3 = botsFor != null && Fight.phase == botsFor;
        boolean live = bots.stream().anyMatch(b -> b.entity != null) || midP3;
        if (live) {
            List<Bot> gone = new ArrayList<>();
            for (Bot b : bots) if (!want.contains(b.clazz)) gone.add(b);
            for (Bot b : gone) {
                if (b.entity != null) b.entity.discard();
                b.entity = null;
                jobs.removeIf(j -> j.bot == b);
                leaps.removeIf(l -> l.bot == b);
            }
            bots.removeAll(new HashSet<>(gone));
            if (new HashSet<>(clazzes(bots)).equals(new HashSet<>(want)) || bots.stream().anyMatch(b -> b.entity != null) || midP3) return bots;
        }
        clear();
        bots.clear();
        for (DungeonClass c : want) bots.add(new Bot(c, order.indexOf(c) + 1));
        return bots;
    }

    public static void takeOver(GoldorPhase phase, DungeonClass c, Vec3 at, Set<String> theirs) {
        if (!planOn() || botsFor != phase || Players.isPlayerClass(c)) return;
        Bot had = botOf(c);
        if (had != null && had.entity != null) return;
        List<DungeonClass> order = P3Plan.botOrder();
        Bot b;
        if (had != null) b = had;
        else {
            b = new Bot(c, order.indexOf(c) + 1);
            bots.add(b);
            bots.sort(Comparator.comparingInt(x -> order.indexOf(x.clazz)));
        }
        b.inSection = phase.section;
        for (int into = 2; into <= 5; into++) {
            Ee e = eeOf(into);
            if (e != null && e.owner == c) {
                if (into <= phase.section || youArrived[into]) {
                    eeArrived[into] = true;
                    if (eeArrivedN[into] < 0) eeArrivedN[into] = phase.n();
                }
                if (into <= phase.section) released[into] = true;
            }
        }
        Roles.Plan plan = P3Plan.plan();
        List<ServerPlayer> others = new ArrayList<>();
        for (ServerPlayer p : Sim.players()) if (Players.classOf(p) != c) others.add(p);
        List<String> todo = new ArrayList<>();
        for (String j : theirs) {
            boolean anyOther = false;
            for (ServerPlayer p : others) if (Players.jobsOf(p).contains(j)) { anyOther = true; break; }
            if (anyOther) continue;
            if (jobs.stream().anyMatch(x -> x.job.equals(j))) continue;
            Station s = st(phase, j);
            if (s != null && s.done) continue;
            todo.add(j);
        }
        Vec3 start = at;
        if (start == null) {
            for (String j : todo) {
                Station s = st(phase, j);
                if ((s != null ? s.section : 0) == phase.section) { start = spotOf(j); break; }
            }
        }
        if (start == null) {
            ServerPlayer h = Sim.host();
            start = h != null ? h.position() : CORE_SPOT;
        }
        put(b, start);
        for (String job : todo) {
            Station st = st(phase, job);
            if (st == null && job.startsWith("gate")) {
                Integer g = gateNum(job);
                if (phase.gateIsDown(g != null ? g : 0)) continue;
            }
            Integer sObj = st != null ? Integer.valueOf(st.section) : gateNum(job);
            if (sObj == null) continue;
            int s = sObj;
            if (s < phase.section) continue;
            Pair<Integer, Double> t = plan.times.get(job);
            if (t == null) t = Pair.of(s, 5.0);
            int ts = t.first;
            double sec = t.second;
            if (ts <= phase.section) {
                Job j = new Job(job, b, phase.section, 0.0);
                j.at = phase.n() + 20;
                jobs.add(j);
            } else jobs.add(new Job(job, b, ts, sec));
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Job j : jobs) if (j.bot == b) {
            if (!first) sb.append(", ");
            sb.append(j.job);
            first = false;
        }
        dbg("§e" + b.name + "§7 takes over the " + c.name() + "'s jobs: " + sb);
        if (coreIn) {
            b.inSection = 5;
            go(b, CORE_SPOT.add(0.0, 0.0, 2.0), phase.n(), -1, "the core");
        } else walkOn(b, phase.n());
    }

    public static Bot bot(int slot) {
        List<Bot> l = bots();
        int i = slot - 1;
        return i >= 0 && i < l.size() ? l.get(i) : null;
    }

    private static Bot botOf(DungeonClass c) {
        for (Bot b : bots) if (b.clazz == c) return b;
        return null;
    }

    private static int generation = 0;

    public static void clear() {
        generation++;
        jobs.clear();
        leaps.clear();
        for (Bot it : bots) {
            if (it.entity != null) it.entity.discard();
            it.entity = null; it.to = null; it.due = -1; it.working = null; it.hold = false; it.inSection = 0;
            it.vy = 0.0; it.blinkReady = 0; it.blinkAt = -100; it.leaptAt = -100; it.heldKey = ""; it.ghost = null;
            it.route = null; it.routeTo = null; it.routeHeld = null; it.routeHeldUntil = -1; it.leadTo = null; it.leadFrames = null; it.legs = null;
        }
        leadIn = false;
    }

    private static boolean leadIn = false;

    public static void startLeadIn(int ticks) {
        if (!botsOn()) return;
        leadIn = true;
        resolveEes();
        Roles.Plan plan = P3Plan.plan();
        for (Bot b : bots()) {
            String dev = earlyDevice(b, plan);
            if (dev != null) {
                b.pos = stand(dev);
                Pair<Float, Float> look = Spots.startLook(dev);
                b.yaw = look.first;
                b.pitch = look.second;
                place(b);
                continue;
            }
            if (P3Plan.ghostOn(b.clazz) && GhostStore.best(P3Plan.skillName(), b.clazz.name()) != null) continue;
            List<GhostRun.Frame> lead = P3Sim.realMoves() ? legLead(b.clazz.name()) : null;
            if (lead != null) {
                b.leadFrames = lead;
                b.leadTo = vec(lead.get(lead.size() - 1).p);
                leadFrame(b, 0);
                dbg("§e" + b.name + "§7 lead-in as yours: " + String.format(Locale.ROOT, "%.1f %.1f %.1f", b.leadTo.x, b.leadTo.y, b.leadTo.z));
                continue;
            }
            Vec3 to = leadInTarget(b, plan);
            if (to == null) continue;
            b.leadTo = to;
            go(b, to, 0, ticks, "where it starts P3 (lead-in)");
        }
    }

    private static Vec3 I4_STAND() { return stand("S4 Target"); }

    private static int i4Shot = 0;

    private static void i4Bot(GoldorPhase phase) {
        int n = phase.n();
        Devices.Target target = phase.devices.target;
        Station st = st(phase, "S4 Target");
        Bot b = null;
        for (Job j : jobs) if (j.job.equals("S4 Target")) { b = j.bot; break; }
        if (b != null && !(b.entity != null && b.ghost == null && b.to == null && b.pos.distanceTo(I4_STAND()) <= 1.0)) b = null;
        if (b == null || st == null || st.done || n < 0) { target.botOn = null; return; }
        target.botOn = b.name;
        b.routeHeld = "TERMINATOR";
        b.routeHeldUntil = n + 5;
        BlockPos cell = target.shown();
        Vec3 aim = cell != null ? new Vec3(cell.getX() + 0.5, cell.getY() + 0.5, (double) cell.getZ())
                : new Vec3(66.5 + KRandom.DEFAULT.nextDouble(-2.5, 2.5), 128.5 + KRandom.DEFAULT.nextDouble(-2.5, 2.5), 50.0);
        Vec3 eye = b.pos.add(0.0, 1.62, 0.0);
        lookAt(b, aim.add(0.0, 0.7, 0.0));
        if (n < i4Shot) return;
        if (cell == null && KRandom.DEFAULT.nextInt(10) != 0) return;
        i4Shot = n + (cell != null ? 3 + KRandom.DEFAULT.nextInt(4) : 8);
        Vec3 v = aim.add(0.0, 0.7, 0.0).subtract(eye).normalize().scale(3.0);
        swing(b);
        Sim.sound(SoundEvents.ARROW_SHOOT, 1f, 1f / (KRandom.DEFAULT.nextFloat() * 0.4f + 1.2f) + 0.5f, eye, SoundSource.PLAYERS);
        Bows.launch(eye, eye.add(v), v);
    }

    private static final Vec3 SS_START = new Vec3(110.9, 121.5, 91.5);
    private static int ssClick = 0;

    private static void ssBot(GoldorPhase phase) {
        int n = phase.n();
        Station st = st(phase, "S1 SS");
        Bot b = null;
        for (Job j : jobs) if (j.job.equals("S1 SS")) { b = j.bot; break; }
        if (b != null && !(b.ghost == null && b.to == null && b.pos.distanceTo(stand("S1 SS")) <= 3.0)) b = null;
        if (b == null || st == null || st.done || n < 0 || n < ssClick) return;
        Vec3 at = n < 10 ? SS_START : new Vec3(110.9, 120.5 + KRandom.DEFAULT.nextInt(4), 92.5 + KRandom.DEFAULT.nextInt(4));
        lookAt(b, at);
        swing(b);
        ssClick = n + (n < 10 ? 4 : KRandom.DEFAULT.nextInt(5) == 0 ? 15 + KRandom.DEFAULT.nextInt(15) : 4 + KRandom.DEFAULT.nextInt(6));
    }

    private static void lookAt(Bot b, Vec3 at) {
        Vec3 d = at.subtract(b.pos.add(0.0, 1.62, 0.0));
        b.yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        b.pitch = (float) (-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
        place(b);
    }

    public static void tickLeadIn(int t) {
        if (leadIn) for (Bot b : bots) {
            if (b.leadFrames != null) leadFrame(b, t);
            else {
                move(b, t);
                hold(b, t);
            }
        }
    }

    private static void leadFrame(Bot b, int t) {
        List<GhostRun.Frame> fr = b.leadFrames;
        if (fr == null) return;
        GhostRun.Frame f = fr.get(Math.clamp(t, 0, fr.size() - 1));
        b.pos = vec(f.p); b.yaw = f.yaw; b.pitch = f.pitch; b.to = null; b.vy = 0.0;
        setHeld(b, f.held.isEmpty() ? "breaker" : f.held);
        place(b);
    }

    private static Set<String> EARLY_DEVICES() { return Set.of("S3 Arrows", "S4 Target"); }

    private static Pair<Integer, Double> time(Roles.Plan plan, String j) {
        Pair<Integer, Double> t = plan.times.get(j);
        return t != null ? t : Pair.of(sectionOf(j), 5.0);
    }

    private static List<String> ownJobs(DungeonClass c, Roles.Plan plan) {
        List<String> mine = new ArrayList<>();
        for (String j : P3Plan.allJobs()) {
            if (Players.isPlayerJob(j)) continue;
            DungeonClass d = P3Plan.doer(j);
            if (d != null && Players.isPlayerClass(d)) d = null;
            if (d == null) {
                List<DungeonClass> o = plan.owners.get(j);
                d = o == null || o.isEmpty() ? null : o.get(0);
            }
            if (d == c) mine.add(j);
        }
        return mine;
    }

    private static String earlyDevice(Bot b, Roles.Plan plan) {
        List<String> mine = ownJobs(b.clazz, plan);
        String best = null;
        for (String j : mine) {
            if (best == null) { best = j; continue; }
            Pair<Integer, Double> tj = time(plan, j), tb = time(plan, best);
            int cmp = Integer.compare(tj.first, tb.first);
            if (cmp == 0) cmp = Double.compare(tj.second, tb.second);
            if (cmp < 0) best = j;
        }
        if (best == null) return null;
        return EARLY_DEVICES().contains(best) && time(plan, best).first == 1 ? best : null;
    }

    private static Vec3 leadInTarget(Bot b, Roles.Plan plan) {
        DungeonClass c = b.clazz;
        if (P3Plan.ghostOn(c) && GhostStore.best(P3Plan.skillName(), c.name()) != null) return null;
        List<String> mine = ownJobs(c, plan);
        for (int k = 2; k <= 4; k++) {
            Ee ee = eeOf(k);
            if (ee == null) continue;
            final int kk = k;
            if (ee.owner == c && !ee.byYou() && mine.stream().noneMatch(j -> time(plan, j).first < kk)) return ee.spot;
        }
        String first = null;
        for (String j : mine) {
            if (time(plan, j).first != 1) continue;
            if (first == null) { first = j; continue; }
            int cmp = Double.compare(time(plan, j).second, time(plan, first).second);
            if (cmp == 0) cmp = j.compareTo(first);
            if (cmp < 0) first = j;
        }
        if (first == null) return null;
        if (EARLY_DEVICES().contains(first)) return null;
        return spotOf(first);
    }

    public static boolean busyAt(Station st) {
        for (Bot b : bots) if (b.working == st) return true;
        return false;
    }

    public static boolean allIn(AABB box) {
        if (!botsOn()) return true;
        for (Bot b : bots) if (!(b.entity == null || box.contains(b.pos))) return false;
        return true;
    }

    // ------------------------------------------------------------------ P3

    private static class Job {
        final String job;
        final Bot bot;
        final int timeSection;
        double sec;
        int at = -1;

        Job(String job, Bot bot, int timeSection, double sec) {
            this.job = job;
            this.bot = bot;
            this.timeSection = timeSection;
            this.sec = sec;
        }
    }

    private static final ArrayList<Job> jobs = new ArrayList<>();

    private static class Leap {
        final Bot bot;
        final int at;
        final Vec3 youAt;
        final Supplier<Target> onto;

        Leap(Bot bot, int at, Vec3 youAt, Supplier<Target> onto) {
            this.bot = bot;
            this.at = at;
            this.youAt = youAt;
            this.onto = onto;
        }

        Leap(Bot bot, int at, Supplier<Target> onto) { this(bot, at, null, onto); }
    }

    private static class Target {
        final Vec3 pos;
        final String name;
        final Float yaw;

        Target(Vec3 pos, String name, Float yaw) {
            this.pos = pos;
            this.name = name;
            this.yaw = yaw;
        }

        Target(Vec3 pos, String name) { this(pos, name, null); }

        Target(Vec3 pos) { this(pos, null, null); }
    }

    private static Target asTarget(Bot b) { return new Target(b.pos, b.name, b.yaw); }

    private static Target asTarget(ServerPlayer p) { return new Target(p.position(), p.getGameProfile().name(), p.getYRot()); }

    private static final ArrayList<Leap> leaps = new ArrayList<>();
    private static final ArrayList<Roles.Move> ontoLeaps = new ArrayList<>();
    private static int planned = 0;
    private static final boolean[] youArrived = new boolean[6];
    private static final boolean[] eeArrived = new boolean[6];
    private static final int[] preleapAt = new int[6];
    private static final int[] eeSpotBy = new int[6];
    private static final String[] holdJob = new String[6];
    @SuppressWarnings("unchecked")
    private static final List<String>[] waitsFor = new List[6];
    @SuppressWarnings("unchecked")
    private static final HashSet<DungeonClass>[] youOn = new HashSet[6];
    static {
        for (int i = 0; i < youOn.length; i++) youOn[i] = new HashSet<>();
    }
    private static final boolean[] holdNoted = new boolean[6];
    private static final boolean[] released = new boolean[6];
    private static final int[] readyAt = filled(6, -1);
    private static final int RELEASE_DELAY = 6;
    private static final int IN_GRACE = 40;
    private static int lastLeap = 0;
    private static final int[] sectionN = new int[6];
    private static final int[] eeArrivedN = filled(6, -1);
    private static final int[] youArrivedN = filled(6, -1);
    private static final int[] youLeftN = filled(6, -1);
    private static int recoredN = -1;
    private static int youInCoreN = -1;
    private static boolean coreEeLeaps = false;

    private static int[] filled(int size, int v) {
        int[] a = new int[size];
        Arrays.fill(a, v);
        return a;
    }

    private static boolean botsOn() { return P3Sim.bots() && !Practice.active(); }

    private static boolean planOn() { return !Practice.active(); }

    private static void put(Bot b, Vec3 at) {
        if (P3Sim.bots()) spawn(b, at);
        else {
            b.pos = at;
            b.entity = null;
        }
    }

    public static void startP3(GoldorPhase phase) {
        Map<DungeonClass, Pair<Vec3, Vec3>> walked = new HashMap<>();
        if (leadIn && phase.arrived && phase.from == 1) for (Bot b : bots) if (b.leadTo != null) walked.put(b.clazz, Pair.of(b.pos, b.leadTo));
        clear();
        Arrays.fill(youArrived, false); Arrays.fill(eeArrived, false); Arrays.fill(preleapAt, -1); Arrays.fill(eeSpotBy, -1); Arrays.fill(holdJob, null); Arrays.fill(waitsFor, null);
        for (HashSet<DungeonClass> s : youOn) s.clear();
        Arrays.fill(holdNoted, false); Arrays.fill(released, false); Arrays.fill(readyAt, -1); coreIn = false;
        Arrays.fill(allInAt, -1);
        Arrays.fill(eeArrivedN, -1); Arrays.fill(youArrivedN, -1); Arrays.fill(youLeftN, -1); recoredN = -1; youInCoreN = -1; coreEeLeaps = false; i4Shot = 0; ssClick = 0;
        ontoLeaps.clear();
        planned = 0;
        lastLeap = 0;
        if (planOn()) {
            bots();
            if (botsOn() && phase.from == 1) startGhosts(phase);
        }
        botsFor = planOn() ? phase : null;
        resolveEes();
        if (!planOn()) return;
        if (P3Sim.realMoves()) Routes.preload();
        int from = Math.clamp(phase.from, 1, 5);
        for (int i = 0; i <= from; i++) eeArrived[i] = true;
        Roles.Plan plan = P3Plan.plan();
        Set<String> ghostJobs = new LinkedHashSet<>();
        for (Bot b : bots) if (b.ghost != null) ghostJobs.addAll(b.ghost.jobs());
        List<Bot> crew = new ArrayList<>();
        for (Bot b : bots) if (b.ghost == null) crew.add(b);
        for (String job : P3Plan.allJobs()) {
            if (Players.isPlayerJob(job) || ghostJobs.contains(job)) continue;
            Station st = st(phase, job);
            if (st != null && st.done) continue;
            if (st == null && job.startsWith("gate")) {
                Integer g = gateNum(job);
                if (phase.gateIsDown(g != null ? g : 0)) continue;
            }
            Integer sObj = st != null ? Integer.valueOf(st.section) : gateNum(job);
            if (sObj == null) continue;
            int s = sObj;
            if (s < from) continue;
            Pair<Integer, Double> t = plan.times.get(job);
            if (t == null) t = Pair.of(s, 5.0);
            int ts = t.first;
            double sec = t.second;
            Bot bot = null;
            DungeonClass d = P3Plan.doer(job);
            if (d != null) {
                Bot o = botOf(d);
                if (o != null && o.ghost == null) bot = o;
            }
            if (bot == null) {
                List<DungeonClass> owners = plan.owners.get(job);
                if (owners != null) for (DungeonClass oc : owners) {
                    Bot o = botOf(oc);
                    if (o != null && o.ghost == null) { bot = o; break; }
                }
            }
            if (bot == null) {
                int best = Integer.MAX_VALUE;
                for (Bot c : crew) {
                    int cnt = 0;
                    for (Job j : jobs) if (j.bot == c && j.timeSection == ts) cnt++;
                    if (cnt < best) { best = cnt; bot = c; }
                }
            }
            if (bot == null) continue;
            if (P3Plan.skill == P3Plan.RANDOM) sec = P3Plan.botMin + KRandom.DEFAULT.nextDouble() * Math.max(P3Plan.botMax - P3Plan.botMin, 0.0);
            if (ts < from) { ts = from; sec = 0.5; }
            jobs.add(new Job(job, bot, ts, sec));
        }
        spread(plan);
        if (P3Plan.helper) for (String job : P3Plan.allJobs()) {
            if (!P3Plan.isMine(job) || !P3Plan.isStacked(job)) continue;
            Station st = st(phase, job);
            if (st != null && st.done) continue;
            Integer sObj = st != null ? Integer.valueOf(st.section) : gateNum(job);
            if (sObj == null) continue;
            int s = sObj;
            if (s < from) continue;
            DungeonClass h = P3Plan.helperOf(job);
            Bot bot = h != null ? botOf(h) : null;
            if (bot == null || bot.ghost != null) continue;
            Pair<Integer, Double> t = plan.times.get(job);
            if (t == null) t = Pair.of(s, 5.0);
            int ts = t.first;
            double sec = t.second;
            if (ts < from) { ts = from; sec = 0.5; }
            jobs.add(new Job(job, bot, ts, sec));
            dbg("helper: §e" + bot.name + "§7 does your stacked " + job + " too, by " + sec + "s into S" + ts);
        }
        if (P3Plan.helper) for (ServerPlayer pl : Sim.players()) {
            if (Sim.isHost(pl)) continue;
            Set<String> theirs = Players.jobsOf(pl);
            for (Map.Entry<String, DungeonClass> e : Players.profile(pl).stacks.entrySet()) {
                String job = e.getKey();
                DungeonClass c = e.getValue();
                if (!theirs.contains(job)) continue;
                Station st = st(phase, job);
                if (st != null && st.done) continue;
                Integer sObj = st != null ? Integer.valueOf(st.section) : gateNum(job);
                if (sObj == null) continue;
                int s = sObj;
                if (s < from) continue;
                Bot bot = botOf(c);
                if (bot == null || bot.ghost != null) continue;
                Pair<Integer, Double> t = plan.times.get(job);
                if (t == null) t = Pair.of(s, 5.0);
                int ts = t.first;
                double sec = t.second;
                if (ts < from) { ts = from; sec = 0.5; }
                jobs.add(new Job(job, bot, ts, sec));
                dbg("helper: §e" + bot.name + "§7 does " + pl.getGameProfile().name() + "'s stacked " + job + " too, by " + sec + "s into S" + ts);
            }
        }
        if (P3Plan.helper) for (Roles.Help h : P3Plan.preset().helps) {
            if (h.section < from) continue;
            boolean someone = false;
            for (ServerPlayer pl : Sim.players()) {
                Set<String> js = Players.jobsOf(pl);
                if (js.contains(h.yours) && js.containsAll(h.jobs)) { someone = true; break; }
            }
            if (!someone) continue;
            DungeonClass c = P3Plan.doer(h.by);
            if (c == null || Players.isPlayerClass(c)) continue;
            Bot bot = botOf(c);
            if (bot == null || bot.ghost != null) continue;
            for (String j : h.jobs) {
                Station s = st(phase, j);
                if (s == null || !s.done) {
                    jobs.add(new Job(j, bot, h.section, h.at));
                    dbg("helper: §e" + bot.name + "§7 gets " + j + " by " + h.at + "s into S" + h.section);
                }
            }
        }
        for (Bot b : bots) {
            b.inSection = from;
            b.dig = null;
            GhostPlayer g = b.ghost;
            if (g != null) {
                GhostRun.Frame f = g.run.frame(0);
                b.yaw = f.yaw;
                b.pitch = f.pitch;
                spawn(b, vec(f.p));
                g.tick(phase.n());
                place(b);
                continue;
            }
            Legs lg = legsFor(b, from);
            GhostRun.Frame f = null;
            if (lg != null) for (Piece p : lg.pieces) if (!p.frames.isEmpty()) { f = p.frames.get(0); break; }
            if (lg != null && f != null) {
                b.legs = lg;
                b.yaw = f.yaw;
                b.pitch = f.pitch;
                Pair<Vec3, Vec3> w = walked.get(b.clazz);
                Vec3 fp = vec(f.p);
                put(b, w != null && w.first.distanceTo(fp) <= 3.0 ? w.first : fp);
                place(b);
                continue;
            }
            Ee e5 = eeOf(5);
            boolean core = e5 != null && e5.owner == b.clazz && from >= 3;
            if (core) {
                b.inSection = 5;
                eeArrived[5] = true;
                b.hold = from < 5;
                if (e5 != null) { b.yaw = e5.yaw; b.pitch = e5.pitch; }
            }
            Job nx = next(b);
            Job first = nx != null && sectionOf(nx.job) == from ? nx : null;
            Vec3 start;
            if (core) start = CORE_EE();
            else if (from == 1 && nx != null && EARLY_DEVICES().contains(nx.job) && nx.timeSection == 1) start = stand(nx.job);
            else if (from == 1 && nx != null && nx.timeSection == 1 && nx.sec <= 1.0) start = spotOf(nx.job);
            else if (from == 1) start = startPos(1);
            else if (first != null) start = spotOf(first.job);
            else {
                Ee ef = eeOf(from);
                start = ef != null ? ef.spot : startPos(from);
            }
            Pair<Vec3, Vec3> w = walked.get(b.clazz);
            if (w != null && !core && (nx == null || !EARLY_DEVICES().contains(nx.job))) {
                put(b, w.first);
                if (w.first.distanceTo(w.second) > 0.5 && w.second.equals(start)) go(b, start, phase.n(), phase.n(), "where it starts P3 (still on its way)");
            } else put(b, start);
        }
    }

    private static void startGhosts(GoldorPhase phase) {
        ArrayList<String> names = new ArrayList<>();
        for (Bot b : bots) {
            if (!P3Plan.ghostOn(b.clazz)) continue;
            GhostRun run = GhostStore.best(P3Plan.skillName(), b.clazz.name());
            if (run == null) continue;
            b.ghost = new GhostPlayer(run, new GhostWorld(b, phase), new GhostBody(b, phase));
            names.add("§d" + b.name + "§7 (" + String.format(Locale.ROOT, "%.2f", run.time / 20.0) + "s)");
        }
        if (!names.isEmpty()) Sim.note("Your best runs play: " + String.join(", ", names) + ".");
    }

    private static Vec3 vec(P p) { return new Vec3(p.x, p.y, p.z); }

    private static P p(Vec3 v) { return new P(v.x, v.y, v.z); }

    private static class GhostWorld implements GhostPlayer.World {
        final Bot b;
        final GoldorPhase phase;

        GhostWorld(Bot b, GoldorPhase phase) {
            this.b = b;
            this.phase = phase;
        }

        @Override public Integer sectionStart(int s) { return s <= phase.section ? (s <= 1 ? 0 : sectionN[s]) : null; }

        @Override public boolean stationDone(String id) {
            Station st = st(phase, id);
            return st != null ? st.done : true;
        }

        @Override public boolean canDo(String id) {
            Station st = st(phase, id);
            if (st == null) return false;
            if (st.done) return false;
            if (st.section != phase.section && !(st.kind == Station.Kind.DEVICE && st.section > phase.section)) return false;
            return !(st.section == phase.section && holding(phase) && phase.stations.stream().filter(x -> x.section == phase.section && !x.done).count() == 1);
        }

        @Override public boolean gateDown(int s) { return phase.gateIsDown(s); }

        @Override public boolean canGate(int s) { return phase.section >= s; }

        @Override public Integer eeArrivedAt(int into, String who) {
            Ee e = eeOf(into);
            if (e == null || e.owner == null) return 0;
            DungeonClass owner = e.owner;
            if (!owner.name().equals(who)) return 0;
            int v = Players.isPlayerClass(owner) ? youArrivedN[into] : eeArrivedN[into];
            return v >= 0 ? v : null;
        }

        @Override public Integer eeReadyAt(int into) {
            Ee e = eeOf(into);
            if (e != null && e.owner == b.clazz) return readyAt[into] >= 0 ? readyAt[into] : null;
            return 0;
        }

        @Override public Integer recoredAt(String who) {
            Ee e = eeOf(5);
            if (e == null || e.owner == null) return 0;
            DungeonClass owner = e.owner;
            if (!owner.name().equals(who)) return 0;
            int v = Players.isPlayerClass(owner) ? youInCoreN : recoredN;
            return v >= 0 ? v : null;
        }

        @Override public P positionOf(String who) {
            Vec3 pos = posOf(Players.classByName(who));
            if (pos != null) return p(pos);
            for (Bot o : bots) if (o.clazz.name().equals(who)) return o.pos != null ? p(o.pos) : null;
            return null;
        }
    }

    private static class GhostBody implements GhostPlayer.Body {
        final Bot b;
        final GoldorPhase phase;

        GhostBody(Bot b, GoldorPhase phase) {
            this.b = b;
            this.phase = phase;
        }

        @Override public void move(P p, float yaw, float pitch, String held) {
            b.pos = vec(p); b.yaw = yaw; b.pitch = pitch; b.to = null;
            setHeld(b, held);
        }

        @Override public void complete(String id) {
            Station st = st(phase, id);
            if (st == null) return;
            if (st.kind == Station.Kind.LEVER) phase.pullLever(st, b.name);
            else st.complete(b.name);
            if (st.kind == Station.Kind.DEVICE) phase.devices.shownDone(st.label);
            dbg("§d" + b.name + "§7 (ghost) did " + id);
        }

        @Override public void gate(int s) {
            if (phase.blowGate(s, b.name)) dbg("§d" + b.name + "§7 (ghost) blew gate " + s);
        }

        @Override public void leap(String who, P at) {
            DungeonClass c = Players.classByName(who);
            ServerPlayer pl = Players.playerOf(c);
            Target onto = null;
            if (pl != null) onto = asTarget(pl);
            else {
                Bot o = botOf(c);
                if (o != null) onto = asTarget(o);
            }
            leapTo(b, new Target(vec(at), onto != null ? onto.name : null, onto != null ? onto.yaw : null), phase.n());
            dbg("§d" + b.name + "§7 (ghost) leaps onto " + who);
        }

        @Override public void swing() { Party.swing(b); }

        @Override public void eeOn(int into) {
            Ee e = eeOf(into);
            if (e == null || e.owner != b.clazz) return;
            b.inSection = Math.max(b.inSection, into);
            b.hold = true;
            arrivedOnEe(b, into, phase.n());
        }

        @Override public void eeLeft(int into) {
            Ee e = eeOf(into);
            if (e == null || e.owner != b.clazz) return;
            released[into] = true;
            b.hold = false;
            dbg("§d" + b.name + "§7 (ghost) moves on from EE" + into);
        }

        @Override public void recore() {
            Ee e = eeOf(5);
            if (e == null || e.owner != b.clazz || recoredN >= 0) return;
            recoredN = phase.n();
            GhostCapture.event("recore", b.clazz.name(), 0);
        }

        @Override public void working(String id) {
            b.working = id != null ? st(phase, id) : null;
        }

        @Override public void giveUp(String job) {
            Bot bot = null;
            int best = Integer.MAX_VALUE;
            for (Bot o : bots) {
                if (o.ghost != null) continue;
                int cnt = 0;
                for (Job j : jobs) if (j.bot == o) cnt++;
                if (cnt < best) { best = cnt; bot = o; }
            }
            if (bot == null) return;
            Job j = new Job(job, bot, phase.section, 0.0);
            j.at = phase.n();
            jobs.add(j);
            dbg("§d" + b.name + "§7 (ghost) couldn't do " + job + ": §e" + bot.name + "§7 does");
            walkOn(bot, phase.n());
        }
    }

    private static void arrivedOnEe(Bot b, int into, int n) {
        eeArrived[into] = true;
        if (eeArrivedN[into] < 0) eeArrivedN[into] = n;
        GhostCapture.event("ee", b.clazz.name(), into);
        Ee ee = eeOf(into);
        if (ee == null) return;
        if (P3Sim.debugBots()) dbg("§a" + b.name + " is on its " + ee.label + " spot§7, holding");
        else Sim.note("§e" + b.name + "§7 is on " + ee.label + ".");
        List<BlockPos> blocks = P3Plan.preset().mines.get(into == 5 ? "core" : "ee" + into);
        if (blocks != null) {
            for (BlockPos pos : blocks) if (!Sim.level().getBlockState(pos).isAir()) {
                Sim.level().levelEvent(2001, pos, Block.getId(Sim.level().getBlockState(pos)));
                com.teslamaps.adm.sim.Blocks.set(pos, Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static void trackYou(GoldorPhase phase) {
        int n = phase.n();
        for (int into = 2; into <= 5; into++) {
            Ee ee = eeOf(into);
            if (ee == null || !ee.byYou()) continue;
            ServerPlayer pl = ee.player();
            if (pl == null) continue;
            Vec3 p = pl.position();
            double d = p.distanceTo(ee.spot);
            if (youArrivedN[into] < 0) {
                if (phase.section < into && d <= 3.0) { youArrivedN[into] = n; GhostCapture.event("ee", ee.owner.name(), into); }
            } else if (youLeftN[into] < 0 && d > 3.0) { youLeftN[into] = n; GhostCapture.event("left", ee.owner.name(), into); }
            else if (youLeftN[into] >= 0 && d <= 3.0 && n - youLeftN[into] <= GhostPlayer.BACK) { youLeftN[into] = -1; GhostCapture.event("back", ee.owner.name(), into); }
        }
        ServerPlayer inCore = null;
        for (ServerPlayer pl : Sim.players()) if (GoldorPhase.CORE_BOX.contains(pl.position())) { inCore = pl; break; }
        if (phase.section >= 5 && youInCoreN < 0 && inCore != null) {
            youInCoreN = n;
            GhostCapture.event("core", "", 0);
            DungeonClass c = Players.classOf(inCore);
            Ee e5 = eeOf(5);
            if (e5 != null && e5.owner == c) GhostCapture.event("recore", c.name(), 0);
        }
    }

    private static void spread(Roles.Plan plan) {
        for (Bot b : bots) for (int ts = 1; ts <= 5; ts++) {
            List<String> roleRaw = plan.jobsOf.get(b.clazz);
            List<String> role = roleRaw != null ? roleRaw : Collections.emptyList();
            List<Job> mine = new ArrayList<>();
            for (Job j : jobs) if (j.bot == b && j.timeSection == ts) mine.add(j);
            mine.sort(Comparator.<Job>comparingDouble(j -> j.sec).thenComparingInt(j -> {
                int i = role.indexOf(j.job);
                return i < 0 ? 99 : i;
            }));
            double prev = 0.0;
            int i = 0;
            while (i < mine.size()) {
                double t = mine.get(i).sec;
                List<Job> group = new ArrayList<>();
                for (int k = i; k < mine.size() && mine.get(k).sec == t; k++) group.add(mine.get(k));
                for (int k = 0; k < group.size(); k++) group.get(k).sec = prev + (t - prev) * (k + 1) / group.size();
                prev = t;
                i += group.size();
            }
        }
    }

    public static void tickP3(GoldorPhase phase) {
        trackYou(phase);
        if (!planOn()) return;
        int n = phase.n();
        int s = phase.section;
        dbgN = n; dbgS = s; curPhase = phase;
        if (P3Sim.debugBots() && n % 40 == 0) dbgHolds(phase);
        if (s != planned) {
            planned = s;
            sectionN[Math.clamp(s, 0, 5)] = n;
            dbg("§bsection " + s + " starts");
            if (s >= 5) for (Bot b : bots) if (b.legs != null) {
                b.legs = null;
                b.to = null;
                if (b.hold && b == coreBot() && eeArrived[5] && !released[5]) { b.inSection = 5; continue; }
                b.hold = false;
                b.inSection = 4;
            }
            sectionStarted(phase, s);
        }
        HashSet<Bot> beaten = new HashSet<>();
        jobs.removeIf(j -> {
            Station st = st(phase, j.job);
            boolean done = (st != null && st.done) || (j.job.startsWith("gate") && phase.gateIsDown(sectionOf(j.job)));
            if (done) beaten.add(j.bot);
            return done;
        });
        for (Bot b : new ArrayList<>(beaten)) {
            boolean leaping = false;
            for (Leap l : leaps) if (l.bot == b) { leaping = true; break; }
            if (!leaping) walkOn(b, n);
        }
        youAtEarlyEnter(phase);
        List<Leap> due = new ArrayList<>();
        for (Leap l : leaps) if (n >= l.at) due.add(l);
        for (Leap l : due) {
            if (l.youAt != null && Sim.players().stream().noneMatch(pl -> pl.position().distanceTo(l.youAt) <= 3.0)) continue;
            leaps.remove(l);
            if (l.bot.legs != null) continue;
            Target t = l.onto.get();
            if (t != null) {
                dbg("§e" + l.bot.name + "§7 leaps (" + shortStr(t.pos) + ")");
                leapTo(l.bot, t, n);
                walkOn(l.bot, n);
            }
        }
        boolean held = holding(phase);
        ArrayList<Bot> finished = new ArrayList<>();
        jobs.removeIf(j -> {
            if (j.at < 0 || n < j.at) return false;
            if (!inReach(j.bot, j.job) && j.bot.legs == null) {
                dbg("§e" + j.bot.name + "§7 wasn't at " + j.job + " on time: put there");
                j.bot.hold = false;
                j.bot.dig = null;
                arrive(j.bot, spotOf(j.job));
            }
            Station st = st(phase, j.job);
            if (st != null) {
                if (st.section == s && held && phase.stations.stream().filter(x -> x.section == s && !x.done).count() == 1) return false;
                if (st.kind != Station.Kind.TERMINAL) swing(j.bot);
                if (st.kind == Station.Kind.LEVER) phase.pullLever(st, j.bot.name);
                else st.complete(j.bot.name);
                if (st.kind == Station.Kind.DEVICE) phase.devices.shownDone(st.label);
                if (!st.done) return false;
            } else {
                int k = sectionOf(j.job);
                if (!phase.gateIsDown(k)) {
                    swing(j.bot);
                    if (!phase.blowGate(k, j.bot.name)) return false;
                }
            }
            dbg("§e" + j.bot.name + "§7 did " + j.job);
            j.bot.working = null;
            finished.add(j.bot);
            return true;
        });
        for (Bot b : finished) walkOn(b, n);
        ghosts(phase);
        earlyEnterBots(phase);
        preleaps(phase);
        ontoLeapsTick(phase);
        releaseEarlyEnterers(phase);
        recoreAfterJobs(phase);
        intoCoreWhenRecored(phase);
        for (Bot b : bots) {
            if (b.legs != null) legTick(b, phase);
            else if (b.ghost == null) move(b, n);
            else place(b);
        }
        i4Bot(phase);
        ssBot(phase);
        for (Bot b : bots) {
            if (b.ghost != null) continue;
            Station was = b.working;
            Job nx = next(b);
            Station w = null;
            if (nx != null && nx.at >= 0 && nx.at - n <= 40 && b.pos.distanceTo(spotOf(nx.job)) <= 2.5) {
                for (Station x : phase.stations) if (x.id().equals(nx.job) && x.kind == Station.Kind.TERMINAL) { w = x; break; }
            }
            b.working = w;
            if (b.working != null && b.working != was) swing(b);
            if (b.legs == null) hold(b, n);
        }
    }

    private static Legs legsFor(Bot b, int from) {
        if (!P3Sim.realMoves() || !botsOn() || from != 1) return null;
        List<Piece> pieces = legPieces(b.clazz.name());
        if (pieces == null || pieces.isEmpty()) return null;
        int moving = 0;
        List<String> none = new ArrayList<>();
        for (Piece p : pieces) {
            if (p.type.equals("move") && !p.frames.isEmpty()) moving++;
            if (p.type.equals("move") && p.frames.isEmpty()) none.add(p.id);
        }
        dbg("§e" + b.name + "§7 moves as your legs: " + moving + " pieces" +
                (none.isEmpty() ? "" : " (§cnone recorded§7 into " + String.join(", ", none) + ")"));
        return new Legs(pieces);
    }

    private static Pair<String, Map<String, Pair<List<Piece>, List<GhostRun.Frame>>>> legsRead = null;

    private static List<Piece> legPieces(String clazz) {
        Map<String, Pair<List<Piece>, List<GhostRun.Frame>>> f = legsFile();
        if (f == null) return null;
        Pair<List<Piece>, List<GhostRun.Frame>> c = f.get(clazz);
        return c != null ? c.first : null;
    }

    private static List<GhostRun.Frame> legLead(String clazz) {
        Map<String, Pair<List<Piece>, List<GhostRun.Frame>>> f = legsFile();
        if (f == null) return null;
        Pair<List<Piece>, List<GhostRun.Frame>> c = f.get(clazz);
        if (c == null || c.second == null || c.second.isEmpty()) return null;
        return c.second;
    }

    private static List<GhostRun.Frame> frames(JsonArray arr) {
        List<GhostRun.Frame> out = new ArrayList<>();
        if (arr == null) return out;
        for (JsonElement fe : arr) {
            JsonArray a = fe.getAsJsonArray();
            out.add(new GhostRun.Frame(new P(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()), a.get(3).getAsFloat(), a.get(4).getAsFloat(), a.size() > 5 ? a.get(5).getAsString() : ""));
        }
        return out;
    }

    private static Map<String, Pair<List<Piece>, List<GhostRun.Frame>>> legsFile() {
        File f = Ec.configDir().resolve("p3sim-legs/" + P3Plan.skillName() + ".json").toFile();
        if (!f.exists()) return null;
        String key = f.getPath() + "@" + f.lastModified();
        Pair<String, Map<String, Pair<List<Piece>, List<GhostRun.Frame>>>> read;
        if (legsRead != null && legsRead.first.equals(key)) read = legsRead;
        else {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("classes");
                LinkedHashMap<String, Pair<List<Piece>, List<GhostRun.Frame>>> m = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    String c = e.getKey();
                    JsonElement v = e.getValue();
                    Pair<List<Piece>, List<GhostRun.Frame>> got = null;
                    try {
                        JsonArray pa = v.getAsJsonObject().getAsJsonArray("pieces");
                        List<Piece> pieces = new ArrayList<>();
                        if (pa != null) for (JsonElement pe : pa) {
                            JsonObject p = pe.getAsJsonObject();
                            String type = p.get("t").getAsString();
                            String id = switch (type) {
                                case "job" -> p.get("id").getAsString();
                                case "leap" -> p.get("onto").getAsString();
                                case "move" -> p.get("to") != null ? p.get("to").getAsString() : "";
                                default -> "";
                            };
                            pieces.add(new Piece(type, frames(p.getAsJsonArray("f")), id, p.get("k") != null ? p.get("k").getAsInt() : 0, frames(p.getAsJsonArray("land"))));
                        }
                        got = Pair.of(pieces, frames(v.getAsJsonObject().getAsJsonArray("lead")));
                    } catch (Throwable t) {
                        Sim.note("§cLegs file " + f.getName() + ": " + c + " left out (" + t.getMessage() + ")");
                    }
                    if (got != null) m.put(c, got);
                }
                read = new Pair<>(key, m);
            } catch (Throwable t) {
                Sim.note("§cLegs file " + f.getName() + " can't be read: " + t.getMessage());
                read = new Pair<>(key, Collections.<String, Pair<List<Piece>, List<GhostRun.Frame>>>emptyMap());
            }
            legsRead = read;
        }
        return read != null ? read.second : null;
    }

    private static final double LEG_BLEND = 6.0;

    private static void legTick(Bot b, GoldorPhase phase) {
        Legs lg = b.legs;
        if (lg == null) return;
        int n = phase.n();
        int steps = 0;
        loop:
        while (steps++ < 10) {
            Piece p = lg.i >= 0 && lg.i < lg.pieces.size() ? lg.pieces.get(lg.i) : null;
            if (p == null) break;
            switch (p.type) {
                case "move" -> {
                    if (p.frames.isEmpty()) {
                        Piece nx = lg.i + 1 < lg.pieces.size() ? lg.pieces.get(lg.i + 1) : null;
                        if (nx != null) {
                            switch (nx.type) {
                                case "job" -> b.pos = spotOf(nx.id);
                                case "ee" -> {
                                    Ee e = eeOf(nx.k);
                                    if (e != null) b.pos = e.spot;
                                }
                                default -> {}
                            }
                        }
                        lg.i++;
                        continue loop;
                    }
                    if (lg.from == null) lg.from = b.pos;
                    double last = p.frames.size() - 1;
                    lg.t = Math.min(last, lg.t + legSpeed(b, lg, n));
                    showFrame(b, lg, p, n);
                    if (lg.t >= last) { lg.i++; lg.t = 0.0; lg.from = null; lg.land = 0; }
                    place(b);
                    return;
                }
                case "job" -> {
                    boolean has = false;
                    for (Job j : jobs) if (j.bot == b && j.job.equals(p.id)) { has = true; break; }
                    if (has) break loop;
                    lg.i++;
                }
                case "sec" -> {
                    if (phase.section < p.k) break loop;
                    lg.i++;
                }
                case "ee" -> {
                    Ee e = eeOf(p.k);
                    if (e == null || e.owner != b.clazz) { lg.i++; continue loop; }
                    b.inSection = Math.max(b.inSection, p.k);
                    if (!eeArrived[p.k]) arrivedOnEe(b, p.k, n);
                    if (released[p.k] || phase.section > p.k) { b.hold = false; lg.i++; continue loop; }
                    b.hold = true;
                    break loop;
                }
                case "leap" -> {
                    DungeonClass c = null;
                    for (DungeonClass cc : CLASSES()) if (cc.name().equals(p.id)) { c = cc; break; }
                    if (c == null || c == b.clazz) { lg.i++; continue loop; }
                    Roles.Move om = null;
                    for (Roles.Move m : ontoLeaps) {
                        if (whoFor(m.who, m.section + 1) == b.clazz && whoFor(m.args.isEmpty() ? "" : m.args.get(0), m.section + 1) == c) { om = m; break; }
                    }
                    if (om != null) { if (ontoTarget(om) == null) break loop; }
                    else if (!leapReady(c, phase)) break loop;
                    lg.i++;
                    ServerPlayer pl = Players.playerOf(c);
                    Vec3 to = pl != null ? pl.position() : null;
                    if (to == null) {
                        Bot o = botOf(c);
                        to = o != null ? o.pos : null;
                    }
                    if (to == null) continue loop;
                    b.pos = to; b.to = null; b.vy = 0.0; b.leaptAt = n;
                    lg.leaptOnto.add(c.name());
                    setHeld(b, "leap");
                    Sim.sound(SoundEvents.ENDERMAN_TELEPORT, 1f, 1f, to, SoundSource.MASTER);
                    String name = pl != null ? pl.getGameProfile().name() : Roles.label(c);
                    if (name.equals(Sim.hostName())) GhostCapture.event("landed", b.clazz.name(), 0);
                    int gen = generation;
                    Bot fb = b;
                    Fight.later(1, "leap announce", () -> { if (gen == generation) Sim.chat(partyLine(fb, "Leaped to " + name + "!")); });
                    place(b);
                    return;
                }
                default -> lg.i++;
            }
        }
        settle(b, lg);
        place(b);
    }

    private static void settle(Bot b, Legs lg) {
        Piece prev = lg.i - 1 >= 0 && lg.i - 1 < lg.pieces.size() ? lg.pieces.get(lg.i - 1) : null;
        GhostRun.Frame f = null;
        if (prev != null && prev.type.equals("move") && lg.land >= 0 && lg.land < prev.land.size()) f = prev.land.get(lg.land);
        if (f != null) {
            lg.land++;
            b.vy = f.p.y - b.pos.y;
            b.pos = vec(f.p);
            b.yaw = f.yaw;
            b.pitch = f.pitch;
            return;
        }
        Double g = ground(b.pos.x, b.pos.z, b.pos.y);
        if (g != null && g < b.pos.y - 0.01) fall(b, b.pos.x, b.pos.z, g);
        else b.vy = 0.0;
    }

    private static boolean leapReady(DungeonClass c, GoldorPhase phase) {
        Integer into = null;
        for (int k = phase.section; k <= Math.min(5, phase.section + 1); k++) {
            Ee e = eeOf(k);
            if (e != null && e.owner == c && !released[k]) { into = k; break; }
        }
        if (into == null) return true;
        if (phase.section >= into) return true;
        Ee ee = eeOf(into);
        if (ee == null) return true;
        if (Players.isPlayerClass(c)) {
            ServerPlayer pl = ee.player();
            return pl != null && pl.position().distanceTo(ee.spot) <= 3.0;
        }
        return eeArrived[into];
    }

    private static double legSpeed(Bot b, Legs lg, int n) {
        double left = (lg.pieces.get(lg.i).frames.size() - 1) - lg.t;
        for (int k = lg.i + 1; k < lg.pieces.size(); k++) {
            Piece p = lg.pieces.get(k);
            switch (p.type) {
                case "move" -> left += Math.max(p.frames.size() - 1, 0);
                case "leap" -> {}
                case "job" -> {
                    Job j = null;
                    for (Job x : jobs) if (x.bot == b && x.job.equals(p.id)) { j = x; break; }
                    if (j == null) return 2.0;
                    if (j.at < 0) return 1.0;
                    int ticks = j.at - n;
                    return ticks <= 0 ? 3.0 : Math.clamp(left / ticks, 1.0, HURRY);
                }
                default -> { return 1.0; }
            }
        }
        return 1.0;
    }

    private static void showFrame(Bot b, Legs lg, Piece p, int n) {
        int i = Math.clamp((int) lg.t, 0, p.frames.size() - 1);
        GhostRun.Frame f0 = p.frames.get(i);
        GhostRun.Frame f1 = p.frames.get(Math.min(i + 1, p.frames.size() - 1));
        double k = lg.t - i;
        Vec3 pos = f0.p.dist(f1.p) > GhostRecording.LEAP_JUMP ? vec(f0.p)
                : new Vec3(f0.p.x + (f1.p.x - f0.p.x) * k, f0.p.y + (f1.p.y - f0.p.y) * k, f0.p.z + (f1.p.z - f0.p.z) * k);
        Vec3 from = lg.from;
        double w = 1.0 - lg.t / LEG_BLEND;
        if (from != null && w > 0) {
            P s = p.frames.get(0).p;
            pos = pos.add((from.x - s.x) * w, (from.y - s.y) * w, (from.z - s.z) * w);
        }
        b.pos = pos; b.yaw = f0.yaw; b.pitch = f0.pitch; b.to = null; b.vy = 0.0;
        if (n - b.leaptAt > 15) setHeld(b, f0.held.isEmpty() ? "breaker" : f0.held);
    }

    private static void ghosts(GoldorPhase phase) {
        int n = phase.n();
        for (Bot b : bots) {
            GhostPlayer g = b.ghost;
            if (g == null) continue;
            g.tick(n);
            if (g.finished() && phase.section >= 5 && !GoldorPhase.CORE_BOX.contains(b.pos)) {
                b.ghost = null;
                b.hold = false;
                b.inSection = 5;
                go(b, CORE_SPOT, n, -1, "the core (its run ended outside)");
            }
        }
        if (phase.section >= 5 && !coreEeLeaps && !coreIn) {
            Bot cb = coreBot();
            if (cb != null && cb.ghost != null && eeArrived[5] && !released[5]) leapOntoCoreEe(cb, n);
        }
    }

    private static void sectionStarted(GoldorPhase phase, int s) {
        int n = phase.n();
        Roles.Plan plan = P3Plan.plan();
        for (Job j : jobs) if (j.timeSection == s) j.at = n + (int) Math.round(j.sec * 20);
        for (Roles.Move m : plan.moves) {
            if (m.section != s) continue;
            Integer at = m.at != null ? Integer.valueOf(n + (int) Math.round(m.at * 20)) : null;
            switch (m.kind) {
                case "spot" -> {
                    Integer k = into(m.who);
                    if (k != null && at != null) eeSpotBy[k] = at;
                }
                case "preleap" -> {
                    Integer k = into(m.who);
                    if (k != null && at != null) preleapAt[k] = at;
                }
                case "hold" -> holdJob[s + 1] = "S" + (s + 1) + " T" + m.who;
                case "waits" -> {
                    Integer k = into(m.who);
                    if (k != null) waitsFor[k] = m.args;
                }
                case "onto" -> ontoLeaps.add(m);
                case "leaps" -> {
                    Bot b = botOf(Roles.whoIs(plan, m.who));
                    if (b != null && b.ghost == null && at != null) {
                        leaps.add(new Leap(b, at, () -> {
                            for (Bot o : bots) if (o != b && o.inSection == s && busy(o)) return asTarget(o);
                            ServerPlayer pl = null;
                            for (ServerPlayer x : Sim.players()) if (GoldorPhase.dtZone(x.position()) == s) { pl = x; break; }
                            if (pl == null) pl = Sim.host();
                            return pl != null ? asTarget(pl) : null;
                        }));
                    }
                }
                default -> {}
            }
        }
        if (s >= 5) { core(phase); return; }
        Ee ee = eeOf(s);
        Bot eeBot = ee != null && !ee.byYou() && s > phase.from ? botOf(ee.owner) : null;
        if (eeBot != null && !eeArrived[s]) dbg("§e" + eeBot.name + "§7 isn't on its " + ee.label + " spot yet: going on there, the others leap to it");
        Bot coreHold = null;
        if (s == 4 && ee == null) {
            Bot cb = coreBot();
            if (cb != null && cb.ghost == null && eeArrived[5] && cb.hold) coreHold = cb;
        }
        boolean coreYou = false;
        if (s == 4 && ee == null) {
            Ee e5 = eeOf(5);
            if (e5 != null && e5.byYou()) {
                ServerPlayer pl = e5.player();
                coreYou = pl != null && pl.position().distanceTo(e5.spot) <= 3.0;
            }
        }
        Ee eeYou = null;
        if (ee != null && ee.byYou() && s > phase.from) {
            ServerPlayer pl = ee.player();
            if (pl != null && pl.position().distanceTo(ee.spot) <= 3.0) eeYou = ee;
        }
        Bot onto = eeBot != null ? eeBot : coreHold;
        int i = 0;
        for (Bot b : bots) {
            if (b.ghost != null || b.legs != null) continue;
            if (b.hold && b.inSection > s && eeArrived[Math.min(b.inSection, 5)] && !released[Math.min(b.inSection, 5)]) continue;
            if (b != onto && onWayToCore(b)) continue;
            if (b.hold && b != onto) dbg("§e" + b.name + "§7 stops holding (section " + s + " started)");
            b.hold = false;
            if (b == onto) { b.hold = eeArrived[s] && !released[s]; continue; }
            if (b.inSection >= s) { walkOn(b, n); continue; }
            if (s == 3) {
                Ee e4 = eeOf(4);
                if (e4 != null && !e4.byYou() && e4.owner == b.clazz && jobs.stream().noneMatch(j -> j.bot == b && sectionOf(j.job) <= 3)) { b.inSection = s; continue; }
            }
            if (ontoFor(b.clazz, s) != null) continue;
            b.inSection = s;
            if (onto != null) {
                Bot o = onto;
                leaps.add(new Leap(b, n + 2 + gapTicks() * i++, () -> asTarget(o)));
            } else if (eeYou != null) {
                Ee ey = eeYou;
                leaps.add(new Leap(b, n + 2 + gapTicks() * i++, ey.spot, () -> {
                    ServerPlayer pl = ey.player();
                    return pl != null ? asTarget(pl) : null;
                }));
            } else if (coreYou) {
                leaps.add(new Leap(b, n + 2 + gapTicks() * i++, () -> {
                    Ee e5 = eeOf(5);
                    ServerPlayer pl = e5 != null ? e5.player() : null;
                    return pl != null ? asTarget(pl) : null;
                }));
            } else walkOn(b, n);
        }
    }

    private static boolean lateForEe(Bot b, int s) {
        if (s > 4 || eeArrived[s]) return false;
        Ee ee = eeOf(s);
        if (ee == null || ee.byYou()) return false;
        return botOf(ee.owner) == b && b.ghost == null;
    }

    private static boolean before(Job j, int into) { return sectionOf(j.job) < into || j.timeSection < into; }

    private static Integer into(String token) { return token.equals("core") ? Integer.valueOf(5) : toIntOrNull(removePrefix(token, "ee")); }

    private static void earlyEnterBots(GoldorPhase phase) {
        int s = phase.section;
        HashSet<Bot> sent = new HashSet<>();
        for (int into = s; into <= 5; into++) {
            if (into == s && (into > 4 || eeArrived[into])) continue;
            Ee ee = eeOf(into);
            if (ee == null) continue;
            if (ee.byYou()) continue;
            Bot b = botOf(ee.owner);
            if (b == null || sent.contains(b)) continue;
            sent.add(b);
            if (b.ghost != null || b.legs != null) continue;
            if (into > s && (b.inSection >= into || busy(b) || b.hold)) continue;
            final int bi = into == 5 ? 4 : into;
            if (jobs.stream().anyMatch(j -> j.bot == b && before(j, bi))) continue;
            if (b.pos.distanceTo(ee.spot) < 0.5) {
                b.inSection = into;
                b.hold = true;
                b.yaw = ee.yaw;
                b.pitch = ee.pitch;
                arrivedOnEe(b, into, phase.n());
                continue;
            }
            if (into == 5 && digCore(b, ee, phase)) continue;
            if (!Objects.equals(b.to, ee.spot)) go(b, ee.spot, phase.n(), P3Sim.realMoves() ? eeSpotBy[into] : phase.n(), "its " + ee.label + " spot", Pair.of(ee.yaw, ee.pitch));
        }
    }

    private static boolean onWayToCore(Bot b) {
        Ee e5 = eeOf(5);
        return e5 != null && !e5.byYou() && e5.owner == b.clazz && b.inSection < 5 && jobs.stream().noneMatch(j -> j.bot == b && before(j, 4));
    }

    private static boolean digCore(Bot b, Ee ee, GoldorPhase phase) {
        if (b.dig != null) return true;
        int zone = GoldorPhase.dtZone(b.pos);
        if (!(zone == 1 || zone == 2 || (zone == -1 && b.pos.z > CORE_N))) return false;
        Double gw = ground(54.5, CORE_N + 3.0, 125.0);
        Vec3 wall = new Vec3(54.5, gw != null && gw != WALL ? gw : 120.0, CORE_N + 3.0);
        List<Vec3> way = List.of(new Vec3(54.5, 115.0, CORE_N - 1.0), new Vec3(54.5, 115.0, 56.0), ee.spot);
        if (b.pos.distanceTo(wall) > 0.6) {
            int mining = (int) ((wall.distanceTo(way.get(0)) + way.get(0).distanceTo(way.get(1)) + way.get(1).distanceTo(way.get(2))) / DIG);
            int due = !P3Sim.realMoves() ? phase.n() : (eeSpotBy[5] >= 0 ? eeSpotBy[5] - mining : -1);
            if (!Objects.equals(b.to, wall)) go(b, wall, phase.n(), due, "the core's S2 wall (mining in to " + ee.label + ")");
            return true;
        }
        b.to = null;
        b.face = null;
        b.dig = new ArrayDeque<>(way);
        dbg("§e" + b.name + "§7 mines into the core from S2, through it and out of its door to its " + ee.label + " spot");
        return true;
    }

    private static void digOn(Bot b, ArrayDeque<Vec3> way, int n) {
        double left = DIG * (b.due >= 0 && b.due <= n ? 2.0 : 1.0);
        while (left > 1e-6 && !way.isEmpty()) {
            Vec3 d = way.getFirst().subtract(b.pos);
            double len = d.length();
            if (Math.hypot(d.x, d.z) > 0.01) { b.yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)); b.pitch = 10f; }
            if (len <= left) { b.pos = way.removeFirst(); left -= len; }
            else { b.pos = b.pos.add(d.scale(left / len)); left = 0.0; }
        }
        b.vy = 0.0;
        boolean broke = false;
        ServerLevel level = Sim.level();
        for (int bx = (int) Math.floor(b.pos.x - 0.3); bx <= (int) Math.floor(b.pos.x + 0.3); bx++)
            for (int by = (int) Math.floor(b.pos.y); by <= (int) Math.floor(b.pos.y + 1.79); by++)
                for (int bz = (int) Math.floor(b.pos.z - 0.3); bz <= (int) Math.floor(b.pos.z + 0.3); bz++) {
                    BlockPos pos = new BlockPos(bx, by, bz);
                    BlockState s = level.getBlockState(pos);
                    if (s.isAir() || s.getDestroySpeed(level, pos) < 0 || s.is(Blocks.BARRIER) ||
                            s.is(Blocks.BEDROCK) || s.getBlock() instanceof CommandBlock) continue;
                    level.levelEvent(2001, pos, Block.getId(s));
                    com.teslamaps.adm.sim.Blocks.set(pos, Blocks.AIR.defaultBlockState());
                    broke = true;
                    Fight.later((int) Math.round(P3Sim.breakerRegen() * 20), "bot dig regen", () -> {
                        if (level.getBlockState(pos).isAir() && Fight.phase instanceof GoldorPhase gp && gp.section < 5) com.teslamaps.adm.sim.Blocks.set(pos, s);
                    });
                }
        if (broke) swing(b);
        if (way.isEmpty()) {
            b.dig = null;
            if (b.face != null) { b.yaw = b.face.first; b.pitch = b.face.second; }
        }
    }

    private static void preleaps(GoldorPhase phase) {
        int n = phase.n();
        int into = phase.section + 1;
        if (into > 5) return;
        Ee ee = eeOf(into);
        if (ee == null) return;
        Bot eeBot = ee.byYou() ? null : botOf(ee.owner);
        boolean open = ee.byYou() ? youArrived[into] : eeBot != null && eeArrived[into] && eeBot.dig == null && (into < 5 || eeBot.hold) &&
                n >= (preleapAt[into] >= 0 ? preleapAt[into] : 0);
        if (!open) return;
        Supplier<Target> target = eeBot != null ? () -> asTarget(eeBot) : () -> {
            ServerPlayer pl = ee.player();
            return pl != null ? asTarget(pl) : null;
        };
        for (Bot b : bots) {
            if (b == eeBot || b.ghost != null || b.inSection >= into || busy(b) || onWayToCore(b) || ontoFor(b.clazz, into) != null) continue;
            b.inSection = into;
            lastLeap = Math.max(n + 1, lastLeap + (eeBot != null ? 2 : gapTicks()));
            if (holdJob[into] != null && jobs.stream().anyMatch(j -> j.bot == b && j.job.equals(holdJob[into]))) b.hold = true;
            leaps.add(new Leap(b, lastLeap, ee.byYou() ? ee.spot : null, target));
        }
    }

    private static Roles.Move ontoFor(DungeonClass c, int into) {
        for (Roles.Move m : ontoLeaps) if (m.section + 1 == into && whoFor(m.who, into) == c) return m;
        return null;
    }

    private static Target ontoTarget(Roles.Move m) {
        int into = m.section + 1;
        if (m.args.isEmpty()) return null;
        String t = m.args.get(0);
        DungeonClass c = whoFor(t, into);
        if (c == null) return null;
        Vec3 stand = spotOf("S" + into + " T" + t);
        ServerPlayer pl = Players.playerOf(c);
        if (pl != null) return pl.position().distanceTo(stand) <= 3.0 ? asTarget(pl) : null;
        Bot o = botOf(c);
        return o != null && o.pos.distanceTo(stand) <= 3.0 ? asTarget(o) : null;
    }

    private static void ontoLeapsTick(GoldorPhase phase) {
        for (Roles.Move m : ontoLeaps) {
            int into = m.section + 1;
            Bot b = botOf(whoFor(m.who, into));
            if (b == null || !(b.ghost == null && b.legs == null && b.inSection < into)) continue;
            if (leaps.stream().anyMatch(l -> l.bot == b) || jobs.stream().anyMatch(j -> j.bot == b && sectionOf(j.job) < into)) continue;
            Target t = ontoTarget(m);
            if (t == null) continue;
            b.inSection = into;
            b.hold = false;
            leaps.add(new Leap(b, phase.n() + 1, () -> {
                Target nt = ontoTarget(m);
                return nt != null ? nt : t;
            }));
            dbg("§e" + b.name + "§7 leaps onto " + t.name + " at S" + into + " T" + m.args.get(0));
        }
    }

    private static void releaseEarlyEnterers(GoldorPhase phase) {
        int n = phase.n();
        int s = phase.section;
        for (int into = s; into <= Math.min(s + 1, 5); into++) {
            Ee e = eeOf(into);
            if (e == null || e.byYou()) continue;
            Bot b = botOf(e.owner);
            if (b == null) continue;
            if (!b.hold || !eeArrived[into] || released[into]) continue;
            for (ServerPlayer pl : Sim.players()) {
                DungeonClass c = Players.classOf(pl);
                if (!youOn[into].contains(c) && pl.position().distanceTo(b.pos) < 3.0) {
                    youOn[into].add(c);
                    dbg("§a" + pl.getGameProfile().name() + " is on " + b.name + "§7 (EE" + into + ")");
                }
            }
            if (into == 5 && s == 4) for (Bot o : bots) {
                if (o == b || o.ghost != null || o.legs != null || youOn[5].contains(o.clazz) || o.pos.distanceTo(b.pos) >= 3.0) continue;
                youOn[5].add(o.clazz);
                dbg("§a" + o.name + " is on " + b.name + "§7 (EE5)");
            }
        }
        for (int into = s; into <= Math.min(s + 1, 5); into++) {
            Ee ee = eeOf(into);
            if (ee == null || ee.byYou()) continue;
            Bot b = botOf(ee.owner);
            if (b == null) continue;
            if (released[into] || !b.hold || !eeArrived[into] || b.inSection < into) continue;
            List<String> waits = waitsFor[into];
            List<DungeonClass> players = new ArrayList<>();
            for (DungeonClass c : Players.playerClasses()) if (c != b.clazz) players.add(c);
            List<DungeonClass> needed;
            if (waits == null) needed = players;
            else {
                needed = new ArrayList<>();
                for (String w : waits) {
                    DungeonClass c = whoFor(w, into);
                    if (c != null && players.contains(c)) needed.add(c);
                }
            }
            final int fi = into;
            Predicate<DungeonClass> there = c -> leapt(c, b, fi) || fi == s && inSectionNow(c, fi);
            boolean ok;
            if (into == s && n - sectionN[s] >= 600) ok = true;
            else if (needed.stream().anyMatch(c -> !there.test(c))) { allInAt[into] = -1; ok = false; }
            else if (needed.stream().anyMatch(c -> !leapt(c, b, fi))) {
                if (allInAt[into] < 0) { allInAt[into] = n; dbg("§e" + b.name + "§7: everyone's in S" + into + ", not all by EE" + into + ": moving on in " + (IN_GRACE / 20.0) + "s"); }
                ok = n - allInAt[into] >= IN_GRACE;
            }
            else if (into == s && n - sectionN[s] >= 200) ok = true;
            else if (waits != null) ok = waits.stream().allMatch(t -> there.test(whoFor(t, fi)));
            else ok = (into == s || preleapOpen(into, n)) &&
                    bots.stream().allMatch(o -> o == b || there.test(o.clazz)) && players.stream().allMatch(there);
            if (!ok) { readyAt[into] = -1; continue; }
            if (readyAt[into] < 0) { readyAt[into] = n; dbg("§e" + b.name + "§7: everyone's on EE" + into + (b.ghost == null ? ", moving in " + (RELEASE_DELAY / 20.0) + "s" : "")); }
            if (b.ghost != null) continue;
            if (n - readyAt[into] < RELEASE_DELAY) continue;
            released[into] = true;
            dbg("§c" + b.name + " moves on from EE" + into + "§7: " + waitStatus(b, into) + (into == s ? ", " + ((n - sectionN[s]) / 20.0) + "s into S" + s : ""));
            Ee recore = into == 5 ? eeOf(6) : null;
            if (recore != null && jobs.stream().noneMatch(j -> j.bot == b)) toRecore(b, recore, s, n);
            else { b.hold = false; walkOn(b, n); }
        }
    }

    private static void toRecore(Bot b, Ee recore, int s, int n) {
        b.hold = true;
        if (s >= 5) { go(b, recore.spot, n, -1, "the recore (in the core)", Pair.of(recore.yaw, recore.pitch)); return; }
        b.to = null;
        b.due = -1;
        b.face = Pair.of(recore.yaw, recore.pitch);
        b.dig = new ArrayDeque<>(List.of(new Vec3(54.5, 115.0, 56.0), recore.spot));
        dbg("§e" + b.name + "§7 mines back into the core to the recore (" + shortStr(recore.spot) + ")");
    }

    private static void recoreAfterJobs(GoldorPhase phase) {
        if (coreIn || phase.section < 4) return;
        Bot b = coreBot();
        if (b == null || !(b.ghost == null && b.legs == null && eeArrived[5] && !b.hold && b.to == null && b.dig == null)) return;
        if (jobs.stream().anyMatch(j -> j.bot == b) || leaps.stream().anyMatch(l -> l.bot == b)) return;
        Ee recore = eeOf(6);
        if (recore == null) return;
        released[5] = true;
        toRecore(b, recore, phase.section, phase.n());
    }

    private static final int[] allInAt = filled(7, -1);

    private static boolean inSectionNow(DungeonClass c, int into) {
        Vec3 p = posOf(c);
        if (p == null) {
            Bot o = botOf(c);
            if (o != null && o.entity != null) p = o.pos;
        }
        if (p == null) return false;
        if (into >= 5) return GoldorPhase.CORE_BOX.contains(p);
        return GoldorPhase.dtZone(p) >= into;
    }

    private static String waitStatus(Bot b, int into) {
        List<String> waits = waitsFor[into];
        List<DungeonClass> who = new ArrayList<>();
        if (waits != null) {
            for (String w : waits) {
                DungeonClass c = whoFor(w, into);
                if (c != null) who.add(c);
            }
        } else for (Bot o : bots) if (o != b) who.add(o.clazz);
        who.addAll(Players.playerClasses());
        List<DungeonClass> distinct = new ArrayList<>();
        for (DungeonClass c : who) if (!distinct.contains(c) && c != b.clazz) distinct.add(c);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < distinct.size(); i++) {
            DungeonClass c = distinct.get(i);
            if (i > 0) sb.append(" ");
            ServerPlayer pl = Players.playerOf(c);
            String name = pl != null ? pl.getGameProfile().name() : Roles.label(c);
            sb.append(leapt(c, b, into) ? "§a+" + name + "§7" : "§c-" + name + "§7");
        }
        return sb + (waits != null ? " (list: " + String.join(" ", waits) + ")" : " (everyone)");
    }

    private static void dbgHolds(GoldorPhase phase) {
        for (Bot b : bots) {
            Integer ee = null;
            for (int k = 1; k <= 5; k++) {
                Ee e = eeOf(k);
                if (e != null && e.owner == b.clazz) { ee = k; break; }
            }
            String state;
            if (b.ghost != null) state = "§dghost§7: " + b.ghost.status;
            else if (b.hold) state = "§6holding§7 (EE" + ee + ": " + (ee != null ? waitStatus(b, ee) : "?") + ")";
            else if (b.to != null) state = "moving to " + shortStr(b.to);
            else state = "standing";
            Job nx = next(b);
            dbg("§e" + b.name + "§7 in S" + b.inSection + ": " + state + "; next " + (nx != null ? nx.job : "none"));
        }
    }

    private static boolean allDigits(String t) {
        for (int i = 0; i < t.length(); i++) if (!Character.isDigit(t.charAt(i))) return false;
        return true;
    }

    private static DungeonClass whoFor(String token, int into) {
        if (allDigits(token)) {
            String j = "S" + into + " T" + token;
            ServerPlayer pl = Players.playerDoing(j);
            if (pl != null) return Players.classOf(pl);
            DungeonClass d = P3Plan.doer(j);
            if (d != null && !Players.isPlayerClass(d)) return d;
            return P3Plan.roleDoerBot(j);
        }
        Integer k = into(token);
        if (k == null) return null;
        Ee e = eeOf(k);
        return e != null ? e.owner : null;
    }

    private static boolean leapt(DungeonClass c, Bot onto, int into) {
        if (c == null || c == onto.clazz) return true;
        if (Players.isPlayerClass(c)) return youOn[into].contains(c);
        if (youOn[into].contains(c)) return true;
        Bot o = botOf(c);
        if (o != null && o.legs != null) return o.legs.leaptOnto.contains(onto.clazz.name()) || !o.legs.expectsLeap(onto.clazz.name());
        if (o == null) return true;
        GhostPlayer g = o.ghost;
        if (g != null) return o.pos.distanceTo(onto.pos) < 3.0 || g.leaptOnto(onto.clazz.name(), Math.max(eeArrivedN[into], 0)) || !g.expectsLeap(onto.clazz.name(), into);
        return o.inSection >= into && leaps.stream().noneMatch(l -> l.bot == o);
    }

    private static boolean preleapOpen(int into, int n) { return n >= (preleapAt[into] >= 0 ? preleapAt[into] : 0); }

    private static void youAtEarlyEnter(GoldorPhase phase) {
        int into = phase.section + 1;
        if (into > 4) return;
        Ee ee = eeOf(into);
        if (ee == null || !ee.byYou()) return;
        if (youArrived[into]) return;
        ServerPlayer p = ee.player();
        if (p == null) return;
        if (p.position().distanceTo(ee.spot) > 3.0) return;
        youArrived[into] = true;
        Sim.asPlayer(p, false, () -> {
            Sim.note("§aAt your " + ee.label + "§7: the party leaps to you.");
            return null;
        });
    }

    private static boolean holding(GoldorPhase phase) {
        if (!P3Plan.waitForYou) return false;
        Ee ee = eeOf(phase.section + 1);
        if (ee == null || !(ee.byYou() && ee.into <= 4)) return false;
        if (youArrived[ee.into]) return false;
        if (!holdNoted[ee.into] && phase.stations.stream().filter(x -> x.section == phase.section && !x.done).count() == 1) {
            holdNoted[ee.into] = true;
            Sim.asPlayer(ee.player(), false, () -> {
                Sim.note("§7The party holds S" + phase.section + "'s last job until you're at your §e" + ee.label + "§7 spot (Wait for you, in the menu's Early Enters).");
                return null;
            });
        }
        return true;
    }

    private static boolean coreIn = false;

    private static Bot coreBot() {
        Ee e = eeOf(5);
        if (e == null || e.byYou()) return null;
        return botOf(e.owner);
    }

    private static void core(GoldorPhase phase) {
        int n = phase.n();
        coreIn = false;
        Bot cb = coreBot();
        if (cb != null && eeArrived[5] && !released[5] && (cb.hold || cb.ghost != null)) { leapOntoCoreEe(cb, n); return; }
        if (cb != null && cb.ghost != null) { if (recoredN >= 0) leapIntoCore(phase); return; }
        if (cb == null || !eeArrived[5] || recored(cb)) leapIntoCore(phase);
    }

    private static void leapOntoCoreEe(Bot cb, int n) {
        coreEeLeaps = true;
        int i = 0;
        for (Bot b : bots) {
            if (b == cb || b.ghost != null) continue;
            b.hold = false;
            if (b.inSection < 5) {
                b.inSection = 5;
                leaps.add(new Leap(b, n + 2 + gapTicks() * i++, () -> asTarget(cb)));
            }
        }
        dbg("core open: everyone leaps onto §e" + cb.name + "§7 by the core first, then it recores");
    }

    private static void intoCoreWhenRecored(GoldorPhase phase) {
        if (coreIn || phase.section < 5) return;
        Bot cb = coreBot();
        if (cb == null) return;
        if (cb.ghost != null) {
            if (recoredN >= 0 || phase.n() - sectionN[5] >= 400) leapIntoCore(phase);
            return;
        }
        if (released[5] && recored(cb)) {
            if (recoredN < 0) {
                recoredN = phase.n();
                GhostCapture.event("recore", cb.clazz.name(), 0);
            }
            leapIntoCore(phase);
        }
    }

    private static boolean recored(Bot cb) { return cb.hold && cb.to == null && cb.dig == null; }

    private static void leapIntoCore(GoldorPhase phase) {
        coreIn = true;
        int n = phase.n();
        dbg("everyone leaps into the core");
        Ee ee = eeOf(5);
        Supplier<Target> onto;
        if (ee == null) onto = null;
        else if (ee.byYou()) {
            ServerPlayer p = ee.player();
            onto = p != null && GoldorPhase.CORE_BOX.inflate(4.0).contains(p.position()) ? () -> asTarget(p) : null;
        } else {
            Bot ob = botOf(ee.owner);
            onto = ob != null ? () -> asTarget(ob) : null;
        }
        final Supplier<Target> ontoF = onto;
        for (int i = 0; i < bots.size(); i++) {
            Bot b = bots.get(i);
            if (b.ghost != null) continue;
            b.hold = false;
            b.inSection = 5;
            Vec3 spot = CORE_SPOT.add((i - 1.5) * 1.5, 0.0, 2.0 + KRandom.DEFAULT.nextDouble());
            if (ontoF != null && botOf(ee != null ? ee.owner : null) != b) leaps.add(new Leap(b, n + 1 + i * 2, () -> ontoF.get()));
            int gen = generation;
            Fight.later(onto != null ? 12 + i * 2 : 0, "bots into core", () -> { if (gen == generation) go(b, spot, 0, -1, "the core"); });
        }
    }

    private static int gapTicks() { return Math.max((int) (P3Plan.leapGap * 20), 1); }

    private static Job next(Bot b, Predicate<Job> of) {
        Job best = null;
        for (Job j : jobs) {
            if (j.bot != b || !of.test(j)) continue;
            if (best == null || j.timeSection < best.timeSection || (j.timeSection == best.timeSection && Double.compare(j.sec, best.sec) < 0)) best = j;
        }
        return best;
    }

    private static Job next(Bot b) { return next(b, j -> true); }

    private static boolean busy(Bot b) {
        for (Job j : jobs) if (j.bot == b && j.at >= 0) return true;
        return false;
    }

    private static void walkOn(Bot b, int n) {
        if (b.ghost != null || b.hold) return;
        Job j = lateForEe(b, planned) ? next(b, x -> before(x, planned)) : next(b);
        if (j == null) return;
        if (j.job.startsWith("gate") && inReach(b, j.job)) return;
        if (j.at >= 0 || sectionOf(j.job) <= b.inSection) {
            Spots.Spot ps = presetStand(j.job);
            go(b, spotOf(j.job), n, j.at, j.job + (j.at < 0 ? " (its section not started)" : ""), ps != null ? Pair.of(ps.yaw, ps.pitch) : null, leadOf(j.job));
        }
    }

    private static int leadOf(String job) { return job.length() == 5 && job.startsWith("S") && job.charAt(3) == 'T' ? TERMINAL_LEAD : 2; }

    private static final int TERMINAL_LEAD = 40;
    private static final int MIN_LEAD = 12;

    private static boolean inReach(Bot b, String job) {
        Integer g = gateNum(job);
        if (g != null) return g >= 0 && g < GoldorPhase.GATE_BOXES.length && GoldorPhase.GATE_BOXES[g].distanceToSqr(b.pos) <= 100.0;
        return b.pos.distanceTo(spotOf(job)) <= 2.5;
    }

    private static int sectionOf(String job) {
        Integer g = gateNum(job);
        return g != null ? g : Integer.parseInt(job.substring(1, 2));
    }

    private static Vec3 spotOf(String job) {
        Spots.Spot ps = presetStand(job);
        if (ps != null) return new Vec3(ps.x, ps.y, ps.z);
        Vec3 s = STANDS.get(job);
        if (s != null) return s;
        Integer g = gateNum(job);
        if (g != null && g >= 0 && g < GATES.length) return GATES[g];
        return CORE_SPOT;
    }

    private static Spots.Spot presetStand(String job) { return P3Plan.preset().defaults.get("stand/" + job); }

    private static void move(Bot b, int n) {
        if (b.dig != null) { digOn(b, b.dig, n); place(b); return; }
        Vec3 to = b.to;
        if (to != null && n >= b.goAt && follow(b, to, n)) { place(b); return; }
        if (to != null && n >= b.goAt) {
            Vec3 d = to.subtract(b.pos);
            double len = d.length();
            double flat = Math.hypot(d.x, d.z);
            if (flat > 0.01) { b.yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)); b.pitch = 0f; }
            boolean late = b.due >= 0 && (b.due <= n || len / (b.due - n) > WALK * 1.15);
            if (len <= WALK && Math.abs(d.y) <= 1.25) arrive(b, to);
            else {
                Vec3 step = flat > 0.01 ? d.multiply(1.0, 0.0, 1.0).scale(Math.min(WALK, flat) / flat) : Vec3.ZERO;
                Vec3 next = b.pos.add(step);
                Double g = ground(next.x, next.z, b.pos.y);
                boolean walkable = g != null && !(to.y - b.pos.y > 1.25 && flat < 2.0) && !(g < b.pos.y - 4 && to.y > g + 3) &&
                        (flat > 0.01 || g < b.pos.y - 0.01);
                if ((late || !walkable) && n >= b.blinkReady) blink(b, to, n, b.due >= 0 && b.due <= n ? 2 : late ? 4 : 8);
                else if (walkable) fall(b, next.x, next.z, g);
                else {
                    Double h = ground(b.pos.x, b.pos.z, b.pos.y);
                    if (h != null) fall(b, b.pos.x, b.pos.z, h);
                }
            }
        } else if (to == null && !b.hold) {
            Double g = ground(b.pos.x, b.pos.z, b.pos.y);
            if (g != null && g < b.pos.y - 0.01) fall(b, b.pos.x, b.pos.z, g);
        }
        place(b);
    }

    private static final double HURRY = 4.0;
    private static final Set<String> ROUTE_ITEMS = Set.of("STARRED_BONZO_STAFF", "BONZO_STAFF", "JERRY_STAFF", "SUPERBOOM_TNT", "HYPERION", "ASPECT_OF_THE_VOID", "ENDER_PEARL");

    private static boolean follow(Bot b, Vec3 to, int n) {
        if (!P3Sim.realMoves()) return false;
        if (!Objects.equals(b.routeTo, to)) {
            b.routeTo = to;
            b.routeT = 0.0;
            Routes.Route r = Routes.find(b.pos, to, Party::closed);
            b.route = r;
            if (r == null) dbg("§e" + b.name + "§7: no real route to " + shortStr(to) + ", walking");
            else dbg("§e" + b.name + "§7: route " + String.format(Locale.ROOT, "%.1f", r.ticks() / 20) + "s at a player's pace" +
                    (b.due >= 0 ? ", " + String.format(Locale.ROOT, "%.1f", (b.due - b.lead - n) / 20.0) + "s to be there" : ""));
        }
        Routes.Route r = b.route;
        if (r == null) return false;
        b.routeT += pace(b, r.ticks() - b.routeT, n);
        if (b.routeT >= r.ticks()) { arrive(b, to); return true; }
        Vec3 p = r.pos(b.routeT);
        int i = r.frameAt(b.routeT);
        float ry = r.yaw[i];
        if (!Float.isNaN(ry)) {
            b.yaw += Math.clamp(Mth.wrapDegrees(ry - b.yaw), -40f, 40f);
            b.pitch += Math.clamp(r.pitch[i] - b.pitch, -30f, 30f);
        } else if (Math.hypot(p.x - b.pos.x, p.z - b.pos.z) > 0.05) b.yaw = (float) Math.toDegrees(Math.atan2(-(p.x - b.pos.x), p.z - b.pos.z));
        double rise = p.y - b.pos.y;
        String h = r.held[i];
        if (h != null && ROUTE_ITEMS.contains(h)) {
            b.routeHeld = h;
            b.routeHeldUntil = n + 8;
            if (rise > 1.0 && b.vy <= 0.5 && !h.equals("HYPERION")) swing(b);
        }
        b.vy = rise;
        b.pos = p;
        return true;
    }

    private static double pace(Bot b, double left, int n) {
        if (b.due < 0 || left <= 0) return 1.0;
        int min = Math.min(b.lead, MIN_LEAD);
        if (n + left <= b.due - min) return 1.0;
        int by = b.due - min - n;
        return by <= 0 ? HURRY : Math.clamp(left / by, 1.0, HURRY);
    }

    private static boolean closed(Integer idObj) {
        int id = idObj;
        if (leadIn) return true;
        GoldorPhase ph = curPhase;
        if (ph == null) return false;
        if (id >= 1 && id <= 3) return !ph.gateIsDown(id);
        if (id == Routes.CORE_DOOR) return ph.section < 5;
        return ph.section <= id - 4;
    }

    private static void fall(Bot b, double x, double z, double g) {
        if (g >= b.pos.y - 0.01) { b.pos = new Vec3(x, g, z); b.vy = 0.0; return; }
        b.vy = (b.vy - 0.08) * 0.98;
        b.pos = new Vec3(x, Math.max(g, b.pos.y + b.vy), z);
        if (b.pos.y <= g) b.vy = 0.0;
    }

    private static void arrive(Bot b, Vec3 to) {
        b.pos = to; b.to = null; b.vy = 0.0; b.route = null; b.routeTo = null;
        if (b.face != null) { b.yaw = b.face.first; b.pitch = b.face.second; }
    }

    private static void blink(Bot b, Vec3 to, int n, int gap) {
        b.blinkReady = n + gap;
        b.blinkAt = n;
        b.vy = 0.0;
        Vec3 d = to.subtract(b.pos);
        if (d.length() <= BLINK) { arrive(b, to); return; }
        Vec3 q = b.pos.add(d.normalize().scale(BLINK));
        Double g = ground(q.x, q.z, q.y);
        b.pos = g != null && g >= q.y - 3 ? new Vec3(q.x, g, q.z) : q;
    }

    private static Double ground(double x, double z, double y) {
        Double best = null;
        Set<Integer> xs = new LinkedHashSet<>(List.of((int) Math.floor(x - 0.29), (int) Math.floor(x + 0.29)));
        Set<Integer> zs = new LinkedHashSet<>(List.of((int) Math.floor(z - 0.29), (int) Math.floor(z + 0.29)));
        for (int cx : xs)
            for (int cz : zs) {
                Double g = column(cx, cz, y);
                if (g != null && g == WALL) return null;
                if (g != null && (best == null || g > best)) best = g;
            }
        return best;
    }

    private static final double WALL = Double.MAX_VALUE;

    private static Double column(int bx, int bz, double y) {
        ServerLevel level = Sim.level();
        int cy = (int) Math.floor(y + 1.25);
        int bottom = cy - 40;
        while (cy > bottom) {
            BlockPos pos = new BlockPos(bx, cy, bz);
            BlockState state = level.getBlockState(pos);
            if (state.getFluidState().is(FluidTags.LAVA)) return WALL;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                double top = cy + shape.max(Direction.Axis.Y);
                if (top > y + 1.25) return WALL;
                for (int hy = (int) Math.floor(top + 0.01); hy <= (int) Math.floor(top + 1.79); hy++) {
                    if (hy == cy) continue;
                    BlockPos hp = new BlockPos(bx, hy, bz);
                    VoxelShape hs = level.getBlockState(hp).getCollisionShape(level, hp);
                    if (!hs.isEmpty() && hy + hs.min(Direction.Axis.Y) < top + 1.8) return WALL;
                }
                return top;
            }
            cy--;
        }
        return null;
    }

    private static void leapTo(Bot b, Target to, int n) {
        Vec3 at = to.pos;
        b.pos = at; b.to = null; b.vy = 0.0; b.leaptAt = n; b.route = null; b.routeTo = null;
        if (to.yaw != null) b.yaw = to.yaw;
        Sim.sound(SoundEvents.ENDERMAN_TELEPORT, 1f, 1f, at, SoundSource.MASTER);
        String name = to.name;
        if (name == null) return;
        if (name.equals(Sim.hostName())) GhostCapture.event("landed", b.clazz.name(), 0);
        int gen = generation;
        Fight.later(1, "leap announce", () -> { if (gen == generation) Sim.chat(partyLine(b, "Leaped to " + name + "!")); });
    }

    public static String partyLine(Bot b, String text) { return "§9Party §8> §b[MVP§c+§b] " + b.name + "§f: " + text; }

    private static void swing(Bot b) {
        if (b.entity != null) b.entity.swing(InteractionHand.MAIN_HAND);
    }

    private static void hold(Bot b, int n) {
        String key;
        boolean leapSoon = false;
        for (Leap l : leaps) if (l.bot == b && l.at - n >= 0 && l.at - n <= 10) { leapSoon = true; break; }
        if (leapSoon || n - b.leaptAt < 20) key = "leap";
        else if (n < b.routeHeldUntil) key = b.routeHeld != null ? b.routeHeld : "breaker";
        else if (n - b.blinkAt < 15) key = "hyperion";
        else key = "breaker";
        setHeld(b, key);
    }

    private static void setHeld(Bot b, String key) {
        Mannequin e = b.entity;
        if (e == null) return;
        if (b.heldKey.equals(key)) return;
        b.heldKey = key;
        ItemStack stack = switch (key) {
            case "leap", "INFINITE_SPIRIT_LEAP" -> SimItems.LEAP();
            case "hyperion", "HYPERION" -> SimItems.HYPERION();
            case "terminator", "TERMINATOR" -> SimItems.TERMINATOR();
            case "SUPERBOOM_TNT" -> SimItems.SUPERBOOM();
            case "ASPECT_OF_THE_VOID" -> SimItems.AOTV();
            case "STARRED_BONZO_STAFF", "BONZO_STAFF" -> SimItems.BONZO();
            case "JERRY_STAFF" -> SimItems.JERRY();
            case "ITEM_SPIRIT_BOW" -> SimItems.SPIRIT_BOW();
            case "MOSQUITO_BOW" -> SimItems.MOSQUITO();
            case "ENDER_PEARL" -> SimItems.PEARLS();
            case "WITHER_CLOAK" -> SimItems.CLOAK();
            case "PET_ROD" -> SimItems.PET_ROD();
            default -> SimItems.DUNGEONBREAKER();
        };
        e.setItemSlot(EquipmentSlot.MAINHAND, stack);
    }

    private static String restingItem(DungeonClass c) {
        if (c == DungeonClass.HEALER) return "breaker";
        if (c == DungeonClass.ARCHER && !(Fight.phase instanceof P4Necron)) return "terminator";
        return "hyperion";
    }

    // ------------------------------------------------------------------ outside P3

    public static void standAt(List<Vec3> spots) {
        clear();
        if (!botsOn()) return;
        List<Bot> l = bots();
        for (int i = 0; i < l.size(); i++) {
            Bot b = l.get(i);
            spawn(b, spots.get(i % spots.size()));
            place(b);
        }
    }

    public static void startP4(boolean fromP3) {
        if (!botsOn()) return;
        leaps.clear();
        jobs.clear();
        for (Bot b : bots) b.ghost = null;
        int gen = generation;
        List<Bot> l = bots();
        for (int i = 0; i < l.size(); i++) {
            Bot b = l.get(i);
            Vec3 spot = P4_SPOTS.get(b.clazz);
            if (spot == null) throw new java.util.NoSuchElementException("Key " + b.clazz + " is missing in the map.");
            Runnable land = () -> {
                if (gen == generation) {
                    if (b.entity == null || b.entity.isRemoved()) spawn(b, spot);
                    b.pos = spot; b.to = null; b.hold = false; b.yaw = 180f; b.pitch = 0f;
                    b.heldKey = "";
                    setHeld(b, restingItem(b.clazz));
                    place(b);
                }
            };
            if (fromP3 && b.entity != null && !b.entity.isRemoved()) Fight.later(40 + 6 * i, "bot into P4", land);
            else land.run();
        }
    }

    private static final Map<DungeonClass, Vec3> P4_SPOTS = Map.of(
            DungeonClass.MAGE, new Vec3(54.5, 64.0, 101.5), DungeonClass.HEALER, new Vec3(59.5, 64.0, 111.5),
            DungeonClass.BERSERK, new Vec3(50.5, 64.0, 110.5), DungeonClass.ARCHER, new Vec3(47.5, 64.0, 107.5),
            DungeonClass.TANK, new Vec3(61.5, 64.0, 117.5)
    );

    private static void spawn(Bot b, Vec3 at) {
        Mannequin m = new Mannequin(EntityType.MANNEQUIN, Sim.level());
        m.setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(profile(b)));
        m.setCustomName(Component.literal("§a" + b.name));
        m.setCustomNameVisible(true);
        m.setInvulnerable(true);
        m.setNoGravity(true);
        dress(m, b.clazz);
        hideDescription(m);
        b.pos = at;
        m.snapTo(at.x, at.y, at.z, 0f, 0f);
        b.entity = Sim.spawn(m);
        b.heldKey = "";
        setHeld(b, Fight.phase instanceof GoldorPhase || Fight.phase instanceof StormEnd ? "breaker" : restingItem(b.clazz));
    }

    private static void dress(Mannequin m, DungeonClass c) {
        m.setItemSlot(EquipmentSlot.HEAD, c == DungeonClass.BERSERK ? Masks.BONZO_MASK() : Masks.SPIRIT_MASK());
        int chest, legs, boots;
        switch (c) {
            case HEALER -> { chest = 0x1793c4; legs = 0x17a8c4; boots = 0x8969c8; }
            case MAGE -> { chest = 0x1793c4; legs = 0x17a8c4; boots = 0x1cd4e4; }
            case TANK -> { chest = -1; legs = 0x5d2fb9; boots = 0x8969c8; }
            case ARCHER -> { chest = 0xe7413c; legs = 0xe75c3c; boots = 0x8969c8; }
            default -> { chest = 0x3e05af; legs = 0x5d23d1; boots = 0x7c44ec; }
        }
        m.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, chest));
        m.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, legs));
        m.setItemSlot(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, boots));
    }

    private static ItemStack dyed(Item item, int rgb) {
        if (rgb < 0) return new ItemStack(Items.CHAINMAIL_CHESTPLATE);
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
        return s;
    }

    public static GameProfile profile(Bot b) {
        return new GameProfile(UUID.nameUUIDFromBytes(("p3sim:" + b.name).getBytes(StandardCharsets.UTF_8)), b.name);
    }

    private static Method hideMethod;

    private static synchronized Method hideMethod() throws NoSuchMethodException {
        if (hideMethod == null) {
            Method mm = Mannequin.class.getDeclaredMethod("setHideDescription", boolean.class);
            mm.setAccessible(true);
            hideMethod = mm;
        }
        return hideMethod;
    }

    private static void hideDescription(Mannequin m) {
        try {
            hideMethod().invoke(m, true);
        } catch (Throwable ignored) {
        }
    }

    private static void place(Bot b) {
        Mannequin e = b.entity;
        if (e == null) return;
        e.snapTo(b.pos.x, b.pos.y, b.pos.z, b.yaw, b.pitch);
        e.yHeadRot = b.yaw;
        e.yBodyRot = b.yaw;
    }

    private static Vec3 startPos(int from) {
        return switch (from) {
            case 1 -> stand("S1 SS").add(KRandom.DEFAULT.nextDouble(-1.5, 0.0), 0.0, KRandom.DEFAULT.nextDouble(-1.5, 1.5));
            case 2 -> GoldorPhase.GATE_CENTRES[1].add(KRandom.DEFAULT.nextDouble(-2.0, 2.0), -3.0, 3.0);
            case 3 -> GoldorPhase.GATE_CENTRES[2].add(-3.0, -3.0, KRandom.DEFAULT.nextDouble(-2.0, 2.0));
            default -> STRIP.add(KRandom.DEFAULT.nextDouble(-4.0, 4.0), 0.0, 0.0);
        };
    }

    // ------------------------------------------------------------------ places

    public static final Map<String, Vec3> STANDS;
    static {
        LinkedHashMap<String, Vec3> m = new LinkedHashMap<>();
        m.put("S1 T1", new Vec3(110.56, 113.0, 75.07)); m.put("S1 T2", new Vec3(108.91, 119.0, 79.53)); m.put("S1 T3", new Vec3(92.45, 112.0, 92.74)); m.put("S1 T4", new Vec3(92.99, 122.0, 100.87));
        m.put("S1 east lever", new Vec3(106.74, 122.0, 111.7)); m.put("S1 west lever", new Vec3(94.46, 122.0, 111.43)); m.put("S1 SS", new Vec3(108.3, 120.0, 94.0));
        m.put("S2 T1", new Vec3(69.69, 109.0, 122.4)); m.put("S2 T2", new Vec3(59.57, 120.0, 125.24)); m.put("S2 T3", new Vec3(45.86, 109.0, 122.43)); m.put("S2 T4", new Vec3(39.5, 109.0, 140.53)); m.put("S2 T5", new Vec3(39.69, 124.0, 125.53));
        m.put("S2 low lever", new Vec3(27.33, 124.0, 127.68)); m.put("S2 high lever", new Vec3(23.3, 132.0, 138.47)); m.put("S2 Lights", new Vec3(60.6, 132.0, 139.0));
        m.put("S3 T1", new Vec3(0.89, 109.0, 112.44)); m.put("S3 T2", new Vec3(1.0, 119.0, 93.6)); m.put("S3 T3", new Vec3(18.7, 121.5, 91.3)); m.put("S3 T4", new Vec3(0.45, 109.0, 78.24));
        m.put("S3 west lever", new Vec3(2.44, 122.0, 55.68)); m.put("S3 east lever", new Vec3(14.67, 122.0, 55.44)); m.put("S3 Arrows", new Vec3(1.40, 120.0, 77.39));
        m.put("S4 T1", new Vec3(41.12, 109.0, 32.96)); m.put("S4 T2", new Vec3(43.96, 121.0, 31.14)); m.put("S4 T3", new Vec3(66.85, 109.0, 32.04)); m.put("S4 T4", new Vec3(72.78, 115.0, 45.45));
        m.put("S4 low lever", new Vec3(83.48, 120.0625, 35.81)); m.put("S4 high lever", new Vec3(85.15, 126.0, 44.7)); m.put("S4 Target", new Vec3(63.5, 127.0, 35.5));
        STANDS = Collections.unmodifiableMap(m);
    }

    private static Vec3 stand(String job) {
        Vec3 v = STANDS.get(job);
        if (v == null) throw new java.util.NoSuchElementException("Key " + job + " is missing in the map.");
        return v;
    }

    public static final Vec3[] GATES = {Vec3.ZERO, new Vec3(95.8, 123.9, 121.0), new Vec3(19.3, 123.6, 127.9), new Vec3(12.4, 116.8, 52.7)};
    public static final Vec3 STRIP = new Vec3(54.6, 115.0, 51.5);
    public static final Vec3 CORE_SPOT = new Vec3(54.5, 115.0, 58.0);

    private static Vec3 CORE_EE() {
        Ee e = eeOf(5);
        return e != null ? e.spot : STRIP;
    }
}
