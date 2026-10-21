// Ported from ADM's p3sim/run/api/BotApi.kt
package com.teslamaps.adm.sim.run.api;

import com.teslamaps.adm.sim.run.Run;
import java.util.List;
import java.util.UUID;

/** Teammate bots in the clear. Implemented by BotsModule. Server thread only. */
public interface BotService {
    /** Empty when bots are off. */
    default List<UUID> botIds(Run run) { return List.of(); }

    /** The member the ClearPlan assigns the room to, or null. */
    default UUID ownerOfRoom(Run run, int roomIndex) { return null; }

    /** Members other than viewer in the viewer's leap order (bots and real players). */
    default List<LeapTarget> leapTargets(Run run, UUID viewer) { return List.of(); }

    /** Asks for a re-plan; the module decides when. */
    default void requestReplan(Run run, String reason) {}
}
