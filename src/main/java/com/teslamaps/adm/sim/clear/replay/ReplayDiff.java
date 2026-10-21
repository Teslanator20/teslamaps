// Ported from ADM's p3sim/clear/replay/ReplayDiff.kt
package com.teslamaps.adm.sim.clear.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.clear.puzzles.hard.Frames;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.run.CheckMark;
import com.teslamaps.adm.sim.run.KeyDropped;
import com.teslamaps.adm.sim.run.KeyPicked;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunBlocks;
import com.teslamaps.adm.sim.run.RunEvent;
import com.teslamaps.adm.sim.util.Pair;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What a replay found ADS doing unlike the recording, written to `replays/<id>.report.txt` when it ends. Server thread, but for adsChat
 * (the client thread).
 */
public class ReplayDiff {
    private final ReplaySession s;
    private final List<String> lines = new ArrayList<>();
    private final List<String> forced = new ArrayList<>();

    public ReplayDiff(ReplaySession s) { this.s = s; }

    private static String f(String fmt, Object... args) { return String.format(Locale.ROOT, fmt, args); }

    private static String take(String t, int n) { return t.length() <= n ? t : t.substring(0, n); }

    private final List<String> actions = new ArrayList<>();

    public void action(int k, String text) { actions.add(f("%6d  %s", k, text)); }

    private final List<String> kicks = new ArrayList<>();

    public void kick(int k, String reason) {
        kicks.add(f("%6d  %s", k, reason));
        note(k, "kick", "the server would have dropped you: " + reason);
    }

    private String endWhy = "still running";
    private int endK = 0;
    private boolean complete = false;
    private Integer bossK = null;
    private int lastK = 0;

    public void ended(String why, int k, Integer boss, int last, boolean whole) { endWhy = why; endK = k; bossK = boss; lastK = last; complete = whole; }

    public void note(int k, String kind, String text) { lines.add(f("%6d  %-7s %s", k, kind, text)); }

    public void forced(int k, String what, String result) { forced.add(f("%6d  %s -> %s", k, what, result != null ? result : "?")); }

    // ---- mobs

    private int mobsOk = 0;
    private final List<String> mobsMissing = new ArrayList<>();
    private final List<String> mobsNearYou = new ArrayList<>();

    public void mobOk() { mobsOk++; }

    /** Recorded WITHER-door HP jumps: ADS had the same max HP (within 1%) two ticks later, or not. */
    private int hpScaleOk = 0;
    private final List<String> hpScaleOff = new ArrayList<>();

    public void hpScale(int k, String label, double rec, Double ads) {
        if (ads != null && Math.abs(ads - rec) <= rec * 0.01) hpScaleOk++;
        else hpScaleOff.add(f("%6d  %s: Hypixel %,.0f, ADS %s", k, label, rec, ads != null ? f("%,.0f", ads) : "no mob"));
    }

    /** Camp mob spawns, in ticks after each side's own BLOOD door: Hypixel's (from the recording) and ADS's. */
    public List<Integer> campHypixel = List.of();
    public final List<Integer> campAds = new ArrayList<>();

    public void mobNotFound(int k, String label, double x, double y, double z, double you) {
        mobsMissing.add(f("%6d  %s at %s (you %.0f blocks away)", k, label, ReplaySession.fmt(x, y, z), you));
    }

    /** A recorded death ADS has not done yet; you: how far you were. */
    public void mobPending(int k, String label, double you, String ads) {
        if (you < 10) mobsNearYou.add(f("%6d  %s (you %.0f blocks away): %s", k, label, you, ads));
    }

    // ---- moves and teleports

    private int movesOff = 0;
    private int moves = 0;
    private final List<String> moveExamples = new ArrayList<>();

    /** After a recorded move was handed in: where ADS has you against where the move put you. */
    public void moved(int k, double x, double y, double z, ServerPlayer p) {
        moves++;
        double d = Math.sqrt((p.getX() - x) * (p.getX() - x) + (p.getY() - y) * (p.getY() - y) + (p.getZ() - z) * (p.getZ() - z));
        if (d > 0.3) {
            movesOff++;
            if (moveExamples.size() < 40 && (moveExamples.isEmpty() || !moveExamples.get(moveExamples.size() - 1).startsWith(f("%6d", k - 1))))
                moveExamples.add(f("%6d  sent %s, ADS has %s (%.2f off)", k, ReplaySession.fmt(x, y, z), ReplaySession.fmt(p.getX(), p.getY(), p.getZ()), d));
        }
    }

