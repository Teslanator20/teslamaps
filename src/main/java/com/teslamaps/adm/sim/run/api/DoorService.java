// Ported from ADM's p3sim/run/api/DoorApi.kt
package com.teslamaps.adm.sim.run.api;

import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.KeyKind;
import com.teslamaps.adm.sim.run.Run;
import java.util.List;
import java.util.UUID;

/** Doors, keys and room check states. Implemented by DoorsModule. Server thread only. */
public interface DoorService {
    /** The key plan of a dungeon (pure). */
    default KeyPlan keyPlan(DungeonPlan plan) { return new KeyPlan(List.of(), null); }

    /** The key this room drops when its last required mob dies, or null. */
    default KeyKind keyForRoom(Run run, int roomIndex) { return null; }

    /** Drops the key stand at (x, z) in roomIndex. Posts KeyDropped. */
    default void dropKey(Run run, int roomIndex, double x, double z) {}

    /** A replay: by takes a key of kind on the recorded tick. False when there is no key to give. */
    default boolean pickUpKey(Run run, UUID by, KeyKind kind, Integer roomIndex, double x, double z) { return false; }

    /** A right click on a door's blocks: opens it, or says there is no key. */
    default DoorOpenResult tryOpen(Run run, int door, UUID by) { return DoorOpenResult.NOT_A_DOOR; }

    /** A left click on a block; on a closed key door it is a use of the door. True when the door stays shut. */
    default boolean touchBlock(Run run, int x, int y, int z, UUID by) { return false; }

    /** The door whose 3x4x3 volume contains the block, or null. */
    default Integer doorAtBlock(Run run, int x, int y, int z) { return null; }
}
