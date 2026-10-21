// Ported from ADM's p3sim/clear/secrets/RedstoneKeys.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.pack.Pos;
import java.util.List;

public class SocketInfo {
    public final HeadCell head;
    public final List<Pos> opens;
    public final List<RevealChest> chests;

    public SocketInfo(HeadCell head, List<Pos> opens, List<RevealChest> chests) {
        this.head = head;
        this.opens = opens;
        this.chests = chests;
    }
}
