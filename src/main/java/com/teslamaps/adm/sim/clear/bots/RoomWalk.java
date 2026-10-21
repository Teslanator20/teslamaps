// Ported from ADM's p3sim/clear/bots/RoomWalk.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** How a teammate moves in a room: a slow wander that now and then goes to a secret, filling the room's time exactly. Pure. */
public final class RoomWalk {
    private RoomWalk() {}

    /** The speed of a stretch is log-normal about this median, blocks a tick, scaled by the class's pace. */
    public static final double MEDIAN_BPT = 0.38;
    public static final double SIGMA = 0.92;
    public static final double MIN_BPT = 0.06;
    public static final double MAX_BPT = 2.0;

    public static final double SECRET_BIAS = 0.6;
    public static final double PAUSE_CHANCE = 0.5;
    public static final int MIN_PAUSE = 10;
    public static final int MAX_PAUSE = 100;

    /** The speed of one stretch for a class that moves at [classBpt]. */
    public static double stretchSpeed(KRandom random, double classBpt) {
        double z = PlanMath.gauss(random);
        return Math.clamp(MEDIAN_BPT * Math.clamp(classBpt / 1.2, 0.6, 1.5) * Math.exp(SIGMA * z), MIN_BPT, MAX_BPT);
    }

    /** Legs that fill exactly [t0] to [t1] for a walker starting at [from]. Returns the legs and where the walker ends up. */
    public static Pair<List<Leg>, Pt> legs(int t0, int t1, Pt from, List<Pt> spots, double classBpt, WalkArea area, KRandom random) {
        if (t1 <= t0) return Pair.of(new ArrayList<>(), from);
        ArrayList<Leg> legs = new ArrayList<>();
        ArrayList<Pt> left = new ArrayList<>(spots);
        int t = t0;
        Pt cur = from;
        int rounds = 0;
        while (t < t1 && rounds++ < MAX_ROUNDS) {
            Pt target;
            if (!left.isEmpty() && random.nextDouble() < SECRET_BIAS) {
                final Pt here = cur;
                ArrayList<Pt> ordered = new ArrayList<>(left);
                ordered.sort(Comparator.comparingDouble(p -> p.dist(here)));
                Pt s = ordered.get(ordered.size() > 1 && random.nextDouble() < 0.25 ? 1 : 0);
                left.remove(s);
                target = s;
            } else {
                Pt w = area.wander(cur, random);
                target = w != null ? w : cur;
            }
            for (Pt p : area.route(cur, target)) {
                double d = cur.dist(p);
                if (d < 0.05) { cur = p; continue; }
                int ticks = Math.max(1, (int) Math.ceil(d / stretchSpeed(random, classBpt)));
                if (t + ticks >= t1) {
                    Pt end = cur.lerp(p, (double) (t1 - t) / ticks);
                    legs.add(new Leg(t, t1, cur, end));
                    t = t1;
                    cur = end;
                    break;
                }
                legs.add(new Leg(t, t + ticks, cur, p));
                t += ticks;
                cur = p;
            }
            if (t < t1 && random.nextDouble() < PAUSE_CHANCE) {
                int n = Math.min(random.nextInt(MIN_PAUSE, MAX_PAUSE + 1), t1 - t);
                legs.add(new Leg(t, t + n, cur, cur));
                t += n;
            }
        }
        if (t < t1) legs.add(new Leg(t, t1, cur, cur));
        return Pair.of(legs, cur);
    }

    private static final int MAX_ROUNDS = 400;
}
