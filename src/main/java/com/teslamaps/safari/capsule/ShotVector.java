package com.teslamaps.safari.capsule;

// Ported from Sparkling Mutuals' safari/shots/ShotVector + contacts/ContactData helpers
public record ShotVector(double x, double y, double z) {

    public static final ShotVector UP = new ShotVector(0, 1, 0);
    public static final ShotVector EAST = new ShotVector(1, 0, 0);
    public static final ShotVector SOUTH = new ShotVector(0, 0, 1);

    public ShotVector minus(ShotVector o) { return new ShotVector(x - o.x, y - o.y, z - o.z); }

    public ShotVector plus(ShotVector o) { return new ShotVector(x + o.x, y + o.y, z + o.z); }

    public ShotVector scaled(double s) { return new ShotVector(x * s, y * s, z * s); }

    public ShotVector plus(ShotVector o, double s) { return new ShotVector(x + o.x * s, y + o.y * s, z + o.z * s); }

    public double dot(ShotVector o) { return x * o.x + y * o.y + z * o.z; }

    public ShotVector cross(ShotVector o) {
        return new ShotVector(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double distanceSquared(ShotVector o) {
        return (x - o.x) * (x - o.x) + (y - o.y) * (y - o.y) + (z - o.z) * (z - o.z);
    }

    public ShotVector lerp(ShotVector to, double f) {
        return new ShotVector(x + (to.x - x) * f, y + (to.y - y) * f, z + (to.z - z) * f);
    }

    public boolean finite() {
        return Math.abs(x) <= Double.MAX_VALUE && Math.abs(y) <= Double.MAX_VALUE && Math.abs(z) <= Double.MAX_VALUE;
    }

    /** null for a zero or broken vector */
    public ShotVector unit() {
        double len = Math.sqrt(dot(this));
        return Math.abs(len) <= Double.MAX_VALUE && len > 1.0E-10 ? scaled(1 / len) : null;
    }

    public ShotVector unit(ShotVector fallback) {
        double len = Math.hypot(Math.hypot(x, y), z);
        return Math.abs(len) <= Double.MAX_VALUE && len > 1.0E-10 ? new ShotVector(x / len, y / len, z / len) : fallback;
    }

    public static ShotVector of(net.minecraft.world.phys.Vec3 v) { return new ShotVector(v.x, v.y, v.z); }
}
