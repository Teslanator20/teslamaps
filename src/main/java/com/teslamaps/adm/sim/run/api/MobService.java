// Ported from ADM's p3sim/run/api/MobApi.kt
package com.teslamaps.adm.sim.run.api;

import com.teslamaps.adm.sim.run.Run;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** The mob registry and spawner. Implemented by MobsModule. Server thread only. */
public interface MobService {
    /** Rolls the room's table and spawns its mobs. */
    default List<MobRecord> spawnRoom(Run run, int roomIndex) { return List.of(); }

    /** One mob by spec; null when it could not be made. */
    default MobRecord spawn(Run run, MobSpec spec) { return null; }

    default MobRecord mob(UUID id) { return null; }

    default List<MobRecord> inRoom(int roomIndex) { return List.of(); }

    default Collection<MobRecord> all() { return List.of(); }

    /** Mobs of the room that must still die for WHITE. */
    default int requiredAlive(int roomIndex) { return 0; }

    /** Sets a mob's HP: nametag, MobDamaged, and at <= 0 the kill (MobKilled, removal, key rule). Null if unknown. */
    default MobRecord setHp(Run run, UUID id, double newHp, UUID by) { return null; }

    /** The record of the mob whose body has this entity id, or null. */
    default MobRecord recordOfEntity(int entityId) { return null; }

    /** A sleeping Fels cannot be hurt until a player body wakes it. */
    default boolean isAsleep(UUID id) { return false; }

    /** Removes a mob without a kill. */
    default void remove(Run run, UUID id) {}

    /** Removes every mob. */
    default void clearAll(Run run) {}
}