    private final List<Pair<Integer, Vec3>> hypTps = new ArrayList<>();
    private final List<Pair<Integer, Vec3>> adsTps = new ArrayList<>();

    /** Hypixel putting you where you already were: a resync, not a teleport to match. */
    private int resyncs = 0;
    private final JsonArray setbacks = new JsonArray();

    private PlacedRoom roomOwning(int bx, int bz) {
        for (PlacedRoom r : s.run.plan.rooms) if (r.placement != null && r.placement.ownsColumn(bx, bz)) return r;
        return null;
    }

    /** [design, x, y, z] of a world point in its room's frame. */
    private JsonArray canonPoint(Vec3 v) {
        int bx = (int) Math.floor(v.x);
        int bz = (int) Math.floor(v.z);
        PlacedRoom room = roomOwning(bx, bz);
        if (room == null) return null;
        Placement pl = room.placement;
        if (pl == null) return null;
        Pair<Double, Double> c = Frames.toCanonical(pl, v.x, v.z);
        if (c == null) return null;
        JsonArray a = new JsonArray();
        a.add(room.design != null ? room.design.id : room.name);
        a.add(Math.round(c.first * 100) / 100.0);
        a.add(Math.round(v.y * 100) / 100.0);
        a.add(Math.round(c.second * 100) / 100.0);
        return a;
    }

    public void recordedTeleport(int k, JsonArray e, Vec3 was) {
        JsonElement ae = e.get(2);
        if (ae == null || !ae.isJsonArray()) return;
        JsonArray a = ae.getAsJsonArray();
        Vec3 to = new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
        if (was != null && was.distanceTo(to) < 0.5) { resyncs++; return; }
        // Before the start Hypixel holds the party in the entrance by putting them back.
        if (k < 0) {
            if (was != null && to.y < 200 && to.y > 0) {
                JsonArray ca = canonPoint(to);
                if (ca != null) {
                    JsonArray cb = canonPoint(was);
                    if (cb != null) {
                        JsonArray row = new JsonArray();
                        row.add(k);
                        for (JsonElement x : ca) row.add(x);
                        for (JsonElement x : cb) row.add(x);
                        setbacks.add(row);
                    }
                }
            }
            return;
        }
        hypTps.add(Pair.of(k, to));
        if (was != null) {
            List<String> parts = new ArrayList<>();
            String way = blockedWay(was, to);
            if (!way.isEmpty()) parts.add("ADS has in the way: " + way);
            JsonArray cp = canonPoint(to);
            if (cp != null) parts.add("room frame " + cp);
            parts.add("past it ADS has " + pastStop(was, to));
            tpWay.put(hypTps.size() - 1, String.join("; ", parts));
        }
    }

    /** For each of Hypixel's teleports (by index): ADS's blocks with a collision on the straight way it moved you. */
    private final HashMap<Integer, String> tpWay = new HashMap<>();

    private static String cellText(BlockPos it) {
        String d = Sim.level().getBlockState(it).getBlock().getDescriptionId();
        return it.getX() + " " + it.getY() + " " + it.getZ() + " " + d.substring(d.lastIndexOf('.') + 1);
    }

    /** What ADS has a step past where Hypixel stopped you (b, going from a). */
    private String pastStop(Vec3 a, Vec3 b) {
        Vec3 raw = new Vec3(b.x - a.x, 0.0, b.z - a.z);
        if (!(raw.length() > 1e-3)) return "-";
        Vec3 d = raw.normalize();
        LinkedHashSet<BlockPos> seen = new LinkedHashSet<>();
        for (double fr : new double[]{0.5, 1.0}) for (double dy : new double[]{0.1, 1.2, 1.62}) seen.add(BlockPos.containing(b.x + d.x * fr, b.y + dy, b.z + d.z * fr));
        List<String> out = new ArrayList<>();
        for (BlockPos p : seen) out.add(cellText(p));
        return String.join(", ", out);
    }

    /** The cells on the line from a to b (each quarter block, feet and head height) that have a collision in ADS. */
    private String blockedWay(Vec3 a, Vec3 b) {
        LinkedHashSet<BlockPos> seen = new LinkedHashSet<>();
        int n = Math.clamp((int) (a.distanceTo(b) * 4), 1, 400);
        for (int i = 1; i <= n; i++) {
            Vec3 p = a.lerp(b, i / (double) n);
            for (double dy : new double[]{0.1, 1.2}) {
                BlockPos pos = BlockPos.containing(p.x, p.y + dy, p.z);
                if (!Sim.level().getBlockState(pos).getCollisionShape(Sim.level(), pos).isEmpty()) seen.add(pos);
            }
        }
        List<String> out = new ArrayList<>();
        for (BlockPos p : seen) out.add(cellText(p));
        return String.join(", ", out);
    }

