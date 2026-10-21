// Ported from ADM's p3sim/run/PracticePlan.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.Rot;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.pack.layouts.Layout;
import com.teslamaps.adm.sim.pack.layouts.LayoutPack;
import com.teslamaps.adm.sim.pack.layouts.LayoutRoom;
import com.teslamaps.adm.sim.pack.rooms.DoorSlot;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.PlacementException;
import com.teslamaps.adm.sim.pack.rooms.RoomDesign;
import com.teslamaps.adm.sim.pack.rooms.RoomPack;
import com.teslamaps.adm.sim.pack.runstate.RunStatePack;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.adm.sim.util.Triple;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The dungeon of a practice run: one room as a recorded run had it, alone in the middle of the map, entered through one doorway
 * turned to its south side (every practice starts at the door, facing north). Pure.
 */
public final class PracticePlan {
    private PracticePlan() {}

    /** A room that can be practised and in how many recorded runs it is. */
    public static class Choice {
        public final String name;
        public final RoomType type;
        public final String shape;
        public final int runs;

        public Choice(String name, RoomType type, String shape, int runs) {
            this.name = name;
            this.type = type;
            this.shape = shape;
            this.runs = runs;
        }
    }

    private static final Set<RoomType> NOT_PRACTISED = Set.of(RoomType.ENTRANCE, RoomType.BLOOD);

    /** Complete doors, not a duplicate, the floor. */
    private static List<Layout> sources(LayoutPack pack, String floor) {
        List<Layout> out = new ArrayList<>();
        for (Layout l : pack.layouts) if (Objects.equals(l.floor, floor) && l.doorsComplete && l.path != null && l.dupOf == null) out.add(l);
        return out;
    }

    public static List<Choice> choices(LayoutPack pack) { return choices(pack, "F7"); }

    /** Every room but the entrance and blood, puzzles first, then by name. */
    public static List<Choice> choices(LayoutPack pack, String floor) {
        Map<String, List<LayoutRoom>> byName = new LinkedHashMap<>();
        for (Layout l : sources(pack, floor)) {
            for (LayoutRoom r : l.rooms) {
                if (NOT_PRACTISED.contains(RoomType.fromWord(r.type))) continue;
                byName.computeIfAbsent(r.name, k -> new ArrayList<>()).add(r);
            }
        }
        List<Choice> out = new ArrayList<>();
        for (Map.Entry<String, List<LayoutRoom>> e : byName.entrySet()) {
            List<LayoutRoom> l = e.getValue();
            out.add(new Choice(e.getKey(), RoomType.fromWord(l.get(0).type), l.get(0).shape, l.size()));
        }
        out.sort(Comparator.comparing((Choice c) -> c.type != RoomType.PUZZLE).thenComparing(c -> c.name));
        return out;
    }

    public static DungeonPlan.Result build(String room, LayoutPack pack, long seed, RoomPack rooms, RunStatePack runstate) {
        return build(room, pack, seed, rooms, runstate, "F7");
    }

    /** The practice plan for room; the recorded run is drawn by seed among those that have it, unbuildable ones skipped. */
    public static DungeonPlan.Result build(String room, LayoutPack pack, long seed, RoomPack rooms, RunStatePack runstate, String floor) {
        List<Layout> have = new ArrayList<>();
        for (Layout l : sources(pack, floor)) {
            for (LayoutRoom r : l.rooms) if (r.name.equalsIgnoreCase(room)) { have.add(l); break; }
        }
        if (have.isEmpty()) return new DungeonPlan.Result.Failed(new PlanFailure("No recorded " + floor + " run has a room called " + room + ", so it cannot be practised."));
        int start = new java.util.Random(seed ^ 0x9AC71CEL).nextInt(have.size());
        String last = null;
        for (int i = 0; i < have.size(); i++) {
            Layout l = have.get((start + i) % have.size());
            DungeonPlan.Result r = from(l, room, seed, rooms, runstate);
            if (r instanceof DungeonPlan.Result.Ok) return r;
            if (r instanceof DungeonPlan.Result.Failed f) last = f.failure.message;
        }
        return new DungeonPlan.Result.Failed(new PlanFailure(last != null ? last : room + " could not be built for practice."));
    }

