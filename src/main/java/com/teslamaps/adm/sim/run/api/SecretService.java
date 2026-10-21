// Ported from ADM's p3sim/run/api/SecretsApi.kt
package com.teslamaps.adm.sim.run.api;

import com.teslamaps.adm.sim.run.BonusKind;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.SecretKind;
import java.util.UUID;

/** Implemented by SecretsModule. Server thread only. */
public interface SecretService {
    /** Credits a secret to the team (and by). False if already counted. */
    default boolean found(Run run, int room, SecretKind kind, UUID by, int x, int y, int z) { return false; }

    /** A teammate cleared room: counts every secret still missing for by. Returns how many. */
    default int creditRoom(Run run, int room, UUID by) { return 0; }

    /** A crypt tomb at (x, y, z) was destroyed. */
    default void cryptDestroyed(Run run, int room, UUID by, int x, int y, int z) {}

    /** A bonus mob died and its point counts. */
    default void bonus(Run run, BonusKind kind, UUID by) {}

    /** The secret maximum the bars print for this room. */
    default int roomMax(Run run, int room) {
        RoomRecord r = run.state.room(room);
        return r != null ? r.secretsMax() : 0;
    }

    /** A reason when the Dungeonbreaker may NOT break (x, y, z), else null. */
    default String breakerRefusal(Run run, int x, int y, int z) { return null; }

    /** A Dungeonbreaker broke (x, y, z). */
    default void onBreakerBreak(Run run, UUID by, int x, int y, int z) {}

    /** A Superboom went off at (x, y, z). Returns how many blocks it destroyed. */
    default int onSuperboom(Run run, UUID by, int x, int y, int z) { return 0; }
}
