// Ported from ADM's p3sim/run/RunState.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.pack.DungeonFrame;

/** Opened / Completed Rooms in tile units, entrance excluded. Doors/state module. */
public class TileCounters {
    public final int total = DungeonFrame.TILE_COUNT;
    public int opened = 0;
    public int completed = 0;

    /** round(100 * completed / 36) */
    public int clearedPct() { return (int) Math.round(100.0 * completed / total); }
}
