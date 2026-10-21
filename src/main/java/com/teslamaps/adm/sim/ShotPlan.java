// Ported from ADM's p3sim/Bows.kt
package com.teslamaps.adm.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.world.phys.Vec3;

public final class ShotPlan {
    private ShotPlan() {}

    public static class Planned {
        public final Vec3 from;
        public final Vec3 at;
        public final Vec3 v;
        public final boolean owned;
        public final int delay;

        public Planned(Vec3 from, Vec3 at, Vec3 v, boolean owned, int delay) {
            this.from = from;
            this.at = at;
            this.v = v;
            this.owned = owned;
            this.delay = delay;
        }
    }

    private static final double G = 0.05;
    private static final double RIGHT = 0.01745;

    public static Vec3 look(float yaw, float pitch) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    public static Vec3 roty(Vec3 v, double deg) {
        double a = Math.toRadians(deg);
        double c = Math.cos(a);
        double s = Math.sin(a);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    public static final Set<String> DUPLEX = Set.of(Bows.MOSQUITO, Bows.LAST_BREATH);

    private static final double[] HYDRA = {-8.0, 8.0};
    private static final double[] SIDES = {5.5, -5.5, 5.5, -5.5};
    private static final double[] HYDRA_C = new double[2];
    private static final double[] HYDRA_S = new double[2];
    private static final double[] SIDES_C = new double[4];
    private static final double[] SIDES_S = new double[4];

    static {
        for (int i = 0; i < 2; i++) {
            HYDRA_C[i] = Math.cos(Math.toRadians(HYDRA[i]));
            HYDRA_S[i] = Math.sin(Math.toRadians(HYDRA[i]));
        }
        for (int i = 0; i < 4; i++) {
            SIDES_C[i] = Math.cos(Math.toRadians(SIDES[i]));
            SIDES_S[i] = Math.sin(Math.toRadians(SIDES[i]));
        }
    }

    public static class Arrows {
        public static final int MAX = 9;

        public int n = 0;
        public final double[] from = new double[3 * MAX];
        public final double[] at = new double[3 * MAX];
        public final double[] v = new double[3 * MAX];
        public final double[] dFrom = new double[MAX];
        public final double[] dAt = new double[MAX];
        public final double[] dV = new double[9 * MAX];
        public final int[] delay = new int[MAX];
        public final boolean[] owned = new boolean[MAX];

        public int add(double fx, double fy, double fz, double ax, double ay, double az, double vx, double vy, double vz,
                double dFrom, double dAt, boolean owned, int delay) {
            int i = n++;
            from[3 * i] = fx; from[3 * i + 1] = fy; from[3 * i + 2] = fz;
            at[3 * i] = ax; at[3 * i + 1] = ay; at[3 * i + 2] = az;
            v[3 * i] = vx; v[3 * i + 1] = vy; v[3 * i + 2] = vz;
            this.dFrom[i] = dFrom; this.dAt[i] = dAt; this.owned[i] = owned; this.delay[i] = delay;
            return i;
        }
    }

    public static List<Planned> plan(String bow, Vec3 pos, float yaw, float pitch, boolean crouch, int stacks, Vec3 noise, int duplexDelay) {
        return plan(bow, pos, yaw, pitch, crouch, stacks, noise, duplexDelay, 3.0);
    }

    public static List<Planned> plan(String bow, Vec3 pos, float yaw, float pitch, boolean crouch, int stacks, Vec3 noise, int duplexDelay, double speed) {
        Arrows a = new Arrows();
        planInto(a, bow, pos.x, pos.y, pos.z, yaw, pitch, crouch, stacks, noise.x, noise.y, noise.z, duplexDelay, speed);
        List<Planned> out = new ArrayList<>(a.n);
        for (int i = 0; i < a.n; i++) {
            out.add(new Planned(new Vec3(a.from[3 * i], a.from[3 * i + 1], a.from[3 * i + 2]), new Vec3(a.at[3 * i], a.at[3 * i + 1], a.at[3 * i + 2]),
                    new Vec3(a.v[3 * i], a.v[3 * i + 1], a.v[3 * i + 2]), a.owned[i], a.delay[i]));
        }
        return out;
    }

    public static void planInto(Arrows out, String bow, double px, double py, double pz, float yaw, float pitch, boolean crouch, int stacks,
                                double nx, double ny, double nz, int duplexDelay) {
        planInto(out, bow, px, py, pz, yaw, pitch, crouch, stacks, nx, ny, nz, duplexDelay, 3.0);
    }

    public static void planInto(Arrows out, String bow, double px, double py, double pz, float yaw, float pitch, boolean crouch, int stacks,
                                double nx, double ny, double nz, int duplexDelay, double speed) {
        out.n = 0;
        double k = 1 + 0.01 * stacks;
        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);
        double cy = Math.cos(yr), sy = Math.sin(yr), cp = Math.cos(pr), sp = Math.sin(pr);
        double dx = -sy * cp, dy = -sp, dz = cy * cp;
        double ux = (dx + nx) * speed, uy = (dy + ny) * speed, uz = (dz + nz) * speed;
        double fx = px - cy * RIGHT, fy = py + (crouch ? 1.27 : 1.62) - 0.1, fz = pz - sy * RIGHT;
        double p1x = fx + ux, p1y = fy + uy, p1z = fz + uz;
        double mx = 0.99 * ux * k, my = (0.99 * uy - G) * k, mz = 0.99 * uz * k;
        double dm = 0.99 * k * speed;
        diag(out, out.add(fx, fy, fz, p1x, p1y, p1z, mx, my, mz, 0.0, speed, true, 0), dm);
        if (stacks >= 10) for (int h = 0; h < 2; h++) {
            double c = HYDRA_C[h], s = HYDRA_S[h];
            int i = out.add(p1x, p1y, p1z, p1x, p1y, p1z, mx * c - mz * s, my, mx * s + mz * c, speed, speed, false, 0);
            double[] d = out.dV;
            int o = 9 * i;
            d[o] = dm * c; d[o + 1] = 0.0; d[o + 2] = -dm * s;
            d[o + 3] = 0.0; d[o + 4] = dm; d[o + 5] = 0.0;
            d[o + 6] = dm * s; d[o + 7] = 0.0; d[o + 8] = dm * c;
        }
        if (bow.equals(Bows.TERMINATOR)) {
            double ml = Math.sqrt(mx * mx + my * my + mz * mz);
            double gx = dm * mx / ml, gy = dm * my / ml, gz = dm * mz / ml;
            for (int t = 0; t < 4; t++) {
                double c = SIDES_C[t], s = SIDES_S[t];
                double rx = dx * c - dz * s, rz = dx * s + dz * c;
                int i = out.add(p1x, p1y - 0.5, p1z, p1x, p1y - 0.5, p1z, rx * ml, dy * ml + G, rz * ml, speed, speed, false, 0);
                double[] d = out.dV;
                int o = 9 * i;
                d[o] = rx * gx; d[o + 1] = rx * gy; d[o + 2] = rx * gz;
                d[o + 3] = dy * gx; d[o + 4] = dy * gy; d[o + 5] = dy * gz;
                d[o + 6] = rz * gx; d[o + 7] = rz * gy; d[o + 8] = rz * gz;
            }
        }
        if (DUPLEX.contains(bow)) for (int r = 0; r < 2; r++) {
            diag(out, out.add(p1x, p1y, p1z, p1x, p1y, p1z, mx, my, mz, speed, speed, false, duplexDelay), dm);
        }
    }

    private static void diag(Arrows out, int i, double d) {
        int o = 9 * i;
        for (int r = 0; r < 9; r++) out.dV[o + r] = 0.0;
        out.dV[o] = d; out.dV[o + 4] = d; out.dV[o + 8] = d;
    }
}
