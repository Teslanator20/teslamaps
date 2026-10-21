package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/RoomHazards.kt

import java.util.UUID;

/** line is the index of the tripwire line that fired (-1 for anything else). */
public class HazardHit {
    public final UUID player;
    public final HazardKind kind;
    public final int line;

    public HazardHit(UUID player, HazardKind kind, int line) {
        this.player = player;
        this.kind = kind;
        this.line = line;
    }

    public HazardHit(UUID player, HazardKind kind) { this(player, kind, -1); }
}
