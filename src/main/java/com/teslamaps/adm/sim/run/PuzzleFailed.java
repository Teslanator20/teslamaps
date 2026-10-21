// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;
import java.util.UUID;

public final class PuzzleFailed implements RunEvent {
    public final int t;
    public final int puzzle;
    public final int room;
    public final UUID player;
    public final String reason;

    public PuzzleFailed(int t, int puzzle, int room, UUID player, String reason) {
        this.t = t;
        this.puzzle = puzzle;
        this.room = room;
        this.player = player;
        this.reason = reason;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof PuzzleFailed x && Objects.equals(t, x.t) && Objects.equals(puzzle, x.puzzle) && Objects.equals(room, x.room) && Objects.equals(player, x.player) && Objects.equals(reason, x.reason);
    }
    @Override public int hashCode() { return Objects.hash(t, puzzle, room, player, reason); }
    @Override public String toString() { return "PuzzleFailed(" + "t=" + t + ", " + "puzzle=" + puzzle + ", " + "room=" + room + ", " + "player=" + player + ", " + "reason=" + reason + ")"; }
}
