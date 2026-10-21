// Ported from ADM's p3sim/clear/secrets/RedstoneKeys.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.pack.Pos;

public class HeadCell {
    public final Pos pos;
    public final String wallFacing;
    public final int rotation;

    public HeadCell(Pos pos, String wallFacing, int rotation) {
        this.pos = pos;
        this.wallFacing = wallFacing;
        this.rotation = rotation;
    }
}
