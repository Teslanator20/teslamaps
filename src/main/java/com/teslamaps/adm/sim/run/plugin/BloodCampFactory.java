// Ported from ADM's p3sim/run/plugin/BloodCamp.kt
package com.teslamaps.adm.sim.run.plugin;

/** A plug-in's way to make a camp. */
public interface BloodCampFactory {
    /** False falls through to the next plug-in, then the placeholder. */
    default boolean supports(BloodRoomSpec room) { return true; }

    /** Called when the blood door opens; host is valid until the camp is finished. */
    BloodCamp create(BloodCampHost host);
}
