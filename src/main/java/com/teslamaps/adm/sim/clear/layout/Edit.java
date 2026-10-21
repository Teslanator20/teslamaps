package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/DoorGeometry.kt

public class Edit {
    public final int x;
    public final int y;
    public final int z;
    public final String state;
    public final int quarterTurns;

    public Edit(int x, int y, int z, String state, int quarterTurns) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.state = state;
        this.quarterTurns = quarterTurns;
    }

    public Edit(int x, int y, int z, String state) { this(x, y, z, state, 0); }
}
