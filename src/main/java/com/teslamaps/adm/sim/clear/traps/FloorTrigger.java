package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/FallingFloor.kt

/** What sets a FallingFloor off. */
public interface FloorTrigger {
    /** A human stands over any cell of the floor. */
    final class StandOn implements FloorTrigger {
        public static final StandOn INSTANCE = new StandOn();

        private StandOn() {}
    }

    /** A human touches the tripwire line with this index (in the room's RoomHazards.lines). */
    final class StringTouched implements FloorTrigger {
        public final int line;

        public StringTouched(int line) { this.line = line; }
    }
}
