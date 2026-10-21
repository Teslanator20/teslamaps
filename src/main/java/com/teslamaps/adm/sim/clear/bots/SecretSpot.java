// Ported from ADM's p3sim/clear/bots/RoomRoutes.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.pack.rooms.RoomSecret;

/** A secret of a placed room as a bot sees it: what it is, where it is in the world. */
public class SecretSpot {
    public final RoomSecret secret;
    public final int x;
    public final int y;
    public final int z;

    public SecretSpot(RoomSecret secret, int x, int y, int z) {
        this.secret = secret;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Pt pt() { return new Pt(x + 0.5, z + 0.5); }
}
