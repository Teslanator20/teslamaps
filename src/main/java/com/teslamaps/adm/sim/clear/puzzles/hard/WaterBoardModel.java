package com.teslamaps.adm.sim.clear.puzzles.hard;

// Ported from ADM's p3sim/clear/puzzles/hard/WaterBoardModel.kt

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.adm.sim.pack.rooms.RoomVolume;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Water Board as a state machine: water runs down channels in the sheet z = SHEET_Z, piston gates moved by levers, five drains over
 * five wool pits of which three are closed. A closed pit opens when water reaches its drain; solved when all three are open.
 * tick() is one server tick; a lever click moves gates and intake at once, its edits come out of the next tick().
 */
public class WaterBoardModel {
    private final WaterSim sim;
    public final List<Gate> gates;
    private final Map<WaterLever, Boolean> leverOn;
    private final Map<WaterLever, String> leverBase;
    public final Set<Integer> closedPits;

    /** Pits that water has opened. */
    public final Set<Integer> openedPits = new HashSet<>();

    /** Whether the water lever has been pulled. */
    public boolean waterOn = false;

    /** Set once every pit that was closed has opened. */
    public boolean solved = false;

    /** Ticks run since the model was built. */
    public int now = 0;

    private static class Due {
        final int at;
        final int kind;
        final int arg;

        Due(int at, int kind, int arg) { this.at = at; this.kind = kind; this.arg = arg; }
    }

    private final ArrayList<Due> pending = new ArrayList<>();
    private final ArrayList<BlockEdit> edits = new ArrayList<>();

    private WaterBoardModel(WaterSim sim, List<Gate> gates, Map<WaterLever, Boolean> leverOn, Map<WaterLever, String> leverBase, Set<Integer> closedPits) {
        this.sim = sim;
        this.gates = gates;
        this.leverOn = leverOn;
        this.leverBase = leverBase;
        this.closedPits = closedPits;
    }

    public boolean isLeverOn(WaterLever l) { return Boolean.TRUE.equals(leverOn.get(l)); }

    /** The edit that flips a lever's own block; its effect follows in tick(). */
    public BlockEdit click(WaterLever lever) {
        boolean on = !Boolean.TRUE.equals(leverOn.get(lever));
        leverOn.put(lever, on);
        if (lever == WaterLever.WATER) setWater(on);
        else for (Gate g : gates) if (g.lever == lever) toggle(g);
        return leverEdit(lever);
    }

    /** The lever's block in its current position. */
    public BlockEdit leverEdit(WaterLever lever) {
        String base = leverBase.get(lever);
        if (base == null) throw new java.util.NoSuchElementException("Key " + lever + " is missing in the map.");
        return new BlockEdit(lever.x, lever.y, lever.z, withPowered(base, isLeverOn(lever)));
    }

    /** Every block the board has changed from the room as built, in its current state. */
    public List<BlockEdit> fullEdits() {
        ArrayList<BlockEdit> out = new ArrayList<>();
        for (Gate g : gates) out.addAll(gateEdits(g));
        for (WaterLever l : WaterLever.values()) out.add(leverEdit(l));
        for (int pit = 0; pit <= 4; pit++) out.addAll(pitEdits(pit, closedPits.contains(pit) && !openedPits.contains(pit)));
        int[] cells = sim.snapshot().first;
        int k = 0;
        while (k + 3 < cells.length) {
            int x = cells[k]; int y = cells[k + 1]; int z = cells[k + 2];
            if (z == SHEET_Z && y >= 56) out.add(new BlockEdit(x, y, z, "minecraft:water[level=" + cells[k + 3] + "]"));
            k += 4;
        }
        return out;
    }