    private static Pair<Tile, Character> sideOf(PlacedRoom r, PlacedDoor d) {
        if (d.roomA == r.index) return Pair.of(d.a, d.axis == 'E' ? 'E' : 'S');
        return Pair.of(d.b, d.axis == 'E' ? 'W' : 'N');
    }

    /** room of layout, alone, its way in turned south when it can be, else as recorded and centred. */
    public static DungeonPlan.Result from(Layout layout, String room, long seed, RoomPack rooms, RunStatePack runstate) {
        DungeonPlan.Result b = DungeonPlan.build(layout, seed, rooms, runstate);
        if (b instanceof DungeonPlan.Result.Failed) return b;
        DungeonPlan full = ((DungeonPlan.Result.Ok) b).plan;
        PlacedRoom r = null;
        for (PlacedRoom x : full.rooms) if (x.name.equalsIgnoreCase(room)) { r = x; break; }
        if (r == null) return new DungeonPlan.Result.Failed(new PlanFailure(layout.id + " has no " + room + "."));
        List<PlacedDoor> doors = new ArrayList<>();
        for (PlacedDoor d : full.doors) if (d.kind.connected() && (d.roomA == r.index || d.roomB == r.index)) doors.add(d);
        doors.sort(Comparator.comparing((PlacedDoor d) -> d.recordedOpen, Comparator.nullsLast(Comparator.<Integer>naturalOrder())));
        if (doors.isEmpty()) return new DungeonPlan.Result.Failed(new PlanFailure(room + " has no doorway in " + layout.id + "."));
        List<Pair<Tile, Character>> sides = new ArrayList<>();
        for (PlacedDoor d : doors) sides.add(sideOf(r, d));
        Triple<List<Tile>, Rot, PracticeStart> turned = southFacing(r, sides);
        List<Tile> tiles;
        Rot rot;
        PracticeStart door;
        if (turned != null) {
            tiles = turned.first;
            rot = turned.second;
            door = turned.third;
        } else {
            // no turn puts a doorway south: as recorded, centred (an odd width leans north-west)
            Pair<Tile, Character> ts = sideOf(r, doors.get(0));
            Tile tile = ts.first;
            char side = ts.second;
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (Tile t : r.tiles) {
                minX = Math.min(minX, t.x); minZ = Math.min(minZ, t.z);
                maxX = Math.max(maxX, t.x); maxZ = Math.max(maxZ, t.z);
            }
            if (r.tiles.isEmpty()) throw new java.util.NoSuchElementException();
            int w = maxX - minX + 1;
            int d = maxZ - minZ + 1;
            int dx = (DungeonFrame.TILES - w) / 2 - minX;
            int dz = (DungeonFrame.TILES - d) / 2 - minZ;
            List<Tile> moved = new ArrayList<>();
            for (Tile t : r.tiles) moved.add(new Tile(t.x + dx, t.z + dz));
            moved.sort(Comparator.<Tile>comparingInt(t -> t.z).thenComparingInt(t -> t.x));
            tiles = moved;
            rot = r.rotation;
            door = new PracticeStart(tile.x + dx, tile.z + dz, side);
        }
        Placement placement = null;
        if (r.design != null) {
            try {
                placement = r.design.place(rot, tiles);
            } catch (PlacementException e) {
                return new DungeonPlan.Result.Failed(new PlanFailure(room + " cannot be placed for practice: " + e.getMessage()));
            }
        }
        PlacedRoom placed = new PlacedRoom(
                0, r.name, r.family, r.designId, r.design, r.type, r.shape, tiles, rot, placement, r.secretsMax,
                null, null, null, null, r.layoutRoom, r.variantId);
        List<String> puzzles = r.type == RoomType.PUZZLE ? List.of(r.name) : List.of();
        return new DungeonPlan.Result.Ok(new DungeonPlan(
                "practice: " + r.name + " (" + layout.id + ")", layout.floor, seed, List.of(placed), List.of(), List.of(0), List.of(), puzzles,
                door));
    }

