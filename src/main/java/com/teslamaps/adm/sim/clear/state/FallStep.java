// Ported from ADM's p3sim/clear/state/DoorFalls.kt
package com.teslamaps.adm.sim.clear.state;

import java.util.Objects;

public final class FallStep {
    public final int door;
    public final BodyBlocks blocks;
    public final boolean onlyFirst;

    public FallStep(int door, BodyBlocks blocks, boolean onlyFirst) {
        this.door = door;
        this.blocks = blocks;
        this.onlyFirst = onlyFirst;
    }

    public FallStep(int door, BodyBlocks blocks) { this(door, blocks, false); }

    @Override public boolean equals(Object o) {
        return o instanceof FallStep f && door == f.door && blocks == f.blocks && onlyFirst == f.onlyFirst;
    }
    @Override public int hashCode() { return Objects.hash(door, blocks, onlyFirst); }
    @Override public String toString() { return "FallStep(door=" + door + ", blocks=" + blocks + ", onlyFirst=" + onlyFirst + ")"; }
}
