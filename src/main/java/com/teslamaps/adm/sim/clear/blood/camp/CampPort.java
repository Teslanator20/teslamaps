// Ported from ADM's p3sim/clear/blood/camp/CampEngine.kt
package com.teslamaps.adm.sim.clear.blood.camp;

/** What the engine asks of the world. */
public interface CampPort {
    /** Puts the mob in the room now; null when it could not be made. */
    String spawn(Landing landing);

    default void headThrown(WallStation station) {}

    default void flyHead(WallStation station, Vec3 end, int ticks) {}

    default void headArrived(WallStation station) {}

    default void watcherAt(double x, double y, double z, float yaw) {}

    default void say(CampMech.Line line) {}

    default void killsCounted(int total) {}

    default void log(String text) {}
}
