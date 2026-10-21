// Ported from ADM's p3sim/pack/rooms/RoomPack.kt
package com.teslamaps.adm.sim.pack.rooms;

import java.util.Objects;

/** Where a blob sits in {@code rooms.blocks.bin} (the index's {@code vol}). */
public final class BlobRef {
    public final String kind;
    public final long off;
    public final int len;
    public final String crc32;
    public final int nonAir;

    public BlobRef(String kind, long off, int len, String crc32, int nonAir) {
        this.kind = kind; this.off = off; this.len = len; this.crc32 = crc32; this.nonAir = nonAir;
    }

    @Override public boolean equals(Object o) {
        return o instanceof BlobRef b && Objects.equals(kind, b.kind) && off == b.off && len == b.len && Objects.equals(crc32, b.crc32) && nonAir == b.nonAir;
    }
    @Override public int hashCode() { return Objects.hash(kind, off, len, crc32, nonAir); }
    @Override public String toString() { return "BlobRef(kind=" + kind + ", off=" + off + ", len=" + len + ", crc32=" + crc32 + ", nonAir=" + nonAir + ")"; }
}
