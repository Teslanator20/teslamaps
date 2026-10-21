// Ported from ADM's p3sim/run/DungeonPlan.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.Rot;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import com.teslamaps.adm.sim.pack.layouts.Layout;
import com.teslamaps.adm.sim.pack.layouts.LayoutDoor;
import com.teslamaps.adm.sim.pack.layouts.LayoutKey;
import com.teslamaps.adm.sim.pack.layouts.LayoutRoom;
import com.teslamaps.adm.sim.pack.rooms.CoreEntry;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.PlacementException;
import com.teslamaps.adm.sim.pack.rooms.RoomDesign;
import com.teslamaps.adm.sim.pack.rooms.RoomPack;
import com.teslamaps.adm.sim.pack.runstate.RoomRun;
import com.teslamaps.adm.sim.pack.runstate.RunStatePack;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Triple;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The dungeon of one run, decided and immutable. Pure data. */
public class DungeonPlan {
    public final String layoutId;
    public final String floor;
    public final long seed;
    public final List<PlacedRoom> rooms;
    public final List<PlacedDoor> doors;
    /** Room indices entrance -> blood. */
    public final List<Integer> path;
    public final List<LayoutKey> recordedKeys;
    public final List<String> puzzleNames;
    /** Set for a practice run. */
    public final PracticeStart practice;

    private final Map<Tile, PlacedRoom> tileRoom;
    private final Map<Triple<Character, Integer, Integer>, PlacedDoor> doorBySlot;

    public DungeonPlan(String layoutId, String floor, long seed, List<PlacedRoom> rooms, List<PlacedDoor> doors, List<Integer> path,
                       List<LayoutKey> recordedKeys, List<String> puzzleNames, PracticeStart practice) {
        this.layoutId = layoutId;
        this.floor = floor;
        this.seed = seed;
        this.rooms = rooms;
        this.doors = doors;
        this.path = path;
        this.recordedKeys = recordedKeys;
        this.puzzleNames = puzzleNames;
        this.practice = practice;
        LinkedHashMap<Tile, PlacedRoom> tr = new LinkedHashMap<>();
        for (PlacedRoom r : rooms) for (Tile t : r.tiles) tr.put(t, r);
        this.tileRoom = tr;
        LinkedHashMap<Triple<Character, Integer, Integer>, PlacedDoor> ds = new LinkedHashMap<>();
        for (PlacedDoor d : doors) ds.put(new Triple<>(d.axis, d.a.x, d.a.z), d);
        this.doorBySlot = ds;
    }

    public DungeonPlan(String layoutId, String floor, long seed, List<PlacedRoom> rooms, List<PlacedDoor> doors, List<Integer> path,
                       List<LayoutKey> recordedKeys, List<String> puzzleNames) {
        this(layoutId, floor, seed, rooms, doors, path, recordedKeys, puzzleNames, null);
    }

    public PlacedRoom roomAt(int tx, int tz) { return tileRoom.get(new Tile(tx, tz)); }

    /** The room a world position stands in, or null. */
    public PlacedRoom roomAtBlock(double x, double z) {
        Tile t = DungeonFrame.tileOf(x, z);
        return t != null ? tileRoom.get(t) : null;
    }

    public PlacedDoor doorAt(char axis, int tx, int tz) { return doorBySlot.get(new Triple<>(axis, tx, tz)); }

    /** The door whose body volume contains the block, or null. */
    public PlacedDoor doorContaining(int x, int y, int z) {
        for (PlacedDoor d : doors) if (d.contains(x, y, z)) return d;
        return null;
    }

    /** Every door slot between two rooms. */
    public List<PlacedDoor> doorsBetween(int roomA, int roomB) {
        List<PlacedDoor> out = new ArrayList<>();
        for (PlacedDoor d : doors) if ((d.roomA == roomA && d.roomB == roomB) || (d.roomA == roomB && d.roomB == roomA)) out.add(d);
        return out;
    }

    /** The connected slot between two rooms, else the first slot (a wall), else null. */
    public PlacedDoor doorBetween(int roomA, int roomB) {
        List<PlacedDoor> l = doorsBetween(roomA, roomB);
        for (PlacedDoor d : l) if (d.kind.connected()) return d;
        return l.isEmpty() ? null : l.get(0);
    }

    public PlacedRoom entrance() {
        for (PlacedRoom r : rooms) if (r.type == RoomType.ENTRANCE) return r;
        return null;
    }

    public PlacedRoom blood() {
        for (PlacedRoom r : rooms) if (r.type == RoomType.BLOOD) return r;
        return null;
    }

    public PlacedRoom fairy() {
        for (PlacedRoom r : rooms) if (r.type == RoomType.FAIRY) return r;
        return null;
    }

    /** Doors that cost a wither key. */
    public List<PlacedDoor> paidWitherDoors() {
        List<PlacedDoor> out = new ArrayList<>();
        for (PlacedDoor d : doors) if (d.kind == DoorKind.WITHER) out.add(d);
        return out;
    }

