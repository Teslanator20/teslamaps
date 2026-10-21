// Ported from ADM's p3sim/clear/bots/PlanExecutor.kt
package com.teslamaps.adm.sim.clear.bots;

/** What the executor needs from the world to do a plan's acts. Every call is idempotent. */
public interface BotWorld {
    /** Opens [door] for [seat]. True when the door is open or falling, false when it cannot be opened yet (no key). */
    boolean openDoor(int seat, int door);

    /** Brings the room's required mobs to at least [fraction] (0..1) killed by [seat]. True when none is left alive. */
    boolean killRequired(int seat, int room, double fraction);

    /** Brings the room's secrets to at least [fraction] (0..1) found by [seat]. True when every secret of the room is found. */
    boolean findSecrets(int seat, int room, double fraction);

    /** Solves the room's puzzle. True when it is solved (or there is none). */
    boolean solvePuzzle(int seat, int room);

    /** Whether somebody else already finished the room's mobs (white) or everything (green). */
    boolean roomDone(int room, boolean green);

    /** A human stands in the room. */
    boolean humanInside(int room);
}
