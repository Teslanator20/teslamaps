// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;
import java.util.UUID;

public final class PuzzleSolved implements RunEvent {
    public final int t;
    public final int puzzle;
    public final int room;
    public final UUID player;

    public PuzzleSolved(int t, int puzzle, int room, UUID player) {
        this.t = t;
        this.puzzle = puzzle;
        this.room = room;
        this.player = player;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof PuzzleSolved x && Objects.equals(t, x.t) && Objects.equals(puzzle, x.puzzle) && Objects.equals(room, x.room) && Objects.equals(player, x.player);
    }
    @Override public int hashCode() { return Objects.hash(t, puzzle, room, player); }
    @Override public String toString() { return "PuzzleSolved(" + "t=" + t + ", " + "puzzle=" + puzzle + ", " + "room=" + room + ", " + "player=" + player + ")"; }
}