    /** The doors on the entrance -> blood path, in path order. */
    public List<PlacedDoor> pathDoors() {
        List<PlacedDoor> out = new ArrayList<>();
        for (int i = 0; i + 1 < path.size(); i++) {
            PlacedDoor d = doorBetween(path.get(i), path.get(i + 1));
            if (d != null && d.kind.connected()) out.add(d);
        }
        return out;
    }

    /** Total tile units. */
    public int tileUnits() {
        int n = 0;
        for (PlacedRoom r : rooms) n += r.tileCount();
        return n;
    }

    /** Sum of the rooms' secret maxima. */
    public int secretsTotal() {
        int n = 0;
        for (PlacedRoom r : rooms) n += r.secretsMax;
        return n;
    }

    public static Result build(Layout layout, long seed, RoomPack rooms) { return build(layout, seed, rooms, null); }

    /** Resolves a recorded layout into a plan; a PlanFailure instead of throwing. */
    public static Result build(Layout layout, long seed, RoomPack rooms, RunStatePack runstate) {
        ArrayList<PlacedRoom> placed = new ArrayList<>();
        for (LayoutRoom r : layout.rooms) {
            Rot rot = r.rot;
            if (rot == null) return new Result.Failed(new PlanFailure(layout.id + ": the room " + r.name + " has no known rotation, so this layout cannot be built."));
            RoomDesign design = null;
            String designId = r.design != null ? r.design : r.family;
            String variantId = null;
            if (rooms != null) {
                design = r.design != null ? rooms.design(r.design) : null;
                if (design == null && r.core != null) {
                    CoreEntry ce = rooms.designByCore(r.core);
                    design = ce != null ? rooms.design(ce.design) : null;
                }
                if (design == null) {
                    List<RoomDesign> builds = new ArrayList<>(rooms.designsOfFamily(r.family));
                    builds.sort(Comparator.comparing((RoomDesign d) -> d.id));
                    if (!builds.isEmpty()) design = builds.get(new KRandom(seed ^ ((long) r.i * 0x9E3779B97F4A7C15L)).nextInt(builds.size()));
                }
                if (design == null) return new Result.Failed(new PlanFailure(layout.id + ": the room library has no " + r.name + " (" + r.family + "), so this layout cannot be built."));
                designId = design.id;
            }
            // the recorded Odin core says which variant the room had
            if (design != null && r.core != null) {
                CoreEntry ce = rooms != null ? rooms.designByCore(r.core) : null;
                variantId = ce != null && ce.design.equals(design.id) ? ce.variant : null;
            }
            Placement placement = null;
            if (design != null) {
                try {
                    placement = design.place(rot, r.tiles);
                } catch (PlacementException e) {
                    return new Result.Failed(new PlanFailure(layout.id + ": " + r.name + " cannot be placed: " + e.getMessage()));
                }
            }
            int secrets;
            RoomRun rr = runstate != null ? runstate.room(r.name) : null;
            if (rr != null) secrets = rr.secretsMax;
            else if (r.secretsMax != null) secrets = r.secretsMax;
            else if (design != null && design.odinSecrets() != null) secrets = design.odinSecrets();
            else secrets = 0;
            List<Tile> sortedTiles = new ArrayList<>(r.tiles);
            sortedTiles.sort(Comparator.<Tile>comparingInt(t -> t.z).thenComparingInt(t -> t.x));
            placed.add(new PlacedRoom(
                    r.i, r.name, r.family, designId, design, RoomType.fromWord(r.type), r.shape, sortedTiles,
                    placement != null ? placement.rot : rot, placement, secrets, r.tNone, r.tWhite, r.tGreen, r.tRed, r, variantId));
        }
        List<PlacedRoom> indexed = new ArrayList<>(placed);
        indexed.sort(Comparator.comparingInt(p -> p.index));
        List<PlacedDoor> doors = new ArrayList<>();
        for (int i = 0; i < layout.doors.size(); i++) {
            LayoutDoor d = layout.doors.get(i);
            doors.add(new PlacedDoor(i, d.axis, d.a, d.b, d.ra, d.rb, d.kind, d.tOpen));
        }
        List<Integer> path = layout.path;
        if (!layout.doorsComplete || path == null) {
            return new Result.Failed(new PlanFailure(layout.id + ": its doors were not fully recorded, so this layout is not playable."));
        }
        return new Result.Ok(new DungeonPlan(layout.id, layout.floor, seed, indexed, doors, path, layout.keys, layout.puzzles));
    }

    /** What build answers. */
    public interface Result {
        final class Ok implements Result {
            public final DungeonPlan plan;

            public Ok(DungeonPlan plan) { this.plan = plan; }
        }

        final class Failed implements Result {
            public final PlanFailure failure;

            public Failed(PlanFailure failure) { this.failure = failure; }
        }
    }
}
