package com.teslamaps.safari.capsule;

import com.teslamaps.safari.capsule.CapsulePathModel.Curve;
import com.teslamaps.safari.capsule.CapsulePathModel.Node;
import com.teslamaps.safari.capsule.CapsulePathModel.Stop;
import java.util.ArrayList;
import java.util.List;

// Ported from Sparkling Mutuals' contacts/CapsuleContactSolver
public final class CapsuleContactSolver {

    public static final double TIE = 1.0E-7;

    public enum Kind { TERRAIN, CRITTER }

    public record Candidate(Kind kind, double parameter, ShotVector position, ShotVector normal, String targetKey, String proxy) {}

    public record Target(String key, String species, ShotBounds bounds, String proxy, boolean bodyPart, ContactSurfaceBox surface) {}

    public record Boundary(double fraction, Stop reason) {}

    public record Segment(Candidate terrain, Boundary boundary) {}

    public record Trace(List<Node> nodes, Candidate primary, Stop stop) {}

    public interface SegmentScene {
        Segment scene(Node a, Node b);
    }

    private CapsuleContactSolver() {}

    public static Trace trace(Curve curve, List<Target> targets, SegmentScene scene) {
        if (curve.nodes().isEmpty()) return new Trace(List.of(), null, curve.stop());
        List<Node> nodes = new ArrayList<>();
        nodes.add(curve.nodes().get(0));
        int limit = Math.min(targets.size(), 64);
        for (int i = 0; i + 1 < curve.nodes().size(); i++) {
            Node a = curve.nodes().get(i), b = curve.nodes().get(i + 1);
            Segment seg = scene.scene(a, b);
            Candidate best = seg.terrain();
            if (best != null && !(best.position().finite() && Math.abs(best.parameter()) <= Double.MAX_VALUE
                    && best.parameter() >= a.parameter() - TIE && best.parameter() <= b.parameter() + TIE)) {
                best = null;
            }
            for (int t = 0; t < limit; t++) {
                Target target = targets.get(t);
                ContactSurfaceBox.Hit surfaceHit = target.surface() != null ? target.surface().entry(a.position(), b.position()) : null;
                double f;
                if (target.surface() != null) {
                    if (surfaceHit == null) continue;
                    f = surfaceHit.fraction();
                } else {
                    Double e = target.bounds().entry(a.position(), b.position());
                    if (e == null) continue;
                    f = e;
                }
                Candidate c = new Candidate(target.surface() != null ? Kind.TERRAIN : Kind.CRITTER,
                        a.parameter() + (b.parameter() - a.parameter()) * f,
                        a.position().lerp(b.position(), f),
                        surfaceHit != null ? surfaceHit.normal() : null,
                        target.key(), target.proxy());
                if (best == null || c.parameter() < best.parameter() - TIE) {
                    best = c;
                } else if (Math.abs(c.parameter() - best.parameter()) <= TIE
                        && c.kind() == Kind.TERRAIN && best.kind() == Kind.CRITTER) {
                    best = c;
                }
            }
            Boundary boundary = seg.boundary();
            if (boundary != null && !(Math.abs(boundary.fraction()) <= Double.MAX_VALUE
                    && boundary.fraction() >= 0 && boundary.fraction() <= 1)) {
                boundary = null;
            }
            Double boundaryParam = boundary != null ? a.parameter() + (b.parameter() - a.parameter()) * boundary.fraction() : null;
            if (boundary != null && (best == null || boundaryParam <= best.parameter() + TIE)) {
                if (boundaryParam > nodes.get(nodes.size() - 1).parameter()) {
                    nodes.add(new Node(boundaryParam, a.position().lerp(b.position(), boundary.fraction()),
                            a.observedRegion() && b.observedRegion()));
                }
                return new Trace(List.copyOf(nodes), null, boundary.reason());
            }
            if (best != null) {
                if (best.parameter() > nodes.get(nodes.size() - 1).parameter()) {
                    nodes.add(new Node(best.parameter(), best.position(), a.observedRegion() && b.observedRegion()));
                }
                return new Trace(List.copyOf(nodes), best, Stop.CONTACT);
            }
            nodes.add(b);
        }
        return new Trace(List.copyOf(nodes), null, curve.stop());
    }
}
