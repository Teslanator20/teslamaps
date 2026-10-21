package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/DoorPlanner.kt

import com.teslamaps.adm.sim.pack.DoorModel;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import com.teslamaps.adm.sim.pack.rooms.DoorSlot;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.SealCell;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.adm.sim.util.Triple;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class DoorPlanner {
    private DoorPlanner() {}

    public static final int CARVE_DEPTH = 2;
    public static final int CARVE_MAX_DEPTH = 4;
    public static final int WALL_PLANE_CELLS = 10;
    public static final int SEAL_DEPTH = 2;

    private static String before(String s, char c) {
        int i = s.indexOf(c);
        return i < 0 ? s : s.substring(0, i);
    }

    private static boolean isDoorBody(String state) {
        String n = before(state, '[');
        return n.equals("minecraft:coal_block") || n.equals("minecraft:red_terracotta") || n.startsWith("minecraft:infested_");
    }

    private static final List<String> NOT_A_WALL = List.of(
            "stairs", "slab", "wall", "fence", "bars", "pane", "door", "trapdoor", "carpet", "lantern", "button", "lever", "sign", "banner",
            "skull", "head", "torch", "vine", "chain", "rail", "plate", "ladder", "flower", "pot", "bed", "chest", "air", "glass", "leaves",
            "grass", "fern", "bush", "candle", "cobweb", "web", "fire", "lava", "water", "barrier", "light", "hopper", "dispenser", "dropper",
            "anvil", "cauldron", "lectern", "brewing", "skeleton", "zombie", "creeper", "piglin", "dragon"
    );

    private static boolean looksLikeWall(String state) {
        String n = before(state, '[');
        if (n.startsWith("minecraft:")) n = n.substring("minecraft:".length());
        for (String w : NOT_A_WALL) if (n.contains(w)) return false;
        return true;
    }

    public static String wallBlock(RoomInstance room, int tx, int tz, char side) {
        HashMap<String, Integer> counts = new HashMap<>();
        for (int a = DoorGeometry.FRAME_MIN - 5; a <= DoorGeometry.FRAME_MAX + 5; a++) {
            if (a >= DoorGeometry.OPEN_MIN && a <= DoorGeometry.OPEN_MAX) continue;
            Pair<Integer, Integer> c = DoorGeometry.sideCell(tx, tz, side, a, 0);
            for (int y = DoorModel.BODY_Y_MIN; y <= DoorModel.BODY_Y_MAX; y++) {
                String st = room.stateAt(c.first, y, c.second);
                if (st == null) continue;
                String s = before(st, '[');
                if (looksLikeWall(s) && !isDoorBody(s)) counts.merge(s, 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        Comparator<Map.Entry<String, Integer>> byCount = Comparator.comparingInt(e -> e.getValue());
        Comparator<Map.Entry<String, Integer>> byKey = Comparator.comparing(e -> e.getKey());
        entries.sort(byCount.reversed().thenComparing(byKey));
        return entries.isEmpty() ? DoorGeometry.FRAME_BLOCK : entries.get(0).getKey();
    }

    private static String floorBlock(List<RoomInstance> rooms, int x, int z) {
        String s = null;
        for (RoomInstance r : rooms) {
            s = r.stateAt(x, DoorModel.THRESHOLD_Y, z);
            if (s != null) break;
        }
        if (s == null) return null;
        return looksLikeWall(s) && !isDoorBody(s) ? s : null;
    }

    public static int solidCells(RoomInstance room, int tx, int tz, char side, int depth) {
        int n = 0;
        for (Triple<Integer, Integer, Integer> c : DoorGeometry.openingCells(tx, tz, side, depth)) if (room.isSolid(c.first, c.second, c.third)) n++;
        return n;
    }

    public static int carveDepth(RoomInstance room, int tx, int tz, char side) {
        int d = CARVE_DEPTH;
        while (d < CARVE_MAX_DEPTH && solidCells(room, tx, tz, side, d - 1) >= WALL_PLANE_CELLS) d++;
        return d;
    }

    public static List<Edit> plan(List<RoomInstance> rooms, Map<Slot, DoorKind> doors) { return plan(rooms, doors, 0L); }

    public static List<Edit> plan(List<RoomInstance> rooms, Map<Slot, DoorKind> doors, long seed) {
        ArrayList<Edit> sides = new ArrayList<>();
        for (RoomInstance room : rooms) {
            Placement p = room.placement;
            for (DoorSlot slot : room.design.slots) {
                Tile wt = p.worldTileOf(slot.tile.x, slot.tile.z);
                char side = DoorGeometry.turn(slot.side, p.quarterTurns());
                Slot gap = DoorGeometry.slotOf(wt.x, wt.z, side);
                DoorKind k = gap != null ? doors.get(gap) : null;
                boolean connected = k != null && k.connected();
                if (connected) {
                    if (!slot.open) carve(room, wt.x, wt.z, side, sides);
                } else if (slot.open) {
                    seal(room, slot.seal != null && slot.seal.cells != null ? slot.seal.cells : List.of(), wt.x, wt.z, side, sides);
                } else {
                    scrub(room, wt.x, wt.z, side, sides);
                }
            }
        }
        ArrayList<Edit> frames = new ArrayList<>();
        ArrayList<Edit> bodies = new ArrayList<>();
        List<Map.Entry<Slot, DoorKind>> sorted = new ArrayList<>(doors.entrySet());
        sorted.sort(Comparator.<Map.Entry<Slot, DoorKind>>comparingInt(e -> e.getKey().axis)
                .thenComparingInt(e -> e.getKey().tz)
                .thenComparingInt(e -> e.getKey().tx));
        for (Map.Entry<Slot, DoorKind> en : sorted) {
            Slot slot = en.getKey();
            DoorKind kind = en.getValue();
            if (!kind.connected()) continue;
            frames.addAll(DoorFrames.frame(slot, DoorFrames.styleFor(kind, slot, seed), (x, z) -> floorBlock(rooms, x, z)));
            bodies.addAll(DoorGeometry.bodyEdits(slot, kind));
        }
        ArrayList<Edit> out = new ArrayList<>(sides.size() + frames.size() + bodies.size());
        out.addAll(sides);
        out.addAll(frames);
        out.addAll(bodies);
        return out;
    }

    private static void carve(RoomInstance room, int tx, int tz, char side, List<Edit> out) {
        int depth = carveDepth(room, tx, tz, side);
        for (int d = 0; d < depth; d++) for (Triple<Integer, Integer, Integer> c : DoorGeometry.openingCells(tx, tz, side, d)) {
            if (room.isSolid(c.first, c.second, c.third)) out.add(new Edit(c.first, c.second, c.third, AIR));
        }
    }

    private static void seal(RoomInstance room, List<SealCell> cells, int tx, int tz, char side, List<Edit> out) {
        Placement p = room.placement;
        HashSet<Triple<Integer, Integer, Integer>> written = new HashSet<>();
        for (SealCell c : cells) {
            WorldPos w = p.toWorld(c.pos.x, c.pos.y, c.pos.z);
            out.add(new Edit(w.x, w.y, w.z, c.state, p.quarterTurns()));
            if (!c.state.equals(AIR)) written.add(new Triple<>(w.x, w.y, w.z));
        }
        String wall = wallBlock(room, tx, tz, side);
        for (int d = 0; d < SEAL_DEPTH; d++) for (Triple<Integer, Integer, Integer> cell : DoorGeometry.openingCells(tx, tz, side, d)) {
            if (!written.contains(cell) && !room.isSolid(cell.first, cell.second, cell.third)) out.add(new Edit(cell.first, cell.second, cell.third, wall));
        }
    }

    private static void scrub(RoomInstance room, int tx, int tz, char side, List<Edit> out) {
        List<Triple<Integer, Integer, Integer>> cells = DoorGeometry.openingCells(tx, tz, side, 0);
        String first = null;
        for (Triple<Integer, Integer, Integer> c : cells) {
            first = room.stateAt(c.first, c.second, c.third);
            if (first != null) break;
        }
        if (first == null) return;
        if (!isDoorBody(first)) return;
        for (Triple<Integer, Integer, Integer> c : cells) if (!first.equals(room.stateAt(c.first, c.second, c.third))) return;
        String wall = wallBlock(room, tx, tz, side);
        for (Triple<Integer, Integer, Integer> c : cells) out.add(new Edit(c.first, c.second, c.third, wall));
    }

    private static final String AIR = "minecraft:air";
}
