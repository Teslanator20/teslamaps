// Ported from ADM's p3sim/clear/secrets/SecretSites.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.run.SecretKind;

public class SecretSite {
    public final int room;
    public final String id;
    public final SecretKind kind;
    public final int x;
    public final int y;
    public final int z;
    public final String facing;
    public final Pos lever;
    public final boolean baked;
    public final int quarterTurns;
    public final boolean supplemental;
    public final SocketInfo socket;
    public final boolean hidden;

    public SecretSite(int room, String id, SecretKind kind, int x, int y, int z, String facing, Pos lever, boolean baked, int quarterTurns,
                      boolean supplemental, SocketInfo socket, boolean hidden) {
        this.room = room;
        this.id = id;
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
        this.lever = lever;
        this.baked = baked;
        this.quarterTurns = quarterTurns;
        this.supplemental = supplemental;
        this.socket = socket;
        this.hidden = hidden;
    }

    public SecretSite(int room, String id, SecretKind kind, int x, int y, int z, String facing, Pos lever, boolean baked, int quarterTurns,
                      boolean supplemental, SocketInfo socket) {
        this(room, id, kind, x, y, z, facing, lever, baked, quarterTurns, supplemental, socket, false);
    }

    public SecretSite(int room, String id, SecretKind kind, int x, int y, int z, String facing, Pos lever, boolean baked, int quarterTurns,
                      boolean supplemental) {
        this(room, id, kind, x, y, z, facing, lever, baked, quarterTurns, supplemental, null, false);
    }

    public String key() { return room + ":" + id; }

    public boolean at(int px, int py, int pz) { return x == px && y == py && z == pz; }
}
