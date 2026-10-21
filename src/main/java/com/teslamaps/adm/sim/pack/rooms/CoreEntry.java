// Ported from ADM's p3sim/pack/rooms/RoomPack.kt
package com.teslamaps.adm.sim.pack.rooms;

import java.util.Objects;

/** One row of the pack's {@code cores} table: an Odin core to a design and variant. */
public final class CoreEntry {
    public final int core;
    public final String design;
    public final String variant;

    public CoreEntry(int core, String design, String variant) { this.core = core; this.design = design; this.variant = variant; }

    @Override public boolean equals(Object o) {
        return o instanceof CoreEntry c && core == c.core && Objects.equals(design, c.design) && Objects.equals(variant, c.variant);
    }
    @Override public int hashCode() { return Objects.hash(core, design, variant); }
    @Override public String toString() { return "CoreEntry(core=" + core + ", design=" + design + ", variant=" + variant + ")"; }
}
