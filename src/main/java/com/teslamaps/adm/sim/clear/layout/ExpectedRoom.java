package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/ProbeReport.kt

import com.teslamaps.adm.sim.util.Pair;
import java.util.Objects;
import java.util.Set;

public final class ExpectedRoom {
    public final String name;
    public final String rotation;
    public final Set<Pair<Integer, Integer>> tiles;

    public ExpectedRoom(String name, String rotation, Set<Pair<Integer, Integer>> tiles) {
        this.name = name;
        this.rotation = rotation;
        this.tiles = tiles;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ExpectedRoom e && Objects.equals(name, e.name) && Objects.equals(rotation, e.rotation) && Objects.equals(tiles, e.tiles);
    }

    @Override
    public int hashCode() { return Objects.hash(name, rotation, tiles); }

    @Override
    public String toString() { return "ExpectedRoom(name=" + name + ", rotation=" + rotation + ", tiles=" + tiles + ")"; }
}
