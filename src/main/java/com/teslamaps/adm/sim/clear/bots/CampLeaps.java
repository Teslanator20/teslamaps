// Ported from ADM's p3sim/clear/bots/Leaps.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Teammates leaping into the blood camp (off by default). */
public final class CampLeaps {
    private CampLeaps() {}

    /** Off: bots do not leap into the blood camp; a test turns it on. */
    public static volatile boolean enabled = false;
    public static final double CAMP_TRY = 1.0;
    /** Leaps this soon after the blood door are left out. */
    public static final int AFTER_DOOR = 20;
    /** The door id a camp leap carries: no door. */
    public static final int NO_DOOR = -1;

    /** Ticks after the blood door a camp leap comes, drawn piecewise. */
    public static int afterDoor(double u) { return (int) Math.round(PlanMath.curve(PlanMath.pts(0.0, 40.0, 0.1, 107.0, 0.5, 901.0, 0.9, 1398.0, 1.0, 1700.0), u)); }

    /** The camp leaps of a plan and the tracks with them in. */
    public static Pair<List<SeatTrack>, List<Leap>> add(List<SeatTrack> tracks, Integer camper, int bloodOpenT, int provenT, int now, Set<Integer> humans, long seed) {
        if (!enabled) return Pair.of(tracks, List.of());
        SeatTrack camp = null;
        for (SeatTrack t : tracks) if (camper != null && t.seat == camper) { camp = t; break; }
        if (camp == null) return Pair.of(tracks, List.of());
        ArrayList<Leap> leaps = new ArrayList<>();
        ArrayList<SeatTrack> out = new ArrayList<>();
        for (SeatTrack s : tracks) {
            if (s.seat == camper || humans.contains(s.seat)) { out.add(s); continue; }
            KRandom rnd = new KRandom(seed ^ ((0x5EEDL + s.seat) * -0x61c8864680b583ebL));
            if (rnd.nextDouble() >= CAMP_TRY) { out.add(s); continue; }
            int maxUntil = Integer.MIN_VALUE;
            boolean any = false;
            for (Act a : s.acts) { if (!any || a.until() > maxUntil) maxUntil = a.until(); any = true; }
            int workDone = (any ? maxUntil : Integer.MIN_VALUE) + 1;
            int at = Math.max(Math.max(bloodOpenT + afterDoor(rnd.nextDouble()), workDone), bloodOpenT + AFTER_DOOR);
            if (at >= provenT || at <= now) { out.add(s); continue; }
            Pt to = camp.positionAt(at);
            if (s.positionAt(at).dist(to) < Leaps.MIN_BLOCKS) { out.add(s); continue; }
            Pt here = s.positionAt(at);
            double gap = here.dist(to);
            Pt land = to.lerp(here, Math.min(1.0, Leaps.LAND_BESIDE / gap));
            ArrayList<Leg> legs = new ArrayList<>();
            for (Leg l : s.legs) {
                if (l.t1 <= at) legs.add(l);
                else if (l.t0 < at) legs.add(new Leg(l.t0, at, l.a, l.at(at), l.hop));
            }
            legs.add(new Leg(at, Math.max(at, Math.max(s.endT(), camp.endT())), land, land));
            leaps.add(new Leap(at, s.seat, camper, NO_DOOR));
            out.add(new SeatTrack(s.seat, legs, s.acts));
        }
        return Pair.of(out, leaps);
    }
}
