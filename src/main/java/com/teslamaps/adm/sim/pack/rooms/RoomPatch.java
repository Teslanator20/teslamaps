// Ported from ADM's p3sim/pack/rooms/RoomVolume.kt
package com.teslamaps.adm.sim.pack.rooms;

import java.util.List;

/** A sparse set of cell changes over a parent volume's box: {@code indices} are linear cell indexes, strictly increasing. */
public class RoomPatch {
    public final int w;
    public final int d;
    public final int h;
    public final int y0;
    public final List<String> palette;
    public final int[] indices;
    public final int[] values;

    public RoomPatch(int w, int d, int h, int y0, List<String> palette, int[] indices, int[] values) {
        this.w = w; this.d = d; this.h = h; this.y0 = y0; this.palette = palette; this.indices = indices; this.values = values;
    }
}
