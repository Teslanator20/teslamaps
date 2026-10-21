// Ported from ADM's p3sim/pack/layouts/LayoutPack.kt
package com.teslamaps.adm.sim.pack.layouts;

import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.util.Pair;

import java.util.Objects;

/** {@code a} is the west (E) or north (S) tile, {@code b} the other; {@code (axis, a)} is the slot id. */
public final class LayoutDoor {
    public final char axis;
    public final Tile a;
    public final Tile b;
    public final int ra;
    public final int rb;
    public final String read;
    public final DoorKind kind;
    public final boolean fix;
    /** Tick (relative to {@code t0}) the door started falling in the recorded run; null for plain doors and doors never seen to fall. */
    public final Integer tOpen;

    public LayoutDoor(char axis, Tile a, Tile b, int ra, int rb, String read, DoorKind kind, boolean fix, Integer tOpen) {
        this.axis = axis;
        this.a = a;
        this.b = b;
        this.ra = ra;
        this.rb = rb;
        this.read = read;
        this.kind = kind;
        this.fix = fix;
        this.tOpen = tOpen;
    }

    /** World (x, z) of the door's centre. */
    public Pair<Integer, Integer> spot() { return DungeonFrame.doorSpot(axis, a.x, a.z); }

    @Override public boolean equals(Object o) {
        return o instanceof LayoutDoor d && axis == d.axis && Objects.equals(a, d.a) && Objects.equals(b, d.b) && ra == d.ra && rb == d.rb
                && Objects.equals(read, d.read) && kind == d.kind && fix == d.fix && Objects.equals(tOpen, d.tOpen);
    }

    @Override public int hashCode() { return Objects.hash(axis, a, b, ra, rb, read, kind, fix, tOpen); }

    @Override public String toString() {
        return "LayoutDoor(axis=" + axis + ", a=" + a + ", b=" + b + ", ra=" + ra + ", rb=" + rb + ", read=" + read + ", kind=" + kind
                + ", fix=" + fix + ", tOpen=" + tOpen + ")";
    }
}
