// Ported from ADM's p3sim/clear/state/StateEffects.kt
package com.teslamaps.adm.sim.clear.state;

import com.teslamaps.adm.sim.run.KeyKind;
import com.teslamaps.adm.sim.run.PlacedDoor;
import java.util.UUID;

public interface StateEffects {
    void chat(String text, UUID to);

    default void chat(String text) { chat(text, null); }

    void sound(Cue cue, UUID to);

    default void sound(Cue cue) { sound(cue, null); }

    void setBody(PlacedDoor door, BodyBlocks blocks, boolean onlyFirst);

    default void setBody(PlacedDoor door, BodyBlocks blocks) { setBody(door, blocks, false); }

    default void spawnDoorDebris(PlacedDoor door) {}

    double spawnStand(int id, KeyKind kind, double x, double z, double floorGuessY);

    void removeStand(int id);

    void clearStands();

    default void tick() {}

    default void reset() {}
}
