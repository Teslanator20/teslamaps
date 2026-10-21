// Ported from ADM's p3sim/clear/bots/Leaps.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** Teammates leaping to the door opener, the way real parties move up. */
public final class Leaps {
    private Leaps() {}

    /** Chance a bot that could leap does. */
    public static final double TRY = 1.0;
    /** A bot nearer than this to the opener walks. */
    public static final double MIN_BLOCKS = 16.0;
    /** The latest a leap comes after its door (ticks). */
    public static final int LATEST = 90;
    /** A stand this long ends a walk. */
    public static final int STAND_TICKS = 20;
    /** Blocks from the opener the leaper lands. */
    public static final double LAND_BESIDE = 1.2;
    /** Measured share of the three spellings. */
    public static final List<Pair<String, Double>> SPELLINGS = List.of(Pair.of("Leaped to %s!", 0.80), Pair.of("Leaping to %s", 0.11), Pair.of("Leaped to %s", 0.09));

    /** Ticks from the door to the leap: the measured spread, drawn piecewise. */
    public static int gap(double u) { return (int) Math.round(PlanMath.curve(PlanMath.pts(0.0, 2.0, 0.1, 6.0, 0.5, 17.0, 0.9, 45.0, 1.0, 90.0), u)); }

    /** The leap line a teammate's mod posts, in that teammate's spelling. */
    public static String line(String name, double u) {
        double acc = 0.0;
        for (Pair<String, Double> p : SPELLINGS) { acc += p.second; if (u < acc) return String.format(Locale.ROOT, p.first, name); }
        return String.format(Locale.ROOT, SPELLINGS.get(0).first, name);
    }

    private static final class Opened {
        final int door;
        final int seat;
        final int openT;
        final Function<Integer, Pt> where;

        Opened(int door, int seat, int openT, Function<Integer, Pt> where) {
            this.door = door;
            this.seat = seat;
            this.openT = openT;
            this.where = where;
        }
    }

    /** The leaps of a plan and the tracks with them in. */
    public static Pair<List<SeatTrack>, List<Leap>> add(List<SeatTrack> tracks, Collection<DoorPlan> doors, Integer blood, Set<Integer> humans, LeapWorld world, KRandom rnd,
                                                        List<PersonDoor> people) {
        LinkedHashMap<Integer, SeatTrack> byseat = new LinkedHashMap<>();
        for (SeatTrack t : tracks) byseat.put(t.seat, t);
        ArrayList<Leap> leaps = new ArrayList<>();
        ArrayList<Opened> wither = new ArrayList<>();
        for (DoorPlan d : doors) {
            if (blood != null && d.door == blood) continue;
            if (humans.contains(d.seat)) continue;
            wither.add(new Opened(d.door, d.seat, d.openT, t -> {
                SeatTrack s = byseat.get(d.seat);
                return s != null ? s.positionAt(t) : null;
            }));
        }
        for (PersonDoor p : people) {
            if (blood != null && p.door == blood) continue;
            wither.add(new Opened(p.door, p.seat, p.t, t -> p.at));
        }
        wither.sort(Comparator.comparingInt(o -> o.openT));
        for (int i = 0; i < wither.size(); i++) {
            Opened d = wither.get(i);
            int nextOpen = i + 1 < wither.size() ? wither.get(i + 1).openT : Integer.MAX_VALUE;
            int at = d.openT + gap(rnd.nextDouble());
            Pt to = d.where.apply(at);
            if (to == null) continue;
            ArrayList<SeatTrack> ordered = new ArrayList<>(byseat.values());
            ordered.sort(Comparator.comparingInt(s -> s.seat));
            for (SeatTrack s : ordered) {
                if (s.seat == d.seat || humans.contains(s.seat)) continue;
                if (rnd.nextDouble() >= TRY) continue;
                int from = at;
                while (true) {
                    Act busy = null;
                    for (Act a : s.acts) if (a.t() <= from && from <= a.until()) { busy = a; break; }
                    if (busy == null) break;
                    from = busy.until() + 1;
                }
                if (from - d.openT > LATEST || from >= nextOpen) continue;
                Pt dest;
                if (from == at) dest = to;
                else {
                    dest = d.where.apply(from);
                    if (dest == null) continue;
                }
                SeatTrack tr = leapOf(s, from, dest, d.door, world);
                if (tr == null) continue;
                byseat.put(s.seat, tr);
                leaps.add(new Leap(from, s.seat, d.seat, d.door));
            }
        }
        ArrayList<SeatTrack> outTracks = new ArrayList<>();
        for (SeatTrack t : tracks) outTracks.add(byseat.get(t.seat));
        leaps.sort(Comparator.comparingInt(l -> l.t));
        return Pair.of(outTracks, leaps);
    }

    public static Pair<List<SeatTrack>, List<Leap>> add(List<SeatTrack> tracks, Collection<DoorPlan> doors, Integer blood, Set<Integer> humans, LeapWorld world, KRandom rnd) {
        return add(tracks, doors, blood, humans, world, rnd, List.of());
    }

    /** [s] leaping at [at] to beside [to] and walking on to where its plan next needs it, or null when it would not leap. */
    public static SeatTrack leapOf(SeatTrack s, int at, Pt to, int door, LeapWorld world) {
        for (Act a : s.acts) if (a.t() <= at && at <= a.until()) return null;
        Pt here = s.positionAt(at);
        if (here.dist(to) < MIN_BLOCKS) return null;
        Integer still = null;
        for (Leg l : s.legs) if (l.t0 > at && l.a.equals(l.b) && l.t1 - l.t0 >= STAND_TICKS) { still = l.t0; break; }
        Integer nextAct = null;
        for (Act a : s.acts) if (a.t() > at && (nextAct == null || a.t() < nextAct)) nextAct = a.t();
        int due = Math.min(nextAct != null ? nextAct : s.endT(), still != null ? still : s.endT());
        if (due <= at) return null;
        Pt dest = s.positionAt(due);
        if (!world.through(here, dest, door)) return null;
        double gap = to.dist(dest);
        Pt land = gap < 0.01 ? to : to.lerp(dest, Math.min(1.0, LAND_BESIDE / gap));
        List<Leg> walk = world.walk(land, dest, at);
        if (walk.isEmpty() && land.dist(dest) > 1.0) return null;
        int end = walk.isEmpty() ? at : walk.get(walk.size() - 1).t1;
        if (end > due) return null;
        ArrayList<Leg> legs = new ArrayList<>();
        for (Leg l : s.legs) {
            if (l.t1 <= at) legs.add(l);
            else if (l.t0 < at) legs.add(new Leg(l.t0, at, l.a, l.at(at), l.hop));
        }
        if (walk.isEmpty()) legs.add(new Leg(at, at, land, land)); else legs.addAll(walk);
        if (due > end) legs.add(new Leg(end, due, dest, dest));
        for (Leg l : s.legs) {
            if (l.t0 >= due) legs.add(l);
            else if (l.t1 > due) legs.add(new Leg(due, l.t1, l.at(due), l.b, l.hop));
        }
        return new SeatTrack(s.seat, legs, s.acts);
    }
}
