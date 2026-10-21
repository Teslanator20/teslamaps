// Ported from ADM's p3sim/pack/rooms/RoomPack.kt
package com.teslamaps.adm.sim.pack.rooms;

import java.util.Objects;

/** A variant is a patch over the base volume, with the Odin core it produces when known. */
public final class RoomVariant {
    public final String id;
    public final String kind;
    public final Integer core;
    public final String srcFile;
    public final int changedCells;
    public final BlobRef vol;

    public RoomVariant(String id, String kind, Integer core, String srcFile, int changedCells, BlobRef vol) {
        this.id = id; this.kind = kind; this.core = core; this.srcFile = srcFile; this.changedCells = changedCells; this.vol = vol;
    }

    @Override public boolean equals(Object o) {
        return o instanceof RoomVariant v && Objects.equals(id, v.id) && Objects.equals(kind, v.kind) && Objects.equals(core, v.core)
                && Objects.equals(srcFile, v.srcFile) && changedCells == v.changedCells && Objects.equals(vol, v.vol);
    }
    @Override public int hashCode() { return Objects.hash(id, kind, core, srcFile, changedCells, vol); }
    @Override public String toString() {
        return "RoomVariant(id=" + id + ", kind=" + kind + ", core=" + core + ", srcFile=" + srcFile + ", changedCells=" + changedCells + ", vol=" + vol + ")";
    }
}
