// Ported from ADM's p3sim/clear/secrets/RedstoneKeys.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.pack.Pos;

public class RevealChestSpec {
    public final String id;
    public final Pos pos;
    public final String facing;
    public final boolean counts;

    public RevealChestSpec(String id, Pos pos, String facing, boolean counts) {
        this.id = id;
        this.pos = pos;
        this.facing = facing;
        this.counts = counts;
    }
}
