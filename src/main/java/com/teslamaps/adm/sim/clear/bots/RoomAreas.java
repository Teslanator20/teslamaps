// Ported from ADM's p3sim/clear/bots/RoomWalk.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.run.PlacedRoom;

/** What the planner asks for a room's floor. Null: the room has no block data. */
@FunctionalInterface
public interface RoomAreas {
    WalkArea of(PlacedRoom room);
}
