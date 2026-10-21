// Ported from ADM's p3sim/clear/secrets/Levers.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.pack.Pos;
import java.util.List;

public class LeverInfo {
    public final int room;
    public final Pos pos;
    public final List<Pos> opens;
    public final List<String> chests;

    public LeverInfo(int room, Pos pos, List<Pos> opens, List<String> chests) {
        this.room = room;
        this.pos = pos;
        this.opens = opens;
        this.chests = chests;
    }

    public long key() { return CellGroups.key(pos.x, pos.y, pos.z); }
}
