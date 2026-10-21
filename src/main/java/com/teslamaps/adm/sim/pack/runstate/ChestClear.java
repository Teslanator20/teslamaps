// Ported from ADM's p3sim/pack/runstate/RunStatePack.kt
package com.teslamaps.adm.sim.pack.runstate;

import com.teslamaps.adm.sim.pack.Pos;

/**
 * The one chest whose opening finishes a trap room. [secret] is the rooms-pack secret id and [cell] its canonical (x, y, z);
 * [afterOpen] is the measured gap from the first open to the white mark.
 */
public class ChestClear {
    public final String room;
    public final String secret;
    public final Pos cell;
    public final Dist afterOpen;
    public final int visits;

    public ChestClear(String room, String secret, Pos cell, Dist afterOpen, int visits) {
        this.room = room; this.secret = secret; this.cell = cell; this.afterOpen = afterOpen; this.visits = visits;
    }
}
