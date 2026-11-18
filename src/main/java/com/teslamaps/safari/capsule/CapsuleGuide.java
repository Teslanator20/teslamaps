package com.teslamaps.safari.capsule;

// Ported from Sparkling Mutuals' contacts/ContactGuideGeometry + ContactSurfaceRing
import com.teslamaps.safari.capsule.CapsuleContactSolver.Candidate;
import com.teslamaps.safari.capsule.CapsuleContactSolver.Kind;
import com.teslamaps.safari.capsule.CapsuleContactSolver.Trace;
import com.teslamaps.safari.capsule.CapsulePathModel.Node;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CapsuleGuide {

    public static final int PATH = 0, HIT = 1;

    /** One segment in world space, role picks the colour at draw time. */
    public record Line(float x0, float y0, float z0, float x1, float y1, float z1, int role) {
        static Line of(ShotVector a, ShotVector b, int role) {
            return new Line((float) a.x(), (float) a.y(), (float) a.z(), (float) b.x(), (float) b.y(), (float) b.z(), role);
        }
    }

    private CapsuleGuide() {}

    /** path + marker lines; dashed where the path model is unverified */
    public static List<Line> build(Trace trace, ShotVector camera, List<ContactSurfaceBox> patch, boolean allDashed) {
        List<Line> out = new ArrayList<>();
        double distance = 0;
        List<Node> nodes = trace.nodes();
        for (int i = 0; i + 1 < nodes.size(); i++) {
            Node a = nodes.get(i), b = nodes.get(i + 1);
            double length = Math.sqrt(a.position().distanceSquared(b.position()));
            if (!(Math.abs(length) <= Double.MAX_VALUE) || length <= 1.0E-12) continue;
            if (!allDashed && a.observedRegion() && b.observedRegion()) {
                out.add(Line.of(a.position(), b.position(), PATH));
            } else {
                double d = 0;
                while (d < length - 1.0E-10) {
                    double phase = (distance + d) % 0.55;
                    boolean on = phase < 0.35;
                    double next = Math.min(length, d + Math.max(on ? 0.35 - phase : 0.55 - phase, 1.0E-8));
                    if (on) out.add(Line.of(a.position().lerp(b.position(), d / length), a.position().lerp(b.position(), next / length), PATH));
                    d = next;
                }
            }
            distance += length;
        }

        Candidate hit = trace.primary();
        if (hit == null) return out;
        ShotVector at = hit.position();
        ShotVector n = hit.kind() == Kind.TERRAIN
                ? (hit.normal() != null ? hit.normal() : ShotVector.UP).unit(ShotVector.UP)
                : camera.minus(at).unit(ShotVector.SOUTH);
        ShotVector u = n.cross(Math.abs(n.y()) < 0.9 ? ShotVector.UP : ShotVector.EAST).unit(ShotVector.EAST);
        ShotVector v = n.cross(u).unit(ShotVector.UP);
        if (hit.kind() == Kind.TERRAIN) {
            if (patch != null) {
                out.addAll(ring(at, n, patch));
            } else {
                for (int i = 0; i < 16; i++) {
                    double a0 = Math.PI * 2 * i / 16, a1 = Math.PI * 2 * (i + 1) / 16;
                    out.add(Line.of(point(at, u, v, 0.25 * Math.cos(a0), 0.25 * Math.sin(a0)),
                            point(at, u, v, 0.25 * Math.cos(a1), 0.25 * Math.sin(a1)), PATH));
                }
            }
        } else {
            ShotVector[] diamond = {
                    point(at, u, v, 0, 0.25), point(at, u, v, 0.25, 0), point(at, u, v, 0, -0.25),
                    point(at, u, v, -0.25, 0), point(at, u, v, 0, 0.25)};
            for (int i = 0; i + 1 < diamond.length; i++) out.add(Line.of(diamond[i], diamond[i + 1], HIT));
            out.add(Line.of(point(at, u, v, -0.06, 0), point(at, u, v, 0.06, 0), HIT));
            out.add(Line.of(point(at, u, v, 0, -0.06), point(at, u, v, 0, 0.06), HIT));
        }
        return out;
    }

    private static ShotVector point(ShotVector c, ShotVector u, ShotVector v, double x, double y) {
        return c.plus(u, x).plus(v, y);
    }

    // circle of 0.25 projected onto the surfaces around the hit, bent over edges
    private static List<Line> ring(ShotVector center, ShotVector normal, List<ContactSurfaceBox> surfaces) {
        ShotVector n = normal.unit();
        if (n == null || !center.finite()) return List.of();
        List<ContactSurfaceBox> source = new ArrayList<>();
        for (ContactSurfaceBox s : surfaces.subList(0, Math.min(32, surfaces.size()))) {
            if (s.valid() && !s.facesNear(center, 0.65).isEmpty()) source.add(s);
        }
        Set<ContactSurfaceBox> connected = new LinkedHashSet<>();
        for (ContactSurfaceBox s : source) {
            for (ContactSurfaceBox.Face f : s.facesNear(center, 0.012)) {
                if (f.normal().dot(n) > 0.99) {
                    connected.add(s);
                    break;
                }
            }
        }
        if (connected.isEmpty()) return List.of();
        for (int pass = 0; pass < source.size(); pass++) {
            for (ContactSurfaceBox s : source) {
                if (connected.contains(s)) continue;
                boolean touching = false;
                for (ContactSurfaceBox c : connected) {
                    if (c.touches(s)) {
                        touching = true;
                        break;
                    }
                }
                if (touching) connected.add(s);
            }
        }
        List<ShotVector> dirs = new ArrayList<>();
        dirs.add(n);
        for (ContactSurfaceBox s : connected) {
            for (ContactSurfaceBox.Face f : s.facesNear(center, 0.27)) {
                ShotVector fn = f.normal();
                if (fn.dot(n) < -1.0E-8) continue;
                boolean known = false;
                for (ShotVector d : dirs) if (d.dot(fn) > 0.999) { known = true; break; }
                if (known) continue;
                boolean buried = false;
                ShotVector probe = f.point().plus(fn, 0.002);
                for (ContactSurfaceBox o : connected) if (o != s && o.contains(probe, -1.0E-6)) { buried = true; break; }
                if (!buried) dirs.add(fn);
            }
        }
        ShotVector sum = new ShotVector(0, 0, 0);
        for (ShotVector d : dirs) sum = sum.plus(d);
        ShotVector direction = sum.unit();
        if (direction == null) direction = n;
        ShotVector u = direction.cross(Math.abs(direction.y()) < 0.9 ? ShotVector.UP : ShotVector.EAST).unit();
        if (u == null) return List.of();
        ShotVector v = direction.cross(u).unit();
        if (v == null) return List.of();

        List<Line> out = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            double a0 = Math.PI * 2 * i / 16, a1 = Math.PI * 2 * (i + 1) / 16;
            ContactSurfaceBox.Hit h0 = project(center, u, v, direction, connected, a0);
            if (h0 == null) continue;
            ContactSurfaceBox.Hit h1 = project(center, u, v, direction, connected, a1);
            if (h1 == null) continue;
            if (h0.normal().dot(h1.normal()) > 0.999) {
                add(out, connected, h0.position(), h1.position(), h0.normal());
                continue;
            }
            double lo = a0, hi = a1;
            ContactSurfaceBox.Hit ha = h0, hb = h1;
            for (int k = 0; k < 14; k++) {
                double mid = (lo + hi) / 2;
                ContactSurfaceBox.Hit h = project(center, u, v, direction, connected, mid);
                if (h != null && h.normal().dot(h0.normal()) > 0.999) {
                    lo = mid;
                    ha = h;
                } else {
                    hi = mid;
                    if (h != null) hb = h;
                }
            }
            ShotVector seam = ha.position().lerp(hb.position(), 0.5);
            if (ha.position().distanceSquared(hb.position()) < 1.0E-6) {
                add(out, connected, h0.position(), seam, h0.normal());
                add(out, connected, seam, h1.position(), h1.normal());
            }
        }
        return out;
    }

    private static ContactSurfaceBox.Hit project(ShotVector center, ShotVector u, ShotVector v, ShotVector dir,
                                                 Set<ContactSurfaceBox> connected, double angle) {
        ShotVector p = center.plus(u, 0.25 * Math.cos(angle)).plus(v, 0.25 * Math.sin(angle));
        ShotVector from = p.plus(dir, 0.65), to = p.plus(dir, -0.65);
        ContactSurfaceBox.Hit best = null;
        for (ContactSurfaceBox s : connected) {
            ContactSurfaceBox.Hit h = s.entry(from, to);
            if (h != null && (best == null || best.fraction() > h.fraction())) best = h;
        }
        return best != null && best.position().distanceSquared(center) < 0.25 ? best : null;
    }

    private static void add(List<Line> out, Set<ContactSurfaceBox> connected, ShotVector a, ShotVector b, ShotVector faceNormal) {
        if (out.size() >= 24) return;
        double len = a.distanceSquared(b);
        if (len < 1.0E-14 || len > 0.0225) return;
        List<double[]> spans = new ArrayList<>();
        for (ContactSurfaceBox s : connected) {
            boolean coplanar = false;
            for (ContactSurfaceBox.Face f : s.facesNear(a, 2.0)) {
                if (f.normal().dot(faceNormal) > 0.999
                        && Math.abs(a.minus(f.point()).dot(f.normal())) < 1.0E-5
                        && Math.abs(b.minus(f.point()).dot(f.normal())) < 1.0E-5) {
                    coplanar = true;
                    break;
                }
            }
            if (!coplanar) continue;
            double[] span = s.span(a, b);
            if (span != null) spans.add(span);
        }
        spans.sort((x, y) -> Double.compare(x[0], y[0]));
        double covered = 0;
        for (double[] s : spans) {
            if (s[0] > covered + 1.0E-6) break;
            covered = Math.max(covered, s[1]);
        }
        if (covered < 0.999999) return;
        ShotVector a2 = a.plus(faceNormal, 0.003), b2 = b.plus(faceNormal, 0.003);
        for (ContactSurfaceBox s : connected) {
            double[] span = s.span(a2, b2);
            if (span != null && span[1] - span[0] > 1.0E-6 && s.contains(a2.lerp(b2, (span[0] + span[1]) / 2), -1.0E-7)) return;
        }
        out.add(Line.of(a2, b2, PATH));
    }
}
