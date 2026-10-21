// Ported from ADM's p3sim/clear/bots/Countdown.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The bots before the clock starts: they appear on the player's spawn block, drop, and walk to the entrance room's exit door. Pure. */
public final class Countdown {
    private Countdown() {}

    /** A walking player's speed, blocks a tick. */
    public static final double WALK_BPT = 0.2158;

    /** Ticks a teammate spends falling from the spawn height to the floor before it can walk. */
    public static final int FALL_TICKS = 12;

    /** How far inside the room, from the door's gap, a bot waits at the end of the countdown. */
    public static final double GATE_INSET = 2.0;

    /** How far sideways of its spawn column a bot ends up, either way. */
    public static final double LATERAL = 1.2;

    /** Changes the pace of one walker by this much either way. */
    public static final double SPEED_SPREAD = 0.15;

    /** The fastest a countdown walk goes when the time is short. */
    public static final double MAX_BPT = 0.28;

    /** Where a bot waits for the clock. */
    public static Pt gate(Pt doorSpot, Pt spawn, KRandom random, double inset) {
        double dx = spawn.x - doorSpot.x, dz = spawn.z - doorSpot.z;
        double len = Math.hypot(dx, dz);
        if (len < 0.5) return spawn;
        double ux = dx / len, uz = dz / len;
        double side = (random.nextDouble() * 2.0 - 1.0) * LATERAL;
        double back = Math.min(inset, len);
        return new Pt(doorSpot.x + ux * back - uz * side, doorSpot.z + uz * back + ux * side);
    }

    public static Pt gate(Pt doorSpot, Pt spawn, KRandom random) { return gate(doorSpot, spawn, random, GATE_INSET); }

    private static int walkTicks(Pt spawn, List<Pt> points, double v) {
        Pt prev = spawn;
        int sum = 0;
        for (Pt p : points) { sum += Math.max(1, (int) Math.ceil(prev.dist(p) / v)); prev = p; }
        return sum;
    }

    /** The legs of one bot from tick [fromT] (negative) to 0. Fills [fromT] to 0 exactly; empty when there is no time. */
    public static List<Leg> legs(Pt spawn, Pt gate, int fromT, KRandom random) {
        int window = -fromT;
        if (window <= 0) return List.of();
        double dist = spawn.dist(gate);
        int fall = Math.min(FALL_TICKS, window / 3);
        int avail = window - fall;
        if (dist < 0.3) return List.of(new Leg(fromT, 0, spawn, spawn));
        double speed = WALK_BPT * (1.0 - SPEED_SPREAD + 2 * SPEED_SPREAD * random.nextDouble());
        int stretches = dist / speed >= 24 && random.nextDouble() < 0.5 ? 3 : 2;
        ArrayList<Double> c = new ArrayList<>();
        for (int i = 1; i < stretches; i++) c.add(0.17 + 0.66 * random.nextDouble());
        Collections.sort(c);
        List<Double> cuts = stretches == 3 && c.get(1) - c.get(0) < 0.17 ? List.of(0.33, 0.66) : c;
        ArrayList<Double> ends = new ArrayList<>(cuts);
        ends.add(1.0);
        ArrayList<Pt> points = new ArrayList<>();
        for (double e : ends) points.add(e >= 1.0 ? gate : spawn.lerp(gate, e));
        while (walkTicks(spawn, points, speed) > avail * 0.9 && speed < MAX_BPT) speed = Math.min(MAX_BPT, speed * 1.08);
        int walk = walkTicks(spawn, points, speed);
        if (walk > avail) return squeeze(spawn, gate, fromT);

        int standing = avail - walk;
        int first = (int) (standing * (0.1 + 0.25 * random.nextDouble()));
        ArrayList<Integer> pauses = new ArrayList<>();
        for (int i = 1; i < stretches; i++) pauses.add((int) (standing * (0.1 + 0.2 * random.nextDouble())));

        ArrayList<Leg> legs = new ArrayList<>();
        int t = fromT;
        Pt cur = spawn;
        int n0 = fall + first;
        if (n0 > 0) { legs.add(new Leg(t, t + n0, cur, cur)); t += n0; }
        for (int i = 0; i < points.size(); i++) {
            Pt to = points.get(i);
            int ticks = Math.max(1, (int) Math.ceil(cur.dist(to) / speed));
            legs.add(new Leg(t, t + ticks, cur, to));
            t += ticks;
            cur = to;
            if (i < pauses.size()) {
                int n = pauses.get(i);
                if (n > 0) { legs.add(new Leg(t, t + n, cur, cur)); t += n; }
            }
        }
        if (t < 0) legs.add(new Leg(t, 0, cur, cur));
        return legs;
    }

    /** A plain stand-then-walk for a window too short for the real thing; still fills the time and ends at the gate. */
    private static List<Leg> squeeze(Pt spawn, Pt gate, int fromT) {
        int window = -fromT;
        int walk = Math.max((int) (window * 0.8), 1);
        int wait = window - walk;
        return wait > 0 ? List.of(new Leg(fromT, fromT + wait, spawn, spawn), new Leg(fromT + wait, 0, spawn, gate)) : List.of(new Leg(fromT, 0, spawn, gate));
    }
}
