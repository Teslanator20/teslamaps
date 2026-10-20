package com.teslamaps.adm.leap.pov;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Ported from ADM's pov/PovInterpolator.kt
public final class PovInterpolator {

    public record Sample(long nanos, double x, double y, double z, float headYaw, float pitch) {}

    public record Pose(double x, double y, double z, float headYaw, float pitch) {}

    private final int capacity;
    private final long evictAfterNanos;
    private final Map<UUID, ArrayDeque<Sample>> rings = new HashMap<>();

    public PovInterpolator() { this(40, 5_000_000_000L); }

    public PovInterpolator(int capacity, long evictAfterNanos) {
        this.capacity = capacity;
        this.evictAfterNanos = evictAfterNanos;
    }

    public void sample(UUID id, long nanos, Pose p) {
        ArrayDeque<Sample> ring = rings.computeIfAbsent(id, k -> new ArrayDeque<>(capacity));
        Sample s = new Sample(nanos, p.x, p.y, p.z, p.headYaw, p.pitch);
        Sample newest = ring.peekLast();
        if (newest != null && nanos <= newest.nanos) {
            ring.pollLast();
            ring.addLast(s);
            return;
        }
        ring.addLast(s);
        while (ring.size() > capacity) ring.pollFirst();
    }

    public Pose poseAt(UUID id, long nanos) {
        ArrayDeque<Sample> ring = rings.get(id);
        if (ring == null || ring.isEmpty()) return null;
        Sample oldest = ring.peekFirst();
        if (nanos <= oldest.nanos) return pose(oldest);
        Sample newest = ring.peekLast();
        if (nanos >= newest.nanos) return pose(newest);
        List<Sample> list = new ArrayList<>(ring);
        int i = list.size() - 1;
        while (i > 0 && list.get(i - 1).nanos > nanos) i--;
        Sample a = list.get(i - 1), b = list.get(i);
        double span = b.nanos - a.nanos;
        double t = span <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, (nanos - a.nanos) / span));
        return new Pose(lerp(t, a.x, b.x), lerp(t, a.y, b.y), lerp(t, a.z, b.z),
                rotLerp((float) t, a.headYaw, b.headYaw), (float) lerp(t, a.pitch, b.pitch));
    }

    public void evict(long now) {
        Iterator<Map.Entry<UUID, ArrayDeque<Sample>>> it = rings.entrySet().iterator();
        while (it.hasNext()) {
            Sample newest = it.next().getValue().peekLast();
            if (newest == null || now - newest.nanos > evictAfterNanos) it.remove();
        }
    }

    public Sample newest(UUID id) {
        ArrayDeque<Sample> r = rings.get(id);
        return r == null ? null : r.peekLast();
    }

    public void clear() { rings.clear(); }

    private static Pose pose(Sample s) { return new Pose(s.x, s.y, s.z, s.headYaw, s.pitch); }

    static double lerp(double t, double a, double b) { return a + (b - a) * t; }

    static float wrapDegrees(float deg) {
        float d = deg % 360.0f;
        if (d >= 180.0f) d -= 360.0f;
        if (d < -180.0f) d += 360.0f;
        return d;
    }

    static float rotLerp(float t, float a, float b) { return wrapDegrees(a + wrapDegrees(b - a) * t); }
}
