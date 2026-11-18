package com.teslamaps.safari.capsule;

public record ShotBounds(ShotVector min, ShotVector max) {

    public static ShotBounds of(net.minecraft.world.phys.AABB b) {
        return new ShotBounds(new ShotVector(b.minX, b.minY, b.minZ), new ShotVector(b.maxX, b.maxY, b.maxZ));
    }

    public boolean valid() {
        return min.finite() && max.finite() && min.x() < max.x() && min.y() < max.y() && min.z() < max.z();
    }

    public ShotVector center() { return min.lerp(max, 0.5); }

    /** slab test, entry fraction along from->to or null */
    public Double entry(ShotVector from, ShotVector to) {
        if (!from.finite() || !to.finite() || !valid()) return null;
        double near = 0, far = 1;
        double[] s = {from.x(), from.y(), from.z()};
        double[] e = {to.x(), to.y(), to.z()};
        double[] lo = {min.x(), min.y(), min.z()};
        double[] hi = {max.x(), max.y(), max.z()};
        for (int i = 0; i < 3; i++) {
            double d = e[i] - s[i];
            if (d == 0.0) {
                if (s[i] < lo[i] || s[i] > hi[i]) return null;
            } else {
                near = Math.max(near, Math.min((lo[i] - s[i]) / d, (hi[i] - s[i]) / d));
                far = Math.min(far, Math.max((lo[i] - s[i]) / d, (hi[i] - s[i]) / d));
                if (near > far) return null;
            }
        }
        return near >= 0 && near <= 1 ? near : null;
    }
}
