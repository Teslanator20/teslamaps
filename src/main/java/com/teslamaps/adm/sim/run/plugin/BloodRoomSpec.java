// Ported from ADM's p3sim/run/plugin/BloodCamp.kt
package com.teslamaps.adm.sim.run.plugin;

import java.util.List;

/** The blood room as the public side built it, in WORLD coordinates. May gain members. */
public interface BloodRoomSpec {
    int roomIndex();

    /** The design id and build key placed. */
    String designId();

    String buildKey();

    /** World centre of the blood door. */
    double doorX();

    double doorY();

    double doorZ();

    /** Where the Watcher stands (floor height; he hangs 4 higher). */
    double watcherX();

    double watcherY();

    double watcherZ();

    /** Wall heads in room order; a stand at (x + 0.5, y + 0.75, z + 0.5). */
    List<HeadSlot> heads();

    /** Mob spawn cells (feet blocks, world). */
    List<double[]> mobCells();
}
