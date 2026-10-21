// Ported from ADM's p3sim/pack/rooms/RoomVolume.kt
package com.teslamaps.adm.sim.pack.rooms;

import com.teslamaps.adm.sim.pack.PackFormatException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * One decoded block volume of a room design, in the canonical room frame: {@code x} east 0..W-1, {@code z} south
 * 0..D-1, {@code y} the ABSOLUTE world y from [y0] up. Cells hold an index into [palette]; entry 0 is {@code minecraft:air}.
 */
public class RoomVolume {
    public final int w;
    public final int d;
    public final int h;
    public final int y0;
    public final List<String> palette;
    public final short[] cells;

    public static final String AIR = "minecraft:air";

    public RoomVolume(int w, int d, int h, int y0, List<String> palette, short[] cells) {
        this.w = w;
        this.d = d;
        this.h = h;
        this.y0 = y0;
        this.palette = palette;
        this.cells = cells;
        if (cells.length != w * d * h) throw new IllegalArgumentException("volume is " + w + "x" + d + "x" + h + " but has " + cells.length + " cells");
    }

    /** Linear index of canonical cell (x, y, z): x fastest, then z, then y. */
    public int index(int x, int y, int z) { return ((y - y0) * d + z) * w + x; }

    public boolean contains(int x, int y, int z) { return x >= 0 && x < w && z >= 0 && z < d && y >= y0 && y < y0 + h; }

    /** The palette index at a cell; 0 (air) outside the box. */
    public int paletteIndexAt(int x, int y, int z) { return contains(x, y, z) ? cells[index(x, y, z)] & 0xFFFF : 0; }

    /** The state string at a cell; {@code minecraft:air} outside the box. */
    public String stateAt(int x, int y, int z) { return palette.get(paletteIndexAt(x, y, z)); }

    public boolean isAir(int x, int y, int z) { return paletteIndexAt(x, y, z) == 0 || palette.get(paletteIndexAt(x, y, z)).equals(AIR); }

    /** How many cells are not air. */
    public int nonAirCount() {
        boolean[] air = new boolean[palette.size()];
        for (int i = 0; i < air.length; i++) air[i] = palette.get(i).equals(AIR);
        int n = 0;
        for (short c : cells) if (!air[c & 0xFFFF]) n++;
        return n;
    }

    /** A copy with [patch] applied. Cells the patch sets to {@code minecraft:air} are cleared. */
    public RoomVolume withPatch(RoomPatch patch) {
        if (patch.w != w || patch.d != d || patch.h != h || patch.y0 != y0) {
            throw new PackFormatException("patch box " + patch.w + "x" + patch.d + "x" + patch.h + "@" + patch.y0 + " does not match its volume "
                    + w + "x" + d + "x" + h + "@" + y0);
        }
        ArrayList<String> pal = new ArrayList<>(palette);
        HashMap<String, Integer> map = new HashMap<>();
        for (int i = 0; i < pal.size(); i++) map.putIfAbsent(pal.get(i), i);
        int[] remap = new int[patch.palette.size()];
        for (int i = 0; i < remap.length; i++) {
            String s = patch.palette.get(i);
            Integer got = map.get(s);
            if (got == null) {
                pal.add(s);
                got = pal.size() - 1;
                map.put(s, got);
            }
            remap[i] = got;
        }
        if (pal.size() > 0xFFFF) throw new PackFormatException("patched palette has " + pal.size() + " entries");
        short[] out = cells.clone();
        for (int k = 0; k < patch.indices.length; k++) out[patch.indices[k]] = (short) remap[patch.values[k]];
        return new RoomVolume(w, d, h, y0, pal, out);
    }
}
