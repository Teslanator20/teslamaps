package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/ProbeReport.kt

import com.teslamaps.adm.sim.util.Pair;
import java.util.Objects;
import java.util.Set;

public final class SeenRoom {
    public final String name;
    public final String rotation;
    public final Set<Pair<Integer, Integer>> tiles;
    public final String type;
    public final String shape;

    public SeenRoom(String name, String rotation, Set<Pair<Integer, Integer>> tiles, String type, String shape) {
        this.name = name;
        this.rotation = rotation;
        this.tiles = tiles;
        this.type = type;
        this.shape = shape;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SeenRoom s && Objects.equals(name, s.name) && Objects.equals(rotation, s.rotation) && Objects.equals(tiles, s.tiles)
                && Objects.equals(type, s.type) && Objects.equals(shape, s.shape);
    }

    @Override
    public int hashCode() { return Objects.hash(name, rotation, tiles, type, shape); }

    @Override
    public String toString() { return "SeenRoom(name=" + name + ", rotation=" + rotation + ", tiles=" + tiles + ", type=" + type + ", shape=" + shape + ")"; }
}
