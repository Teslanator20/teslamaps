// Ported from ADM's p3sim/clear/blood/camp/WallPlan.kt
package com.teslamaps.adm.sim.clear.blood.camp;

/** One wall head: host slot, stand position (world), class and mob name. */
public class WallStation {
    public final int slot;
    public final Vec3 stand;
    public final WallClass cls;
    public final String mobName;

    public WallStation(int slot, Vec3 stand, WallClass cls, String mobName) {
        this.slot = slot;
        this.stand = stand;
        this.cls = cls;
        this.mobName = mobName;
    }
}
