// Ported from ADM's p3sim/pack/runstate/RunStatePack.kt
package com.teslamaps.adm.sim.pack.runstate;

import com.google.gson.JsonObject;

import java.util.Map;

public class ClassRoles {
    public final int n;
    public final Map<String, ClassRole> classes;
    public final double[] ownedRoomsSortedMean;
    public final Double standingFrac;
    public final Double firstEntrantTopLeaderFrac;
    public final JsonObject raw;

    public ClassRoles(int n, Map<String, ClassRole> classes, double[] ownedRoomsSortedMean, Double standingFrac, Double firstEntrantTopLeaderFrac, JsonObject raw) {
        this.n = n;
        this.classes = classes;
        this.ownedRoomsSortedMean = ownedRoomsSortedMean;
        this.standingFrac = standingFrac;
        this.firstEntrantTopLeaderFrac = firstEntrantTopLeaderFrac;
        this.raw = raw;
    }
}
