// Ported from ADM's p3sim/clear/secrets/RedstoneKeys.kt
package com.teslamaps.adm.sim.clear.secrets;

public class KeyGate {
    public final int room;
    public final String key;
    public final int x;
    public final int y;
    public final int z;
    public final int quarterTurns;

    public KeyGate(int room, String key, int x, int y, int z, int quarterTurns) {
        this.room = room;
        this.key = key;
        this.x = x;
        this.y = y;
        this.z = z;
        this.quarterTurns = quarterTurns;
    }
}