    public void adsTeleport(int k, Vec3 to) { adsTps.add(Pair.of(k, to)); }

    private static String prim(JsonObject b, String k) {
        JsonElement e = b.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    public void missingTarget(int k, String what, JsonObject b) {
        JsonElement type = b.get("type");
        String tag = prim(b, "tag");
        if (tag == null) tag = prim(b, "name");
        if (tag == null) tag = "";
        note(k, "target", what + " on " + (type != null ? type.getAsString() : null) + " \"" + tag + "\" at " + b.get("x") + " " + b.get("y") + " " + b.get("z") + ": ADS has nothing there");
    }

    // ---- keys

    private final List<String> keyLines = new ArrayList<>();

    public void recordedKeyStand(int k, String kind, double x, double y, double z) { keyLines.add(f("%6d  Hypixel  %s appeared at %s", k, kind, ReplaySession.fmt(x, y, z))); }

    public void recordedKeyPick(int k, String kind, String by) { keyLines.add(f("%6d  Hypixel  %s taken by %s", k, kind, by)); }

    private static String cap(String n) {
        String l = n.toLowerCase(Locale.ROOT);
        return l.isEmpty() ? l : Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }

    public void adsEvent(RunEvent e) {
        if (e instanceof KeyDropped kd) {
            List<PlacedRoom> rooms = s.run.plan.rooms;
            Object room = kd.room >= 0 && kd.room < rooms.size() ? rooms.get(kd.room).name : (Object) kd.room;
            keyLines.add(f("%6d  ADS      %s Key dropped at %s (room %s)", kd.t, cap(kd.kind.name()), ReplaySession.fmt(kd.x, kd.y, kd.z), room));
        } else if (e instanceof KeyPicked kp) {
            PlayerRecord r = s.run.state.players.get(kp.player);
            keyLines.add(f("%6d  ADS      %s Key taken by %s", kp.t, cap(kp.kind.name()), r != null ? r.name : "?"));
        }
    }

    // ---- blocks: what Hypixel changed a block to, checked in ADS a moment later

    private static final class BlockCheck {
        final int due;
        final int k;
        final BlockPos pos;
        final String want;
        final double you;
        final double mate;

        BlockCheck(int due, int k, BlockPos pos, String want, double you, double mate) {
            this.due = due;
            this.k = k;
            this.pos = pos;
            this.want = want;
            this.you = you;
            this.mate = mate;
        }
    }

    private final ArrayDeque<BlockCheck> blockChecks = new ArrayDeque<>();
    /** Block differences by where they came from: Hypixel's start-of-run updates (T0), near you (yours), away from you. */
    private final LinkedHashMap<String, HashMap<String, List<String>>> blockMiss = new LinkedHashMap<>();

    {
        blockMiss.put("start", new HashMap<>());
        blockMiss.put("you", new HashMap<>());
        blockMiss.put("away", new HashMap<>());
    }

    private int blocksChecked = 0;
    private int blocksUnloaded = 0;
    private int blocksForced = 0;

    /** A block Hypixel changed; you and mate: how far you and the nearest teammate were from it. */
    public void recordedBlock(int k, int x, int y, int z, String state, double you, double mate) {
        if (state == null) return;
        BlockPos pos = new BlockPos(x, y, z);
        blockHistory.computeIfAbsent(pos, p -> new ArrayList<>()).add(Pair.of(k, state));
        blockChecks.addLast(new BlockCheck(k + BLOCK_LAG, k, pos, state, you, mate));
    }

    /** Every recorded state of each changed cell, in order. */
    private final HashMap<BlockPos, List<Pair<Integer, String>>> blockHistory = new HashMap<>();

    /** The states Hypixel had at pos from `from` to `to`: the one standing at from and every later change up to to. */
    private List<String> hypStates(BlockPos pos, int from, int to) {
        List<Pair<Integer, String>> h = blockHistory.get(pos);
        if (h == null) return List.of();
        List<String> out = new ArrayList<>();
        Pair<Integer, String> standing = null;
        for (Pair<Integer, String> p : h) if (p.first <= from) standing = p;
        if (standing != null) out.add(standing.second);
        for (Pair<Integer, String> p : h) if (p.first >= from + 1 && p.first <= to) out.add(p.second);
        return out;
    }

    // ---- chat

    private final List<Pair<Integer, String>> recChat = new ArrayList<>();
    private final ConcurrentLinkedQueue<Pair<Integer, String>> adsChatQ = new ConcurrentLinkedQueue<>();
    public volatile int nowK = 0;

    public void recordedChat(int k, String t) { if (!ReplayFileKt.isBlank(t)) recChat.add(Pair.of(k, t)); }

    /** A chat line the game got from ADS (client thread). */
    public void adsChat(String t) {
        String c = ReplaySession.clean(t);
        if (!ReplayFileKt.isBlank(c)) adsChatQ.add(Pair.of(nowK, c));
    }

    // ---- rooms

    private final LinkedHashMap<Integer, Map<String, Integer>> recRooms = new LinkedHashMap<>();
    private final HashMap<Integer, String> roomNames = new HashMap<>();

    public void recordedRoom(int k, int room, String name, String mark) {
        roomNames.put(room, name);
        recRooms.computeIfAbsent(room, r -> new LinkedHashMap<>()).putIfAbsent(mark, k);
    }

    private JsonObject lastDung = null;

    public void recordedDung(JsonObject o) { if (o != null) lastDung = o; }

    private static String idOf(String state) {
        int i = state.indexOf('[');
        return i < 0 ? state : state.substring(0, i);
    }

    public void tick(int now) {
        nowK = now;
        while (!blockChecks.isEmpty() && blockChecks.peekFirst().due <= now) {
            BlockCheck c = blockChecks.removeFirst();
            if (!RunBlocks.isLoaded(c.pos.getX(), c.pos.getZ())) { blocksUnloaded++; continue; }
            blocksChecked++;
            BlockState haveState = Sim.level().getBlockState(c.pos);
            String have = str(haveState);
            // Hypixel's cell as it stood at the check, give or take BLOCK_TOLERANCE ticks.
            List<String> states = hypStates(c.pos, now - BLOCK_TOLERANCE, now);
            if (states.contains(have)) continue;
            String want = states.isEmpty() ? c.want : states.get(states.size() - 1);
            String wantId = idOf(want);
            String haveId = idOf(have);
            String key = wantId.equals(haveId) ? wantId + ": state differs" : "Hypixel " + wantId + ", ADS " + haveId;
            String cat = c.k <= 1 ? "start" : c.you <= NEAR ? "you" : "away";
            List<String> l = blockMiss.get(cat).computeIfAbsent(key, x -> new ArrayList<>());
            l.add(f("k " + c.k + " at " + c.pos.getX() + " " + c.pos.getY() + " " + c.pos.getZ() + ": Hypixel " + want + ", ADS " + have + " (you %.0f, teammate %.0f)", c.you, c.mate));
            JsonArray cell = canonCell(cat, c.k, c.pos, parse(want), haveState, c.you);
            if (cell != null) cells.add(cell);
            // The world is put as Hypixel had it, so what comes after is played on the same blocks.
            BlockState st = parse(want);
            if (st != null) { Sim.level().setBlock(c.pos, st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE); blocksForced++; }
        }
    }

    /** A block a teammate or the room changed away from you: counted when ADS did not have it already, then put in as it is, now. */
    public void awayBlock(int k, int x, int y, int z, String state, double you, double mate) {
        BlockState st = state != null ? parse(state) : null;
        if (st == null) return;
        BlockPos pos = new BlockPos(x, y, z);
        blockHistory.computeIfAbsent(pos, p -> new ArrayList<>()).add(Pair.of(k, state));
        if (!RunBlocks.isLoaded(x, z)) { blocksUnloaded++; return; }
        blocksChecked++;
        BlockState have = Sim.level().getBlockState(pos);
        if (str(have).equals(state)) return;
        String wantId = idOf(state);
        String haveId = idOf(str(have));
        String key = wantId.equals(haveId) ? wantId + ": state differs" : "Hypixel " + wantId + ", ADS " + haveId;
        blockMiss.get("away").computeIfAbsent(key, q -> new ArrayList<>())
                .add(f("k " + k + " at " + x + " " + y + " " + z + ": Hypixel " + state + ", ADS " + str(have) + " (you %.0f, teammate %.0f)", you, mate));
        JsonArray cell = canonCell("away", k, pos, st, have, you);
        if (cell != null) cells.add(cell);
        Sim.level().setBlock(pos, st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        blocksForced++;
    }

    /** Puts a recorded block in as it is, now. */
    public void setNow(int x, int y, int z, String state) {
        BlockState st = state != null ? parse(state) : null;
        if (st == null) return;
        Sim.level().setBlock(new BlockPos(x, y, z), st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    /** Every block difference in its room's own frame: [cat, k, design, x, y, z, Hypixel state, ADS state, you]. */
    private final JsonArray cells = new JsonArray();

    private JsonArray canonCell(String cat, int k, BlockPos pos, BlockState hyp, BlockState ads, double you) {
        PlacedRoom room = roomOwning(pos.getX(), pos.getZ());
        if (room == null) return null;
        Placement pl = room.placement;
        if (pl == null) return null;
        Pair<Integer, Integer> c = pl.toCanonical(pos.getX(), pos.getZ());
        if (c == null) return null;
        Rotation back = Rotation.values()[(4 - pl.quarterTurns() % 4) % 4];
        JsonArray a = new JsonArray();
        a.add(cat);
        a.add(k);
        a.add(room.design != null ? room.design.id : room.name);
        a.add(c.first);
        a.add(pos.getY());
        a.add(c.second);
        a.add(hyp != null ? str(hyp.rotate(back)) : "?");
        a.add(str(ads.rotate(back)));
        a.add((int) Math.round(you));
        return a;
    }

    private BlockState parse(String s) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s, false).blockState();
        } catch (Throwable t) {
            return null;
        }
    }

    private String str(BlockState b) {
        String id = BuiltInRegistries.BLOCK.getKey(b.getBlock()).toString();
        List<String> props = b.getValues().map(v -> v.property().getName() + "=" + v.valueName()).sorted().toList();
        if (props.isEmpty()) return id;
        return id + "[" + String.join(",", props) + "]";
    }

    // ---- the report

    private final List<String> chatMissing = new ArrayList<>();
    private final List<String> chatExtra = new ArrayList<>();
    private final List<String> chatNumbers = new ArrayList<>();

    private static String g(JsonObject d, String k) {
        JsonElement e = d.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "?";
    }

    private static boolean ignored(String t) {
        for (Pattern p : IGNORE_CHAT) if (p.matcher(t).find()) return true;
        return false;
    }

    private static int leadingInt(String line) {
        String t = line.trim();
        int sp = t.indexOf(' ');
        String head = sp < 0 ? t : t.substring(0, sp);
        try { return Integer.parseInt(head); } catch (NumberFormatException e) { return 0; }
    }

    public void write() {
        List<String> out = new ArrayList<>();
        Run run = s.run;
        out.add("ADS replay report: " + s.file.id + "  (written at run tick " + run.t() + ")");
        out.add("Ended: " + endWhy + " at k " + endK + " (" + (complete ? "the whole recording" : "CUT SHORT: the recording goes to k " + lastK + ", boss at " + (bossK != null ? bossK.toString() : "-")) + ")");
        if (!kicks.isEmpty()) { out.add("Kicks ADS's server would have made (the replay went on):"); out.addAll(kicks); }
        out.add("");
        out.add("== Score parts at the end: Hypixel (Odin) vs ADS");
        if (lastDung != null) {
            JsonObject d = lastDung;
            out.add("  secrets " + g(d, "secretsFound") + " / " + run.state.secrets.teamFound + "   crypts " + g(d, "crypts") + " / " + run.state.secrets.crypts
                    + "   deaths " + g(d, "deaths") + " / " + run.state.deaths.teamDeaths);
            out.add("  rooms opened " + g(d, "openedRooms") + " / " + run.state.counters.opened + "   completed " + g(d, "completedRooms") + " / " + run.state.counters.completed
                    + "   cleared " + g(d, "percentCleared") + "% / " + run.state.score.clearedPct + "%");
            out.add("  bat " + g(d, "bat") + " / " + run.state.secrets.batKilled + "   mimic " + g(d, "mimic") + " / " + run.state.secrets.mimicKilled
                    + "   prince " + g(d, "prince") + " / " + run.state.secrets.princeKilled);
        }
        out.add("");
        out.add("== Mob HP after a WITHER door: " + hpScaleOk + " as recorded, " + hpScaleOff.size() + " not");
        for (String it : hpScaleOff) out.add("  " + it);
        out.add("== Watcher camp spawns, ticks after the BLOOD door: Hypixel " + campHypixel.size() + ", ADS " + campAds.size());
        out.add("  Hypixel " + join(campHypixel, " "));
        out.add("  ADS     " + join(campAds, " "));
        out.add("== Mobs: " + mobsOk + " recorded deaths ADS had done itself");
        out.add("  Near you, not dead in ADS when the recording's died (" + mobsNearYou.size() + "; the kill was likely yours):");
        for (String it : mobsNearYou) out.add("  " + it);
        out.add("  Recorded deaths ADS has no such mob for (" + mobsMissing.size() + "):");
        for (String it : mobsMissing) out.add("  " + it);
        out.add("");
        out.add("== Keys");
        List<String> keys = new ArrayList<>(keyLines);
        keys.sort(Comparator.comparingInt(ReplayDiff::leadingInt));
        out.addAll(keys);
        out.add("");
        out.add("== Teleports of you: Hypixel " + hypTps.size() + ", ADS " + adsTps.size() + " (and " + resyncs + " of Hypixel's resyncs, to where you stood)");
        Set<Integer> used = new HashSet<>();
        List<Integer> delays = new ArrayList<>();
        for (Pair<Integer, Vec3> h : hypTps) {
            int k = h.first;
            Vec3 at = h.second;
            Integer i = null;
            for (int it = 0; it < adsTps.size(); it++) {
                if (!used.contains(it) && Math.abs(adsTps.get(it).first - k) <= 12 && adsTps.get(it).second.distanceTo(at) < 1.5) { i = it; break; }
            }
            if (i != null) { used.add(i); delays.add(k - adsTps.get(i).first); }
            else {
                int idx = -1;
                for (int q = 0; q < hypTps.size(); q++) if (hypTps.get(q).first == k && Objects.equals(hypTps.get(q).second, at)) { idx = q; break; }
                String way = tpWay.get(idx);
                out.add(f("%6d  Hypixel put you at %s; ADS did not", k, ReplaySession.fmt(at.x, at.y, at.z)) + (way != null ? "  (" + way + ")" : ""));
            }
        }
        for (int i = 0; i < adsTps.size(); i++) if (!used.contains(i)) {
            Pair<Integer, Vec3> p = adsTps.get(i);
            out.add(f("%6d  ADS put you at %s; Hypixel did not", p.first, ReplaySession.fmt(p.second.x, p.second.y, p.second.z)));
        }
        double avg = 0.0;
        if (!delays.isEmpty()) {
            long sum = 0;
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int d : delays) { sum += d; min = Math.min(min, d); max = Math.max(max, d); }
            avg = sum / (double) delays.size();
            out.add("  " + delays.size() + " matched; Hypixel's came " + f("%.1f", avg) + " ticks after ADS's on average (min " + min + ", max " + max + ")");
        }
        out.add("");
        out.add("== Your moves: " + moves + " handed in, " + movesOff + " ended away from where you sent (> 0.3 blocks)");
        out.addAll(moveExamples);
        out.add("");
        out.add("== Teammate and room work ADS had to be made to do (" + forced.size() + ")");
        out.addAll(forced);
        out.add("");
        out.add("== Rooms: recorded mark (k) vs ADS");
        for (Map.Entry<Integer, Map<String, Integer>> en : recRooms.entrySet()) {
            int room = en.getKey();
            RoomRecord r = run.state.room(room);
            if (r == null) continue;
            Map<String, Integer> ads = new HashMap<>();
            ads.put("NONE", r.tDiscovered);
            ads.put("WHITE", r.tWhite);
            ads.put("GREEN", r.tGreen);
            ads.put("RED", r.tRed);
            ads.put("FAILED", r.tRed);
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, Integer> mk : en.getValue().entrySet()) {
                if (mk.getKey().equals("UNDISCOVERED")) continue;
                Integer a = ads.get(mk.getKey());
                parts.add(mk.getKey() + " rec " + mk.getValue() + " / ADS " + (a != null ? a.toString() : "never"));
            }
            String rn = roomNames.get(room);
            if (!parts.isEmpty()) out.add("  " + (rn != null ? rn : Integer.toString(room)) + ": " + String.join("; ", parts) + "  (ADS now " + r.check + ")");
        }
        for (RoomRecord r : run.state.rooms)
            if (r.check == CheckMark.GREEN || r.check == CheckMark.WHITE) if (!recRooms.containsKey(r.index())) out.add("  " + r.plan.name + ": ADS " + r.check + ", never marked in the recording");
        out.add("");
        out.add("== Blocks: " + blocksChecked + " of Hypixel's block changes checked " + BLOCK_LAG + " ticks later against Hypixel's cell then (±" + BLOCK_TOLERANCE + ") ("
                + blocksUnloaded + " not loaded in ADS); " + blocksForced + " differed and were set as Hypixel had them");
        Map<String, String> catNames = Map.of("start", "Hypixel's start-of-run updates (k 0-1)", "you", "within " + NEAR + " blocks of you", "away", "away from you (teammates, or the room by itself)");
        for (Map.Entry<String, HashMap<String, List<String>>> en : blockMiss.entrySet()) {
            int total = 0;
            for (List<String> l : en.getValue().values()) total += l.size();
            out.add("  -- " + catNames.get(en.getKey()) + ": " + total);
            List<Map.Entry<String, List<String>>> sorted = new ArrayList<>(en.getValue().entrySet());
            sorted.sort(Comparator.<Map.Entry<String, List<String>>>comparingInt(e -> e.getValue().size()).reversed());
            for (Map.Entry<String, List<String>> e : sorted) {
                out.add("  " + e.getValue().size() + "x " + e.getKey());
                for (int i = 0; i < Math.min(3, e.getValue().size()); i++) out.add("      " + e.getValue().get(i));
            }
        }
        out.add("");
        List<Pair<Integer, String>> ads = new ArrayList<>(adsChatQ);
        out.add("== Chat Hypixel sent that ADS did not (within " + CHAT_WINDOW + " ticks)");
        Function<String, String> norm = t -> NUM.matcher(t).replaceAll("#");
        Map<String, List<Pair<Integer, String>>> adsBy = new LinkedHashMap<>();
        for (Pair<Integer, String> a : ads) adsBy.computeIfAbsent(norm.apply(a.second), q -> new ArrayList<>()).add(a);
        Map<String, List<Pair<Integer, String>>> recBy = new LinkedHashMap<>();
        for (Pair<Integer, String> a : recChat) recBy.computeIfAbsent(norm.apply(a.second), q -> new ArrayList<>()).add(a);
        for (Pair<Integer, String> rc : recChat) {
            int k = rc.first;
            String t = rc.second;
            if (ignored(t)) continue;
            boolean got = false;
            List<Pair<Integer, String>> l = adsBy.get(norm.apply(t));
            if (l != null) for (Pair<Integer, String> a : l) if (Math.abs(a.first - k) <= CHAT_WINDOW) { got = true; break; }
            if (!got) { out.add(f("%6d  %s", k, take(t, 160))); chatMissing.add(norm.apply(t)); }
        }
        out.add("");
        out.add("== The same line with other numbers (Hypixel | ADS)");
        for (Pair<Integer, String> rc : recChat) {
            int k = rc.first;
            String t = rc.second;
            if (ignored(t)) continue;
            List<Pair<Integer, String>> l = adsBy.get(norm.apply(t));
            if (l == null) continue;
            List<Pair<Integer, String>> same = new ArrayList<>();
            for (Pair<Integer, String> a : l) if (Math.abs(a.first - k) <= CHAT_WINDOW) same.add(a);
            boolean exact = false;
            for (Pair<Integer, String> a : same) if (a.second.equals(t)) { exact = true; break; }
            if (same.isEmpty() || exact) continue;
            Pair<Integer, String> a = same.get(0);
            for (Pair<Integer, String> q : same) if (Math.abs(q.first - k) < Math.abs(a.first - k)) a = q;
            out.add(f("%6d  %s | %+d  %s", k, take(t, 110), a.first - k, take(a.second, 110)));
            chatNumbers.add(t + " | " + a.second);
        }
        out.add("");
        out.add("== Chat ADS sent that Hypixel did not");
        for (Pair<Integer, String> ac : ads) {
            int k = ac.first;
            String t = ac.second;
            boolean got = false;
            List<Pair<Integer, String>> l = recBy.get(norm.apply(t));
            if (l != null) for (Pair<Integer, String> a : l) if (Math.abs(a.first - k) <= CHAT_WINDOW) { got = true; break; }
            if (!got && !t.startsWith("[ADS]")) { out.add(f("%6d  %s", k, take(t, 160))); chatExtra.add(norm.apply(t)); }
        }
        out.add("");
        out.add("== Notes");
        out.addAll(lines);
        out.add("");
        out.add("== Your clicks as ADS took them");
        out.addAll(actions);
        JsonObject j = new JsonObject();
        j.addProperty("end", endWhy);
        j.addProperty("endK", endK);
        if (bossK != null) j.addProperty("bossK", bossK);
        j.addProperty("lastK", lastK);
        j.addProperty("complete", complete);
        j.add("cells", cells);
        j.add("setbacks", setbacks);
        j.addProperty("resyncs", resyncs);
        JsonArray designs = new JsonArray();
        LinkedHashSet<String> ds = new LinkedHashSet<>();
        for (PlacedRoom r : s.run.plan.rooms) if (r.design != null && r.design.id != null) ds.add(r.design.id);
        for (String d : ds) designs.add(d);
        j.add("designs", designs);
        JsonArray kk = new JsonArray();
        for (String it : kicks) kk.add(it);
        j.add("kicks", kk);
        j.addProperty("id", s.file.id);
        j.addProperty("cls", s.file.self.cls);
        j.addProperty("t", run.t());
        if (lastDung != null) j.add("hypixel", lastDung);
        JsonObject adsJ = new JsonObject();
        adsJ.addProperty("secretsFound", run.state.secrets.teamFound);
        adsJ.addProperty("crypts", run.state.secrets.crypts);
        adsJ.addProperty("deaths", run.state.deaths.teamDeaths);
        adsJ.addProperty("openedRooms", run.state.counters.opened);
        adsJ.addProperty("completedRooms", run.state.counters.completed);
        adsJ.addProperty("percentCleared", run.state.score.clearedPct);
        j.add("ads", adsJ);
        j.addProperty("hpScaleOk", hpScaleOk);
        j.addProperty("hpScaleOff", hpScaleOff.size());
        j.addProperty("mobsOk", mobsOk);
        j.addProperty("mobsNearYou", mobsNearYou.size());
        j.addProperty("mobsMissing", mobsMissing.size());
        j.addProperty("tpHypixel", hypTps.size());
        j.addProperty("tpAds", adsTps.size());
        j.addProperty("tpMatched", delays.size());
        j.addProperty("tpDelay", delays.isEmpty() ? 0.0 : avg);
        j.addProperty("forced", forced.size());
        JsonArray ch = new JsonArray();
        for (int it : campHypixel) ch.add(it);
        j.add("campHypixel", ch);
        JsonArray ca = new JsonArray();
        for (int it : campAds) ca.add(it);
        j.add("campAds", ca);
        JsonObject blocks = new JsonObject();
        for (Map.Entry<String, HashMap<String, List<String>>> en : blockMiss.entrySet()) {
            JsonObject o = new JsonObject();
            for (Map.Entry<String, List<String>> e : en.getValue().entrySet()) o.addProperty(e.getKey(), e.getValue().size());
            blocks.add(en.getKey(), o);
        }
        j.add("blocks", blocks);
        JsonArray cm = new JsonArray();
        for (String it : chatMissing) cm.add(it);
        j.add("chatMissing", cm);
        JsonArray ce = new JsonArray();
        for (String it : chatExtra) ce.add(it);
        j.add("chatExtra", ce);
        JsonArray cn = new JsonArray();
        for (String it : chatNumbers) cn.add(it);
        j.add("chatNumbers", cn);
        try {
            Files.createDirectories(ReplayFile.dir());
            Files.writeString(ReplayFile.dir().resolve(s.file.id + ".report.json"), j.toString());
            List<String> blockLines = new ArrayList<>();
            for (Map.Entry<String, HashMap<String, List<String>>> en : blockMiss.entrySet())
                for (Map.Entry<String, List<String>> e : en.getValue().entrySet())
                    for (String l : e.getValue()) blockLines.add(en.getKey() + " | " + e.getKey() + " | " + l);
            Files.writeString(ReplayFile.dir().resolve(s.file.id + ".blocks.txt"), String.join("\n", blockLines));
            Files.write(ReplayFile.dir().resolve(s.file.id + ".report.txt"), out);
            Sim.note("Replay report: config/teslamaps/adm/ads/replays/" + s.file.id + ".report.txt");
        } catch (Throwable t) {
            Sim.note("Replay report could not be written: " + t);
        }
    }

    private static String join(List<Integer> l, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) { if (i > 0) sb.append(sep); sb.append(l.get(i)); }
        return sb.toString();
    }

    public static final int BLOCK_LAG = 10;
    public static final double NEAR = 12.0;
    public static final int BLOCK_TOLERANCE = 3;
    public static final int CHAT_WINDOW = 40;
    private static final Pattern NUM = Pattern.compile("[0-9][0-9,.]*");
    /** Lines that are about the recording's account, Hypixel's server or chat, not the run. */
    private static final List<Pattern> IGNORE_CHAT = List.of(Pattern.compile("^Party >"), Pattern.compile("^Guild >"), Pattern.compile("^From "), Pattern.compile("^To "),
            Pattern.compile("profile"), Pattern.compile("Profile ID"), Pattern.compile("FIRE SALE"), Pattern.compile("^\\[Sacks]"), Pattern.compile("Autopet"),
            Pattern.compile("RARE DROP"), Pattern.compile("^Co-op >"));
}
