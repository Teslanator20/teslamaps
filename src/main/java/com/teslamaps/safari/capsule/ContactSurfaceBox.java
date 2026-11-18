package com.teslamaps.safari.capsule;

import java.util.ArrayList;
import java.util.List;

// Ported from Sparkling Mutuals' contacts/ContactSurfaceBox
public final class ContactSurfaceBox {

    public record Hit(double fraction, ShotVector position, ShotVector normal) {}

    public record Face(ShotVector point, ShotVector normal) {}

    final ShotVector origin, x, y, z;
    private final double determinant;
    private final ShotVector[] inverse;

    public ContactSurfaceBox(ShotVector origin, ShotVector x, ShotVector y, ShotVector z) {
        this.origin = origin;
        this.x = x;
        this.y = y;
        this.z = z;
        this.determinant = x.dot(y.cross(z));
        double inv = 1 / determinant;
        this.inverse = new ShotVector[]{y.cross(z).scaled(inv), z.cross(x).scaled(inv), x.cross(y).scaled(inv)};
    }

    public static ContactSurfaceBox axis(ShotBounds b) {
        return new ContactSurfaceBox(b.min(),
                new ShotVector(b.max().x() - b.min().x(), 0, 0),
                new ShotVector(0, b.max().y() - b.min().y(), 0),
                new ShotVector(0, 0, b.max().z() - b.min().z()));
    }

    public boolean valid() {
        return origin.finite() && x.finite() && y.finite() && z.finite()
                && Math.abs(determinant) <= Double.MAX_VALUE && Math.abs(determinant) > 1.0E-10;
    }

    public ShotVector local(ShotVector p) {
        ShotVector d = p.minus(origin);
        return new ShotVector(inverse[0].dot(d), inverse[1].dot(d), inverse[2].dot(d));
    }

    public ShotVector world(ShotVector p) {
        return origin.plus(x, p.x()).plus(y, p.y()).plus(z, p.z());
    }

    public boolean contains(ShotVector p, double tolerance) {
        if (!valid() || !p.finite()) return false;
        ShotVector q = local(p);
        return in(q.x(), tolerance) && in(q.y(), tolerance) && in(q.z(), tolerance);
    }

    private static boolean in(double v, double t) { return v >= -t && v <= 1 + t; }

    public List<ShotVector> vertices() {
        List<ShotVector> out = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) out.add(world(new ShotVector(i & 1, i >> 1 & 1, i >> 2 & 1)));
        return out;
    }

    public ShotBounds bounds() {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (ShotVector v : vertices()) {
            minX = Math.min(minX, v.x()); minY = Math.min(minY, v.y()); minZ = Math.min(minZ, v.z());
            maxX = Math.max(maxX, v.x()); maxY = Math.max(maxY, v.y()); maxZ = Math.max(maxZ, v.z());
        }
        return new ShotBounds(new ShotVector(minX, minY, minZ), new ShotVector(maxX, maxY, maxZ));
    }

    /** enter/exit fractions of from->to through the box, null if it misses */
    public double[] span(ShotVector from, ShotVector to) {
        if (!valid() || !from.finite() || !to.finite()) return null;
        double[] s = arr(local(from)), e = arr(local(to));
        double enter = 0, exit = 1;
        for (int i = 0; i < 3; i++) {
            double d = e[i] - s[i];
            if (Math.abs(d) < 1.0E-12) {
                if (s[i] < -1.0E-6 || s[i] > 1.000001) return null;
            } else {
                enter = Math.max(enter, Math.min(-s[i] / d, (1 - s[i]) / d));
                exit = Math.min(exit, Math.max(-s[i] / d, (1 - s[i]) / d));
                if (enter > exit + 1.0E-10) return null;
            }
        }
        return enter <= 1 && exit >= 0 ? new double[]{enter, exit} : null;
    }

    public Hit entry(ShotVector from, ShotVector to) {
        if (!valid() || !from.finite() || !to.finite()) return null;
        double[] s = arr(local(from)), e = arr(local(to));
        double enter = 0, exit = 1;
        ShotVector normal = from.minus(to).unit();
        if (normal == null) return null;
        for (int i = 0; i < 3; i++) {
            double d = e[i] - s[i];
            if (Math.abs(d) < 1.0E-12) {
                if (s[i] < 0 || s[i] > 1) return null;
            } else {
                double t0 = -s[i] / d, t1 = (1 - s[i]) / d;
                double near = Math.min(t0, t1), far = Math.max(t0, t1);
                if (near > enter) {
                    enter = near;
                    normal = inverse[i].scaled(d > 0 ? -1 : 1).unit();
                    if (normal == null) return null;
                }
                exit = Math.min(exit, far);
                if (enter > exit + 1.0E-10) return null;
            }
        }
        return enter >= 0 && enter <= 1 && !(exit < 0) ? new Hit(enter, from.lerp(to, enter), normal) : null;
    }

    public List<Face> facesNear(ShotVector p, double radius) {
        if (!valid()) return List.of();
        ShotVector q = local(p);
        double[] base = {clamp(q.x()), clamp(q.y()), clamp(q.z())};
        List<Face> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            for (int side = 0; side <= 1; side++) {
                double[] v = base.clone();
                v[i] = side;
                ShotVector point = world(new ShotVector(v[0], v[1], v[2]));
                if (point.distanceSquared(p) > radius * radius) continue;
                ShotVector n = inverse[i].scaled(side == 0 ? -1 : 1).unit();
                if (n != null) out.add(new Face(point, n));
            }
        }
        return out;
    }

    public boolean touches(ContactSurfaceBox other) {
        if (!valid() || !other.valid()) return false;
        ShotBounds a = bounds(), b = other.bounds();
        if (a.max().x() < b.min().x() - 1.0E-7 || b.max().x() < a.min().x() - 1.0E-7
                || a.max().y() < b.min().y() - 1.0E-7 || b.max().y() < a.min().y() - 1.0E-7
                || a.max().z() < b.min().z() - 1.0E-7 || b.max().z() < a.min().z() - 1.0E-7) return false;
        return edgesHit(this, other) || edgesHit(other, this);
    }

    private static boolean edgesHit(ContactSurfaceBox s, ContactSurfaceBox t) {
        List<ShotVector> vs = s.vertices();
        for (ShotVector v : vs) if (t.contains(v, 1.0E-7)) return true;
        for (int i = 0; i < 8; i++) {
            for (int bit : new int[]{1, 2, 4}) {
                if ((i & bit) == 0 && t.entry(vs.get(i), vs.get(i | bit)) != null) return true;
            }
        }
        return false;
    }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    private static double[] arr(ShotVector v) { return new double[]{v.x(), v.y(), v.z()}; }
}
