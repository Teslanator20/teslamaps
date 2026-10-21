// Ported from ADM's p3sim/pack/rooms/RoomPack.kt
package com.teslamaps.adm.sim.pack.rooms;

import java.util.Objects;

/** {@code slab} is {@code [x0,y0,z0,x1,y1,z1]} with absolute y. */
public final class RoomCrusher {
    public final String id;
    public final int[] slab;
    public final String axis;
    public final int sign;
    public final int sweep;
    public final String material;

    public RoomCrusher(String id, int[] slab, String axis, int sign, int sweep, String material) {
        this.id = id; this.slab = slab; this.axis = axis; this.sign = sign; this.sweep = sweep; this.material = material;
    }

    @Override public boolean equals(Object o) {
        return o instanceof RoomCrusher c && Objects.equals(id, c.id) && slab == c.slab && Objects.equals(axis, c.axis) && sign == c.sign
                && sweep == c.sweep && Objects.equals(material, c.material);
    }
    @Override public int hashCode() { return Objects.hash(id, slab, axis, sign, sweep, material); }
    @Override public String toString() {
        return "RoomCrusher(id=" + id + ", slab=" + slab + ", axis=" + axis + ", sign=" + sign + ", sweep=" + sweep + ", material=" + material + ")";
    }
}
