// Ported from ADM's p3sim/clear/state/StateController.kt
package com.teslamaps.adm.sim.clear.state;

import com.teslamaps.adm.sim.run.KeyKind;
import java.util.UUID;

public class KeyStand {
    public final int id;
    public final KeyKind kind;
    public final int room;
    public final double x;
    public final double y;
    public final double z;
    public final int spawnT;
    public final UUID killer;

    public KeyStand(int id, KeyKind kind, int room, double x, double y, double z, int spawnT, UUID killer) {
        this.id = id;
        this.kind = kind;
        this.room = room;
        this.x = x;
        this.y = y;
        this.z = z;
        this.spawnT = spawnT;
        this.killer = killer;
    }
}
