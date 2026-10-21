// Ported from ADM's p3sim/clear/secrets/RedstoneKeys.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.pack.Pos;
import java.util.List;

public class KeyRoomSpec {
    public final String designId;
    public final List<Pos> keySpots;
    public final Pos socket;
    public final Pos head;
    public final String wallFacing;
    public final List<Pos> opens;
    public final List<RevealChestSpec> chests;

    public KeyRoomSpec(String designId, List<Pos> keySpots, Pos socket, Pos head, String wallFacing, List<Pos> opens, List<RevealChestSpec> chests) {
        this.designId = designId;
        this.keySpots = keySpots;
        this.socket = socket;
        this.head = head;
        this.wallFacing = wallFacing;
        this.opens = opens;
        this.chests = chests;
    }
}
