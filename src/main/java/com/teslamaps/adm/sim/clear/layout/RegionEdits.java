package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/RegionEdits.kt

import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import com.teslamaps.adm.sim.pack.rooms.RoomBlockEntity;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RegionEdits {
    private RegionEdits() {}

    private static final String AIR = "minecraft:air";

    public static List<Edit> markerRemovals(List<RoomInstance> rooms) {
        List<Edit> out = new ArrayList<>();
        for (RoomInstance r : rooms) for (RoomBlockEntity b : r.design.blockEntities) {
            if (!b.marker) continue;
            if (!r.volume.contains(b.pos.x, b.pos.y, b.pos.z) || r.volume.isAir(b.pos.x, b.pos.y, b.pos.z)) continue;
            // a tile's centre column is what Odin hashes: never changed
            if (b.pos.x % 32 == 15 && b.pos.z % 32 == 15) continue;
            WorldPos w = r.placement.toWorld(b.pos.x, b.pos.y, b.pos.z);
            out.add(new Edit(w.x, w.y, w.z, AIR));
        }
        return out;
    }

    public static final String CLAY = "minecraft:blue_terracotta";

    public static List<Edit> fairyMarkers(List<RoomInstance> rooms) {
        List<Edit> out = new ArrayList<>();
        for (RoomInstance r : rooms) {
            if (!r.design.type.equalsIgnoreCase("fairy") || r.design.roofY <= 0) continue;
            WorldPos w = r.placement.toWorld(0, r.design.roofY, 0);
            out.add(new Edit(w.x, w.y, w.z, CLAY));
        }
        return out;
    }

    public static List<Edit> build(List<RoomInstance> rooms, Map<Slot, DoorKind> doors) { return build(rooms, doors, 0L); }

    public static List<Edit> build(List<RoomInstance> rooms, Map<Slot, DoorKind> doors, long seed) {
        List<Edit> out = new ArrayList<>(markerRemovals(rooms));
        out.addAll(fairyMarkers(rooms));
        out.addAll(DoorPlanner.plan(rooms, doors, seed));
        return out;
    }
}
