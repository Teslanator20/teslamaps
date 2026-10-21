// Ported from ADM's p3sim/pack/rooms/Placement.kt
package com.teslamaps.adm.sim.pack.rooms;

import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.Rot;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.util.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where one room design stands in the world: the position law and the tile law, as pure functions. [rot] is the rotation
 * actually used (Fairy is always SOUTH); {@link #quarterTurns()} is how many clockwise quarter turns every block STATE must
 * also be rotated by.
 */
public class Placement {
    public final String shape;
    /** Canonical footprint: x extent and z extent in blocks. */
    public final int w;
    public final int d;
    public final Rot rot;
    /** The world tiles this room covers, sorted row-major. */
    public final List<Tile> worldTiles;
    /** World min corner of the placed box. */
    public final int originX;
    public final int originZ;

    private final Set<Tile> tileSet;
    private final int tx0;
    private final int tz0;

    private Placement(String shape, int w, int d, Rot rot, List<Tile> worldTiles, int originX, int originZ) {
        this.shape = shape;
        this.w = w;
        this.d = d;
        this.rot = rot;
        this.worldTiles = worldTiles;
        this.originX = originX;
        this.originZ = originZ;
        this.tileSet = new LinkedHashSet<>(worldTiles);
        int mx = Integer.MAX_VALUE, mz = Integer.MAX_VALUE;
        if (worldTiles.isEmpty()) throw new java.util.NoSuchElementException();
        for (Tile t : worldTiles) { mx = Math.min(mx, t.x); mz = Math.min(mz, t.z); }
        this.tx0 = mx;
        this.tz0 = mz;
    }

    /** The quarter turns to rotate each block state by. */
    public int quarterTurns() { return rot.q; }

    /** The placed box's size in the world: (w, d) for SOUTH and NORTH, swapped for WEST and EAST. */
    public int boxW() { return rot == Rot.SOUTH || rot == Rot.NORTH ? w : d; }
    public int boxD() { return rot == Rot.SOUTH || rot == Rot.NORTH ? d : w; }

    public List<Tile> tiles() { return worldTiles; }

    /** The world block of canonical cell (x, y, z); y is unchanged. */
    public WorldPos toWorld(int x, int y, int z) {
        int px, pz;
        switch (rot) {
            case SOUTH -> { px = x; pz = z; }
            case NORTH -> { px = w - 1 - x; pz = d - 1 - z; }
            case WEST -> { px = d - 1 - z; pz = x; }
            case EAST -> { px = z; pz = w - 1 - x; }
            default -> throw new IllegalStateException();
        }
        return new WorldPos(originX + px, y, originZ + pz);
    }

    /** The canonical cell (x, z) of a world column, or null when it is outside the placed box. */
    public Pair<Integer, Integer> toCanonical(int wx, int wz) {
        int px = wx - originX;
        int pz = wz - originZ;
        if (px < 0 || px >= boxW() || pz < 0 || pz >= boxD()) return null;
        return switch (rot) {
            case SOUTH -> Pair.of(px, pz);
            case NORTH -> Pair.of(w - 1 - px, d - 1 - pz);
            case WEST -> Pair.of(pz, d - 1 - px);
            case EAST -> Pair.of(w - 1 - pz, px);
        };
    }

    /** Whether world column (wx, wz) belongs to this room: in one of its tiles, or in the gap column/row between two of them. */
    public boolean ownsColumn(int wx, int wz) {
        for (int tx : axis(wx)) for (int tz : axis(wz)) if (!tileSet.contains(new Tile(tx, tz))) return false;
        return true;
    }

    private static List<Integer> axis(int v) {
        int t = Math.floorDiv(v - DungeonFrame.ORIGIN, DungeonFrame.PITCH);
        return v - DungeonFrame.tileMin(t) == DungeonFrame.TILE_SIZE ? List.of(t, t + 1) : List.of(t);
    }

    /** The world tile that canonical tile cell (ix, iz) lands on. */
    public Tile worldTileOf(int ix, int iz) { return placeTile(rot, ix, iz, nx(), nz(), tx0, tz0); }

    private int nx() { return canonicalSize(shape).first; }
    private int nz() { return canonicalSize(shape).second; }

    /** Canonical tile cells by shape. */
    public static List<Tile> canonicalTiles(String shape) {
        return switch (shape) {
            case "1x1" -> List.of(new Tile(0, 0));
            case "1x2" -> List.of(new Tile(0, 0), new Tile(1, 0));
            case "1x3" -> List.of(new Tile(0, 0), new Tile(1, 0), new Tile(2, 0));
            case "1x4" -> List.of(new Tile(0, 0), new Tile(1, 0), new Tile(2, 0), new Tile(3, 0));
            case "2x2" -> List.of(new Tile(0, 0), new Tile(1, 0), new Tile(0, 1), new Tile(1, 1));
            case "L" -> List.of(new Tile(0, 0), new Tile(0, 1), new Tile(1, 1));
            default -> throw new PlacementException("unknown room shape '" + shape + "'");
        };
    }

    /** (nx, nz): the tile counts along x and z of the canonical bounding box. */
    public static Pair<Integer, Integer> canonicalSize(String shape) {
        List<Tile> t = canonicalTiles(shape);
        int mx = Integer.MIN_VALUE, mz = Integer.MIN_VALUE;
        for (Tile x : t) { mx = Math.max(mx, x.x); mz = Math.max(mz, x.z); }
        return Pair.of(mx + 1, mz + 1);
    }

    /** Block extent of the canonical footprint for a shape: 31 per tile + 32 per extra tile along that axis. */
    public static Pair<Integer, Integer> footprint(String shape) {
        Pair<Integer, Integer> s = canonicalSize(shape);
        return Pair.of(31 + 32 * (s.first - 1), 31 + 32 * (s.second - 1));
    }

    /** Odin rotations the law accepts per shape. */
    public static List<Rot> legalRotations(String shape) {
        return switch (shape) {
            case "1x1", "L" -> List.of(Rot.values());
            case "1x2", "1x3", "1x4" -> List.of(Rot.SOUTH, Rot.WEST);
            case "2x2" -> List.of(Rot.SOUTH);
            default -> List.of();
        };
    }

    private static final Comparator<Tile> ROW_MAJOR = Comparator.<Tile>comparingInt(t -> t.z).thenComparingInt(t -> t.x);

    /** The world tiles a design of [shape] covers when placed [rot] with its tile box starting at (tx0, tz0), sorted row-major. */
    public static List<Tile> tilesFor(String shape, Rot rot, int tx0, int tz0) {
        Pair<Integer, Integer> s = canonicalSize(shape);
        List<Tile> out = new ArrayList<>();
        for (Tile t : canonicalTiles(shape)) out.add(placeTile(rot, t.x, t.z, s.first, s.second, tx0, tz0));
        out.sort(ROW_MAJOR);
        return out;
    }

    private static Tile placeTile(Rot rot, int ix, int iz, int nx, int nz, int tx0, int tz0) {
        int a, b;
        switch (rot) {
            case SOUTH -> { a = ix; b = iz; }
            case NORTH -> { a = nx - 1 - ix; b = nz - 1 - iz; }
            case WEST -> { a = nz - 1 - iz; b = ix; }
            case EAST -> { a = iz; b = nx - 1 - ix; }
            default -> throw new IllegalStateException();
        }
        return new Tile(tx0 + a, tz0 + b);
    }

    public static Placement of(String shape, int w, int d, Rot rotWord, List<Tile> worldTiles) {
        return of(shape, w, d, rotWord, worldTiles, false);
    }

    /**
     * The placement of a design of [shape] (footprint w x d) with the layout's rotation word and world tiles. [fairy]: always
     * SOUTH. Throws {@link PlacementException} when the rotation is not one that occurs for the shape, or the tile law fails.
     */
    public static Placement of(String shape, int w, int d, Rot rotWord, List<Tile> worldTiles, boolean fairy) {
        Rot rot = fairy ? Rot.SOUTH : rotWord;
        if (!legalRotations(shape).contains(rot)) throw new PlacementException("a " + shape + " room is never placed " + rot);
        Pair<Integer, Integer> fp = footprint(shape);
        int fw = fp.first, fd = fp.second;
        if (w != fw || d != fd) throw new PlacementException("a " + shape + " room is " + fw + "x" + fd + " blocks, but the design says " + w + "x" + d);
        if (worldTiles.isEmpty()) throw new PlacementException("a room needs at least one tile");
        List<Tile> sorted = new ArrayList<>(worldTiles);
        sorted.sort(ROW_MAJOR);
        int tx0 = Integer.MAX_VALUE, tz0 = Integer.MAX_VALUE;
        for (Tile t : sorted) { tx0 = Math.min(tx0, t.x); tz0 = Math.min(tz0, t.z); }
        Pair<Integer, Integer> s = canonicalSize(shape);
        LinkedHashSet<Tile> image = new LinkedHashSet<>();
        for (Tile t : canonicalTiles(shape)) image.add(placeTile(rot, t.x, t.z, s.first, s.second, tx0, tz0));
        if (!image.equals(new HashSet<>(sorted)) || sorted.size() != image.size()) {
            throw new PlacementException("a " + shape + " room placed " + rot + " covers " + image + ", not the layout's tiles " + sorted);
        }
        for (Tile t : sorted) if (t.x < 0 || t.x > 5 || t.z < 0 || t.z > 5) throw new PlacementException("tile " + t.x + "," + t.z + " is outside the 6x6 grid");
        return new Placement(shape, w, d, rot, sorted, -200 + 32 * tx0, -200 + 32 * tz0);
    }
}
