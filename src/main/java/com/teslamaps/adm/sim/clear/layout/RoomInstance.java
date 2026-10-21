package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/DoorPlanner.kt

import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.RoomDesign;
import com.teslamaps.adm.sim.pack.rooms.RoomVolume;
import com.teslamaps.adm.sim.util.Pair;

public class RoomInstance {
    public final RoomDesign design;
    public final RoomVolume volume;
    public final Placement placement;

    public RoomInstance(RoomDesign design, RoomVolume volume, Placement placement) {
        this.design = design;
        this.volume = volume;
        this.placement = placement;
    }

    public String stateAt(int x, int y, int z) {
        Pair<Integer, Integer> c = placement.toCanonical(x, z);
        if (c == null) return null;
        int cx = c.first;
        int cz = c.second;
        if (!volume.contains(cx, y, cz)) return null;
        String s = volume.stateAt(cx, y, cz);
        return s.equals(RoomVolume.AIR) ? null : s;
    }

    public boolean isSolid(int x, int y, int z) { return stateAt(x, y, z) != null; }
}
