// Ported from ADM's p3sim/pack/rooms/RoomPack.kt
package com.teslamaps.adm.sim.pack.rooms;

import java.util.Objects;

/** Where a design's blocks came from (informational). */
public final class RoomSource {
    public final String blocks;
    public final String file;
    public final String ref;
    public final Integer captureRotation;

    public RoomSource(String blocks, String file, String ref, Integer captureRotation) {
        this.blocks = blocks; this.file = file; this.ref = ref; this.captureRotation = captureRotation;
    }

    @Override public boolean equals(Object o) {
        return o instanceof RoomSource s && Objects.equals(blocks, s.blocks) && Objects.equals(file, s.file) && Objects.equals(ref, s.ref)
                && Objects.equals(captureRotation, s.captureRotation);
    }
    @Override public int hashCode() { return Objects.hash(blocks, file, ref, captureRotation); }
    @Override public String toString() { return "RoomSource(blocks=" + blocks + ", file=" + file + ", ref=" + ref + ", captureRotation=" + captureRotation + ")"; }
}