    /** Runs one tick; returns the block edits it caused (water, gates, pits), canonical. */
    public List<BlockEdit> tick() {
        now++;
        sim.advance();
        int i = 0;
        while (i < pending.size()) {
            Due p = pending.get(i);
            if (p.at <= now) {
                pending.remove(i);
                run(p);
            } else i++;
        }
        for (WaterSim.Change c : sim.runDue()) {
            edits.add(new BlockEdit(c.x, c.y, c.z, c.level < 0 ? AIR : "minecraft:water[level=" + c.level + "]"));
            if (c.level >= 0 && c.y == DRAIN_Y && c.z == SHEET_Z) {
                int pit = indexOf(DRAIN_X, c.x);
                if (pit >= 0 && closedPits.contains(pit) && openedPits.add(pit)) {
                    pending.add(new Due(now, K_PIT, pit));
                    pending.sort(Comparator.comparingInt(d -> d.at));
                }
            }
        }
        int j = 0;
        while (j < pending.size()) {
            Due p = pending.get(j);
            if (p.at <= now) { pending.remove(j); run(p); } else j++;
        }
        ArrayList<BlockEdit> out = new ArrayList<>(edits);
        edits.clear();
        return out;
    }

    private static int indexOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return -1;
    }

    private void run(Due p) {
        switch (p.kind) {
            case K_WATER -> setWater(p.arg != 0);
            case K_GATES -> pending.add(new Due(now + GATE_DELAY_TICKS, K_APPLY, p.arg));
            case K_APPLY -> {
                WaterLever lever = WaterLever.values()[p.arg];
                for (Gate g : gates) if (g.lever == lever) toggle(g);
            }
            case K_PIT -> {
                edits.addAll(pitEdits(p.arg, false));
                if (openedPits.containsAll(closedPits) && !solved) solved = true;
            }
            default -> {}
        }
    }

    /** The water lever: on takes the plug out and sets the intake source; pulled again, the plug goes back. */
    private void setWater(boolean on) {
        if (on == waterOn) return;
        waterOn = on;
        if (on) { sim.setSolid(INTAKE_X, INTAKE_Y, SHEET_Z, false); sim.placeSource(INTAKE_X, INTAKE_Y, SHEET_Z); }
        else { sim.setSolid(INTAKE_X, INTAKE_Y, SHEET_Z, true); edits.add(new BlockEdit(INTAKE_X, INTAKE_Y, SHEET_Z, PLUG)); }
    }

    private void toggle(Gate g) {
        g.extended = !g.extended;
        String block = g.lever.gateBlock;
        if (block == null) throw new NullPointerException();
        if (g.extended) {
            sim.setSolid(g.x, g.y, SHEET_Z, true);
            edits.add(new BlockEdit(g.x, g.y, PISTON_Z, "minecraft:sticky_piston[extended=true,facing=north]"));
            edits.add(new BlockEdit(g.x, g.y, HEAD_Z, HEAD));
            edits.add(new BlockEdit(g.x, g.y, SHEET_Z, block));
        } else {
            edits.add(new BlockEdit(g.x, g.y, SHEET_Z, AIR));
            sim.setSolid(g.x, g.y, SHEET_Z, false);
            edits.add(new BlockEdit(g.x, g.y, HEAD_Z, block));
            edits.add(new BlockEdit(g.x, g.y, PISTON_Z, "minecraft:sticky_piston[extended=false,facing=north]"));
        }
    }

    private List<Integer> sortedClosed() {
        List<Integer> l = new ArrayList<>(closedPits);
        l.sort(null);
        return l;
    }

    /** The pits that start closed, as edits. */
    public List<BlockEdit> startEdits() {
        List<BlockEdit> out = new ArrayList<>();
        for (int k : sortedClosed()) out.addAll(pitEdits(k, true));
        return out;
    }

    /** Everything a savepoint needs. */
    public JsonObject snapshot() {
        JsonObject o = new JsonObject();
        o.addProperty("now", now);
        o.addProperty("waterOn", waterOn);
        o.addProperty("solved", solved);
        JsonArray closed = new JsonArray();
        for (int k : sortedClosed()) closed.add(k);
        o.add("closed", closed);
        JsonArray opened = new JsonArray();
        List<Integer> op = new ArrayList<>(openedPits);
        op.sort(null);
        for (int k : op) opened.add(k);
        o.add("opened", opened);
        JsonArray levers = new JsonArray();
        for (WaterLever l : WaterLever.values()) if (isLeverOn(l)) levers.add(l.name());
        o.add("levers", levers);
        JsonArray ga = new JsonArray();
        for (Gate g : gates) ga.add(g.extended ? 1 : 0);
        o.add("gates", ga);
        Pair<int[], int[]> s = sim.snapshot();
        JsonArray water = new JsonArray();
        for (int v : s.first) water.add(v);
        o.add("water", water);
        JsonArray updates = new JsonArray();
        for (int v : s.second) updates.add(v);
        o.add("updates", updates);
        JsonArray due = new JsonArray();
        for (Due p : pending) due.add((p.at - now) + "," + p.kind + "," + p.arg);
        o.add("due", due);
        return o;
    }

    private static Integer toIntOrNull(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static int[] ints(JsonArray a) {
        if (a == null) return new int[0];
        int[] out = new int[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsInt();
        return out;
    }

    /** Puts the model back to a snapshot; returns the edits that make the room's blocks match. */
    public List<BlockEdit> restore(JsonObject o) {
        JsonElement e;
        now = (e = o.get("now")) != null ? e.getAsInt() : 0;
        waterOn = (e = o.get("waterOn")) != null && e.getAsBoolean();
        solved = (e = o.get("solved")) != null && e.getAsBoolean();
        closedPits.clear();
        JsonArray ca = o.getAsJsonArray("closed");
        if (ca != null) for (JsonElement x : ca) closedPits.add(x.getAsInt());
        openedPits.clear();
        JsonArray oa = o.getAsJsonArray("opened");
        if (oa != null) for (JsonElement x : oa) openedPits.add(x.getAsInt());
        leverOn.clear();
        JsonArray la = o.getAsJsonArray("levers");
        if (la != null) for (JsonElement x : la) {
            WaterLever l;
            try { l = WaterLever.valueOf(x.getAsString()); } catch (Throwable t) { l = null; }
            if (l != null) leverOn.put(l, true);
        }
        ArrayList<BlockEdit> out = new ArrayList<>();
        JsonArray ge = o.getAsJsonArray("gates");
        for (int i = 0; i < gates.size(); i++) {
            Gate g = gates.get(i);
            boolean want = ge != null && ge.get(i).getAsInt() == 1;
            if (want != g.extended) { g.extended = want; out.addAll(gateEdits(g)); }
            sim.setSolid(g.x, g.y, SHEET_Z, g.extended);
        }
        sim.restore(ints(o.getAsJsonArray("water")), ints(o.getAsJsonArray("updates")));
        pending.clear();
        JsonArray da = o.getAsJsonArray("due");
        if (da != null) for (JsonElement s : da) {
            String[] f = s.getAsString().split(",", -1);
            if (f.length == 3) {
                Integer a = toIntOrNull(f[0]), b = toIntOrNull(f[1]), c = toIntOrNull(f[2]);
                pending.add(new Due(now + (a != null ? a : 0), b != null ? b : 0, c != null ? c : 0));
            }
        }
        for (int pit : closedPits) out.addAll(pitEdits(pit, !openedPits.contains(pit)));
        return out;
    }

    /** The blocks of a gate in its current state. */
    public List<BlockEdit> gateEdits(Gate g) {
        String block = g.lever.gateBlock;
        if (block == null) throw new NullPointerException();
        if (g.extended) return List.of(
                new BlockEdit(g.x, g.y, PISTON_Z, "minecraft:sticky_piston[extended=true,facing=north]"), new BlockEdit(g.x, g.y, HEAD_Z, HEAD), new BlockEdit(g.x, g.y, SHEET_Z, block));
        return List.of(
                new BlockEdit(g.x, g.y, PISTON_Z, "minecraft:sticky_piston[extended=false,facing=north]"), new BlockEdit(g.x, g.y, HEAD_Z, block), new BlockEdit(g.x, g.y, SHEET_Z, AIR));
    }

    public static final String AIR = "minecraft:air";
    private static final String DEFAULT_LEVER = "minecraft:lever[face=wall,facing=east,powered=false]";
    private static final String HEAD = "minecraft:piston_head[facing=north,short=false,type=sticky]";

    /** Ticks from a lever click to the gates moving. */
    public static final int GATE_DELAY_TICKS = 0;
    /** The block that plugs the intake. */
    public static final String PLUG = "minecraft:lapis_block";

    /** The sheet the water runs in, the layer of an extended gate's head, and the layer of the pistons. */
    public static final int SHEET_Z = 26;
    public static final int HEAD_Z = 27;
    public static final int PISTON_Z = 28;

    /** The intake: the water lever puts a source here. */
    public static final int INTAKE_X = 15;
    public static final int INTAKE_Y = 82;

    /** The five drains, by pit index (Odin's wool order: purple, orange, blue, green, red). */
    public static final int[] DRAIN_X = {24, 20, 15, 10, 6};
    public static final int DRAIN_Y = 58;

    /** Wool colour of each pit, in the same order. */
    public static final String[] PIT_WOOL = {"purple_wool", "orange_wool", "blue_wool", "lime_wool", "red_wool"};

    /** The z plane of pit k (Odin reads the wool at x = 15, y = 56, z = 19 - k). */
    public static int pitZ(int k) { return 19 - k; }

    private static final int K_WATER = 0;
    private static final int K_GATES = 1;
    private static final int K_APPLY = 2;
    private static final int K_PIT = 3;

    private static final int BOX_Y_MIN = 56;
    private static final int BOX_Z_MIN = 24;
    private static final int BOX_Z_MAX = 29;

    /** Three of the five pits are closed in every Water Board. */
    public static Set<Integer> randomClosedPits(KRandom random) {
        List<Integer> l = new ArrayList<>(List.of(0, 1, 2, 3, 4));
        KRandom.shuffle(l, random);
        return new LinkedHashSet<>(l.subList(0, 3));
    }

    public static String withPowered(String state, boolean on) {
        return on ? state.replace("powered=false", "powered=true") : state.replace("powered=true", "powered=false");
    }

    /** Builds the model from the room's volume, or null if it is not a Water Board. closedPits null reads them from the volume. */
    public static WaterBoardModel create(RoomVolume volume, Set<Integer> closedPits) {
        if (!volume.contains(INTAKE_X, INTAKE_Y, SHEET_Z) || volume.isAir(INTAKE_X, INTAKE_Y, SHEET_Z)) return null;
        ArrayList<Gate> gates = new ArrayList<>();
        for (int y = BOX_Y_MIN; y < volume.y0 + volume.h; y++) for (int x = 0; x < volume.w; x++) {
            String piston = volume.stateAt(x, y, PISTON_Z);
            if (!piston.startsWith("minecraft:sticky_piston") || !piston.contains("facing=north")) continue;
            boolean extended = volume.stateAt(x, y, HEAD_Z).startsWith("minecraft:piston_head");
            String block = substringBefore(volume.stateAt(x, y, extended ? SHEET_Z : HEAD_Z), "[");
            WaterLever lever = WaterLever.ofGateBlock(block);
            if (lever == null) continue;
            gates.add(new Gate(x, y, lever, extended));
        }
        if (gates.isEmpty()) return null;
        int yTop = volume.y0 + volume.h;
        WaterSim sim = new WaterSim(0, BOX_Y_MIN, BOX_Z_MIN, volume.w, yTop - BOX_Y_MIN, BOX_Z_MAX - BOX_Z_MIN + 1);
        for (int y = BOX_Y_MIN; y < yTop; y++) for (int z = BOX_Z_MIN; z <= BOX_Z_MAX; z++) for (int x = 0; x < volume.w; x++) {
            String s = volume.stateAt(x, y, z);
            if (s.equals(AIR)) continue;
            if (s.startsWith("minecraft:water[level=")) {
                Integer lv = toIntOrNull(substringBefore(substringAfter(s, "level="), "]"));
                sim.setStaticWater(x, y, z, lv != null ? lv : 0);
            } else sim.setSolid(x, y, z, true);
        }
        Set<Integer> closed = new LinkedHashSet<>();
        if (closedPits != null) closed.addAll(closedPits);
        else for (int k = 0; k <= 4; k++) if (!volume.isAir(15, 56, pitZ(k))) closed.add(k);
        HashMap<WaterLever, Boolean> lev = new HashMap<>();
        HashMap<WaterLever, String> base = new HashMap<>();
        for (WaterLever l : WaterLever.values()) {
            String st = volume.stateAt(l.x, l.y, l.z);
            lev.put(l, st.contains("powered=true"));
            base.put(l, st.startsWith("minecraft:lever") ? withPowered(st, false) : DEFAULT_LEVER);
        }
        return new WaterBoardModel(sim, gates, lev, base, closed);
    }

    public static WaterBoardModel create(RoomVolume volume) { return create(volume, null); }

    private static String substringBefore(String s, String d) {
        int i = s.indexOf(d);
        return i < 0 ? s : s.substring(0, i);
    }

    private static String substringAfter(String s, String d) {
        int i = s.indexOf(d);
        return i < 0 ? s : s.substring(i + d.length());
    }

    /** The blocks of pit k: closed, side pistons out and wool in the passage; open, wool pulled aside and redstone back to stone. */
    public static List<BlockEdit> pitEdits(int k, boolean closed) {
        int z = pitZ(k);
        String wool = "minecraft:" + PIT_WOOL[k];
        ArrayList<BlockEdit> out = new ArrayList<>();
        String stone = "minecraft:stone";
        if (closed) {
            out.add(new BlockEdit(15, 53, z, "minecraft:redstone_block"));
            out.add(new BlockEdit(15, 54, z, "minecraft:sticky_piston[extended=true,facing=up]"));
            out.add(new BlockEdit(15, 55, z, "minecraft:piston_head[facing=up,short=false,type=sticky]"));
            for (int y = 56; y <= 57; y++) {
                out.add(new BlockEdit(11, y, z, "minecraft:redstone_block"));
                out.add(new BlockEdit(12, y, z, "minecraft:sticky_piston[extended=true,facing=east]"));
                out.add(new BlockEdit(13, y, z, "minecraft:piston_head[facing=east,short=false,type=sticky]"));
                for (int x = 14; x <= 16; x++) out.add(new BlockEdit(x, y, z, wool));
                out.add(new BlockEdit(17, y, z, "minecraft:piston_head[facing=west,short=false,type=sticky]"));
                out.add(new BlockEdit(18, y, z, "minecraft:sticky_piston[extended=true,facing=west]"));
                out.add(new BlockEdit(19, y, z, "minecraft:redstone_block"));
            }
        } else {
            out.add(new BlockEdit(15, 53, z, stone));
            out.add(new BlockEdit(15, 54, z, "minecraft:sticky_piston[extended=false,facing=up]"));
            out.add(new BlockEdit(15, 55, z, wool));
            for (int y = 56; y <= 57; y++) {
                out.add(new BlockEdit(11, y, z, stone));
                out.add(new BlockEdit(12, y, z, "minecraft:sticky_piston[extended=false,facing=east]"));
                out.add(new BlockEdit(13, y, z, wool));
                for (int x = 14; x <= 16; x++) out.add(new BlockEdit(x, y, z, AIR));
                out.add(new BlockEdit(17, y, z, wool));
                out.add(new BlockEdit(18, y, z, "minecraft:sticky_piston[extended=false,facing=west]"));
                out.add(new BlockEdit(19, y, z, stone));
            }
        }
        return out;
    }
}
