package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/RoomHazards.kt

import java.util.UUID;

/** A player's feet position in the room's canonical frame. */
public class PlayerPos {
    public final UUID id;
    public final double x;
    public final double y;
    public final double z;

    public PlayerPos(UUID id, double x, double y, double z) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
