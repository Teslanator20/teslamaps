// Ported from ADM's p3sim/pack/layouts/LayoutPack.kt
package com.teslamaps.adm.sim.pack.layouts;

import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.adm.sim.util.Triple;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One recorded layout. */
public class Layout {
    public final String id;
    /** {@code F7} or {@code M7}. */
    public final String floor;
    public final String date;
    public final String source;
    public final List<LayoutRoom> rooms;
    public final List<LayoutDoor> doors;
    public final boolean doorsComplete;
    /** Room indices entrance -> blood along connected doors; null unless [doorsComplete]. */
    public final List<Integer> path;
    public final List<LayoutKey> keys;
    /** Names of the rooms with {@code type == "puzzle"}, in {@code rooms} order. */
    public final List<String> puzzles;
    public final int secretsTotal;
    public final String t0Basis;
    public final Integer endTick;
    /** Class names of the recorded party. */
    public final List<String> party;
    public final Boolean buildable;
    public final Boolean playable;
    public final String dupOf;
    public final Map<String, Integer> rotSummary;

    private final Map<Tile, LayoutRoom> tileRoom;
    private final Map<Triple<Character, Integer, Integer>, LayoutDoor> doorBySlot;

    public Layout(String id, String floor, String date, String source, List<LayoutRoom> rooms, List<LayoutDoor> doors, boolean doorsComplete,
                  List<Integer> path, List<LayoutKey> keys, List<String> puzzles, int secretsTotal, String t0Basis, Integer endTick,
                  List<String> party, Boolean buildable, Boolean playable, String dupOf, Map<String, Integer> rotSummary) {
        this.id = id;
        this.floor = floor;
        this.date = date;
        this.source = source;
        this.rooms = rooms;
        this.doors = doors;
        this.doorsComplete = doorsComplete;
        this.path = path;
        this.keys = keys;
        this.puzzles = puzzles;
        this.secretsTotal = secretsTotal;
        this.t0Basis = t0Basis;
        this.endTick = endTick;
        this.party = party;
        this.buildable = buildable;
        this.playable = playable;
        this.dupOf = dupOf;
        this.rotSummary = rotSummary;
        LinkedHashMap<Tile, LayoutRoom> tr = new LinkedHashMap<>();
        for (LayoutRoom r : rooms) for (Tile t : r.tiles) tr.put(t, r);
        this.tileRoom = tr;
        LinkedHashMap<Triple<Character, Integer, Integer>, LayoutDoor> ds = new LinkedHashMap<>();
        for (LayoutDoor d : doors) ds.put(Triple.of(d.axis, d.a.x, d.a.z), d);
        this.doorBySlot = ds;
    }

    /** The room that covers world tile (tx, tz); null for an empty tile. */
    public LayoutRoom roomAt(int tx, int tz) { return tileRoom.get(new Tile(tx, tz)); }

    /** The door record for slot (axis, tx, tz); null when the slot separates a room from itself or has no record. */
    public LayoutDoor doorAt(char axis, int tx, int tz) { return doorBySlot.get(Triple.of(axis, tx, tz)); }

    /** World (x, z) of a slot's centre. */
    public Pair<Integer, Integer> doorSpot(char axis, int tx, int tz) { return DungeonFrame.doorSpot(axis, tx, tz); }

    public LayoutRoom entrance() {
        for (LayoutRoom r : rooms) if (r.type.equals("entrance")) return r;
        return null;
    }

    public LayoutRoom blood() {
        for (LayoutRoom r : rooms) if (r.type.equals("blood")) return r;
        return null;
    }

    public LayoutRoom fairy() {
        for (LayoutRoom r : rooms) if (r.type.equals("fairy")) return r;
        return null;
    }

    /** The default pool: playable, not a duplicate, F7. */
    public boolean inDefaultPool() { return Boolean.TRUE.equals(playable) && dupOf == null && floor.equals("F7"); }
}
