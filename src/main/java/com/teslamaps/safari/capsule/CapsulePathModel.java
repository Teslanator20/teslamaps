package com.teslamaps.safari.capsule;

import java.util.ArrayList;
import java.util.List;

// Ported from Sparkling Mutuals' preview/CapsulePathModel + contacts/ContactCurveSampler
public final class CapsulePathModel {

    public static final double SPEED = 0.8482482249942948;
    public static final double VERTICAL_ACCELERATION = -0.020085553311180723;
    public static final double HALF_ACCELERATION = -0.010042776655590361;
    public static final double END_PARAMETER = 17.68350296294565;

    public enum Stop { CONTACT, RANGE_LIMIT, ORDINAL_LIMIT, WORK_LIMIT, INVALID, UNLOADED, FLUID }

    public record Node(double parameter, ShotVector position, boolean observedRegion) {}

    public record Curve(List<Node> nodes, Stop stop) {}

    private CapsulePathModel() {}

    /** position after n ticks, no drag */
    public static ShotVector point(ShotVector eye, ShotVector dir, double n) {
        return new ShotVector(
                eye.x() + SPEED * n * dir.x(),
                eye.y() + SPEED * n * dir.y() + HALF_ACCELERATION * n * (n - 1),
                eye.z() + SPEED * n * dir.z());
    }

    private static Node node(double pitch, ShotVector eye, ShotVector dir, double n) {
        return new Node(n, point(eye, dir, n), -10.0 <= pitch && pitch <= 1.0 && 2.0 <= n && n <= END_PARAMETER);
    }

    public static Curve sample(ShotVector eye, ShotVector aim, double pitch) {
        double magnitude = Math.hypot(Math.hypot(aim.x(), aim.y()), aim.z());
        if (!eye.finite() || !aim.finite() || !(Math.abs(pitch) <= Double.MAX_VALUE) || pitch < -90 || pitch > 90
                || !(Math.abs(magnitude) <= Double.MAX_VALUE) || magnitude == 0) {
            return new Curve(List.of(), Stop.INVALID);
        }
        ShotVector dir = new ShotVector(aim.x() / magnitude, aim.y() / magnitude, aim.z() / magnitude);
        List<Node> nodes = new ArrayList<>(257);
        nodes.add(node(pitch, eye, dir, 0));
        double n = 0, length = 0;
        int attempts = 0;
        while (nodes.size() <= 256 && attempts < 4096) {
            double next = Math.min(n + 0.5, n < 2 ? 2 : n < END_PARAMETER ? END_PARAMETER : 128);
            ShotVector last = nodes.get(nodes.size() - 1).position();
            ShotVector p = point(eye, dir, next);
            while (Math.sqrt(last.distanceSquared(p)) > 0.5
                    || Math.abs(VERTICAL_ACCELERATION) * Math.pow(next - n, 2) / 8 > 0.01) {
                if (++attempts >= 4096) return new Curve(List.copyOf(nodes), Stop.WORK_LIMIT);
                next = (n + next) / 2;
                p = point(eye, dir, next);
            }
            attempts++;
            if (!p.finite() || next <= n) return new Curve(List.of(), Stop.INVALID);
            double chord = Math.sqrt(last.distanceSquared(p));
            if (length + chord >= 64) {
                double lo = n, hi = next;
                for (int i = 0; i < 48; i++) {
                    double mid = (lo + hi) / 2;
                    if (Math.sqrt(last.distanceSquared(point(eye, dir, mid))) <= 64 - length) lo = mid;
                    else hi = mid;
                }
                if (lo > n) nodes.add(node(pitch, eye, dir, lo));
                return new Curve(List.copyOf(nodes), Stop.RANGE_LIMIT);
            }
            nodes.add(node(pitch, eye, dir, next));
            length += chord;
            n = next;
            if (next >= 128) return new Curve(List.copyOf(nodes), Stop.ORDINAL_LIMIT);
        }
        return new Curve(List.copyOf(nodes), Stop.WORK_LIMIT);
    }
}
