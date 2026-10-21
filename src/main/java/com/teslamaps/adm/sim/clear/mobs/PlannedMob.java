// Ported from ADM's p3sim/clear/mobs/MobPlanner.kt
package com.teslamaps.adm.sim.clear.mobs;

import java.util.List;
import java.util.UUID;

public class PlannedMob {
    public final UUID id;
    public final int arch;
    public final boolean star;
    public final boolean required;
    public final String modifier;
    public final double maxHp;
    public final double x, y, z;
    public final float yaw;
    public final boolean mover;
    public final List<double[]> loop;

    public PlannedMob(UUID id, int arch, boolean star, boolean required, String modifier, double maxHp, double x, double y, double z, float yaw, boolean mover, List<double[]> loop) {
        this.id = id; this.arch = arch; this.star = star; this.required = required; this.modifier = modifier; this.maxHp = maxHp;
        this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.mover = mover; this.loop = loop;
    }
}