    /** Quarter turns clockwise from south, as Rot.q counts them. */
    private static final String SIDES = "SWNE";

    /**
     * r turned so one of its doorways (recorded first, then the design's other open ones) is on its south side, on the map's middle
     * line and in tile column 2 or 3, whichever centres the room. Only turns the design is placed in. Null when none works.
     */
    private static Triple<List<Tile>, Rot, PracticeStart> southFacing(PlacedRoom r, List<Pair<Tile, Character>> doors) {
        RoomDesign design = r.design;
        if (design == null) return null;
        Placement now = r.placement;
        if (now == null) return null;
        List<Tile> cells = Placement.canonicalTiles(r.shape);
        List<Rot> rots = new ArrayList<>();
        rots.add(r.rotation);
        for (Rot x : Rot.values()) if (x != r.rotation) rots.add(x);
        // the doorways in the room's own frame: the recorded ones, then its other open doorways
        LinkedHashSet<Pair<Tile, Character>> ways = new LinkedHashSet<>();
        for (Pair<Tile, Character> ds : doors) {
            Tile tile = ds.first;
            char side = ds.second;
            for (Tile c : cells) {
                if (now.worldTileOf(c.x, c.z).equals(tile)) {
                    ways.add(Pair.of(c, SIDES.charAt(Math.floorMod(SIDES.indexOf(side) - now.rot.q, 4))));
                    break;
                }
            }
        }
        for (DoorSlot s : design.slots) if (s.open) ways.add(Pair.of(s.tile, s.side));
        for (Pair<Tile, Character> way : ways) {
            Tile cell = way.first;
            char canSide = way.second;
            for (Rot rot : rots) {
                if (SIDES.charAt((SIDES.indexOf(canSide) + rot.q) % 4) != 'S') continue;
                Placement at0;
                try {
                    at0 = design.place(rot, Placement.tilesFor(r.shape, rot, 0, 0));
                } catch (PlacementException e) {
                    continue;
                }
                Tile door0 = at0.worldTileOf(cell.x, cell.z);
                // the door's tile: as near the middle row as fits, then the column that centres the room
                Triple<Tile, List<Tile>, Double> best = null;
                for (int tz = 2; tz < DungeonFrame.TILES; tz++) {
                    for (int tx : new int[] {2, 3}) {
                        Tile at = new Tile(tx, tz);
                        List<Tile> t = Placement.tilesFor(r.shape, rot, at.x - door0.x, at.z - door0.z);
                        boolean out = false;
                        for (Tile q : t) {
                            if (q.x < 0 || q.x >= DungeonFrame.TILES || q.z < 0 || q.z >= DungeonFrame.TILES) { out = true; break; }
                        }
                        if (out) continue;
                        double sum = 0.0;
                        for (Tile q : t) sum += q.x + 0.5;
                        Triple<Tile, List<Tile>, Double> cand = new Triple<>(at, t, Math.abs(sum / t.size() - DungeonFrame.TILES / 2.0));
                        if (best == null || better(cand, best)) best = cand;
                    }
                }
                if (best == null) continue;
                return new Triple<>(best.second, rot, new PracticeStart(best.first.x, best.first.z, 'S'));
            }
        }
        return null;
    }

    /** compareBy(first.z, third) strictly less: the first minimum wins. */
    private static boolean better(Triple<Tile, List<Tile>, Double> a, Triple<Tile, List<Tile>, Double> b) {
        if (a.first.z != b.first.z) return a.first.z < b.first.z;
        return a.third.compareTo(b.third) < 0;
    }
}
