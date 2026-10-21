// Ported from ADM's p3sim/run/api/BotApi.kt
package com.teslamaps.adm.sim.run.api;

import java.util.Objects;
import java.util.UUID;

public final class LeapTarget {
    public final UUID uuid;
    public final String name;
    public final String dungeonClass;
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;
    public final boolean here;

    public LeapTarget(UUID uuid, String name, String dungeonClass, double x, double y, double z, float yaw, float pitch, boolean here) {
        this.uuid = uuid;
        this.name = name;
        this.dungeonClass = dungeonClass;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.here = here;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof LeapTarget oo && Objects.equals(uuid, oo.uuid) && Objects.equals(name, oo.name) && Objects.equals(dungeonClass, oo.dungeonClass) && Objects.equals(x, oo.x) && Objects.equals(y, oo.y) && Objects.equals(z, oo.z) && Objects.equals(yaw, oo.yaw) && Objects.equals(pitch, oo.pitch) && Objects.equals(here, oo.here);
    }
    @Override public int hashCode() { return Objects.hash(uuid, name, dungeonClass, x, y, z, yaw, pitch, here); }
    @Override public String toString() { return "LeapTarget(" + "uuid=" + uuid + ", " + "name=" + name + ", " + "dungeonClass=" + dungeonClass + ", " + "x=" + x + ", " + "y=" + y + ", " + "z=" + z + ", " + "yaw=" + yaw + ", " + "pitch=" + pitch + ", " + "here=" + here + ")"; }
}
