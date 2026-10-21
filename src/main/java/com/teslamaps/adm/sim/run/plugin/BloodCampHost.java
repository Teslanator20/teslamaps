// Ported from ADM's p3sim/run/plugin/BloodCamp.kt
package com.teslamaps.adm.sim.run.plugin;

import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.api.MobSpec;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.List;
import java.util.UUID;

/** What a camp can ask of the public side. Implemented by the blood module. Server thread only. */
public interface BloodCampHost {
    Run run();

    BloodRoomSpec room();

    /** Seeded from the run's seed. */
    KRandom random();

    /** Server ticks since the blood door opened. */
    int ticksSinceOpen();

    /** Spawns a mob through the mob module; null if it could not. */
    UUID spawnMob(MobSpec spec);

    /** Without a kill (no key, no score). */
    void removeMob(UUID id);

    List<UUID> mobsAlive();

    /** Players and bots inside the blood room. */
    List<UUID> partyInRoom();

    /** What a wall head shows: a texture id (64 hex) or a table role word. */
    void setHead(int slot, String textureKey);

    /** Takes the head off the wall. */
    default void clearHead(int slot) {}

    /** The head flies to (x, y, z) over ticks, spinning, gone on arrival. Default: off the wall now. */
    default void flyHead(int slot, double x, double y, double z, int ticks) { clearHead(slot); }

    /** Default: he stays where he stands. */
    default void moveWatcher(double x, double y, double z) {}

    /** Facing yaw (0 south, 90 west). Default: ignore the yaw. */
    default void moveWatcher(double x, double y, double z, float yaw) { moveWatcher(x, y, z); }

    /** The camp's counted kills; the fifteenth is his defeat. Default: nothing. */
    default void watcherKills(int kills) {}

    /** `[BOSS] The Watcher: ...` */
    void watcherSays(String line);

    /** A note in chat ("sim" voice). */
    void note(String text);
}
