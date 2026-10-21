// Ported from ADM's p3sim/clear/bots/PackRoomAreas.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.RoomDesign;
import com.teslamaps.adm.sim.pack.rooms.RoomPack;
import com.teslamaps.adm.sim.pack.rooms.RoomVolume;
import com.teslamaps.adm.sim.run.PlacedRoom;
import java.util.IdentityHashMap;

/** The floors of a dungeon's rooms, read from the rooms pack's blocks the first time a room is asked for. Thread safe. */
public class PackRoomAreas implements RoomAreas {
    private final RoomPack pack;

    /** By the placed room itself (not its index). */
    private final IdentityHashMap<PlacedRoom, WalkArea> cache = new IdentityHashMap<>();

    public PackRoomAreas(RoomPack pack) { this.pack = pack; }

    @Override
    public WalkArea of(PlacedRoom room) {
        synchronized (cache) {
            if (cache.containsKey(room)) return cache.get(room);
            WalkArea area;
            try { area = build(room); } catch (Exception e) { area = null; }
            cache.put(room, area);
            return area;
        }
    }

    private WalkArea build(PlacedRoom room) {
        RoomDesign design = room.design;
        if (design == null) return null;
        Placement placement = room.placement;
        if (placement == null) return null;
        if (pack == null) return null;
        RoomVolume volume = pack.volumeFor(design, room.variantId);
        if (volume == null) return null;
        RoomGrid grid = RoomGrids.fromVolume(volume, placement);
        return grid.openCount() >= MIN_OPEN_COLUMNS ? new GridArea(grid) : null;
    }

    /** The grid of a room, for the body's wall check and tests; null when it has none. */
    public RoomGrid gridOf(PlacedRoom room) { return of(room) instanceof GridArea g ? g.grid : null; }

    /** A floor smaller than this many columns is not worth wandering on. */
    public static final int MIN_OPEN_COLUMNS = 100;
}
