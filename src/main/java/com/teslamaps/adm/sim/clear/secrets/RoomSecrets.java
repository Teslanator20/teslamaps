// Ported from ADM's p3sim/clear/secrets/SecretSites.kt
package com.teslamaps.adm.sim.clear.secrets;

import java.util.List;

public class RoomSecrets {
    public final int room;
    public final int max;
    public final List<SecretSite> sites;
    public final List<KeyGate> gates;
    public final List<SecretSite> bonus;

    public RoomSecrets(int room, int max, List<SecretSite> sites, List<KeyGate> gates, List<SecretSite> bonus) {
        this.room = room;
        this.max = max;
        this.sites = sites;
        this.gates = gates;
        this.bonus = bonus;
    }

    public RoomSecrets(int room, int max, List<SecretSite> sites, List<KeyGate> gates) { this(room, max, sites, gates, List.of()); }

    public RoomSecrets(int room, int max, List<SecretSite> sites) { this(room, max, sites, List.of(), List.of()); }

    public int placeable() { return sites.size(); }

    public SecretSite siteAt(int x, int y, int z) {
        for (SecretSite s : sites) if (s.at(x, y, z)) return s;
        return null;
    }

    public KeyGate gateAt(int x, int y, int z) {
        for (KeyGate g : gates) if (g.x == x && g.y == y && g.z == z) return g;
        return null;
    }

    public SecretSite bonusAt(int x, int y, int z) {
        for (SecretSite s : bonus) if (s.at(x, y, z)) return s;
        return null;
    }

    public int missing() { return Math.max(max - sites.size(), 0); }
}
