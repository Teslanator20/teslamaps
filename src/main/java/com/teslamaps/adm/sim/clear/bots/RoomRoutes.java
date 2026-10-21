// Ported from ADM's p3sim/clear/bots/RoomRoutes.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.RoomDesign;
import com.teslamaps.adm.sim.pack.rooms.RoomSecret;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.List;

/** The plausible way through a room when no recorded traversal exists. Pure. */
public final class RoomRoutes {
    private RoomRoutes() {}

    /** The room's secrets in world coordinates. At most [limit] are returned. */
    public static List<SecretSpot> secretSpots(PlacedRoom room, int limit) {
        RoomDesign design = room.design;
        if (design == null) return List.of();
        Placement placement = room.placement;
        if (placement == null) return List.of();
        int n = Math.min(Math.max(limit, 0), design.secrets.size());
        ArrayList<SecretSpot> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            RoomSecret s = design.secrets.get(i);
            WorldPos it = placement.toWorld(s.pos.x, s.pos.y, s.pos.z);
            out.add(new SecretSpot(s, it.x, it.y, it.z));
        }
        return out;
    }

    public static List<SecretSpot> secretSpots(PlacedRoom room) { return secretSpots(room, Integer.MAX_VALUE); }

    /** Spots in the order a walker would visit them, starting at [from]: always the nearest one left. */
    public static List<Pt> visitOrder(Pt from, List<Pt> spots) {
        ArrayList<Pt> left = new ArrayList<>(spots);
        ArrayList<Pt> out = new ArrayList<>(spots.size());
        Pt cur = from;
        while (!left.isEmpty()) {
            Pt next = null;
            double bestD = 0.0;
            for (Pt p : left) {
                double d = p.dist(cur);
                if (next == null || d < bestD) { next = p; bestD = d; }
            }
            left.remove(next);
            out.add(next);
            cur = next;
        }
        return out;
    }

    /** Legs that fill exactly [t0] to [t1]: a wander over [area] (a box round [centre] when the floor is not known) with visits to [spots]. */
    public static Pair<List<Leg>, Pt> legs(int t0, int t1, Pt from, List<Pt> spots, Pt centre, double bpt, KRandom random, WalkArea area) {
        WalkArea a = area != null ? area : new FreeArea(List.of(new Rect(centre.x - 12.0, centre.z - 12.0, centre.x + 12.0, centre.z + 12.0)));
        return RoomWalk.legs(t0, t1, from, spots, bpt, a, random);
    }

    public static Pair<List<Leg>, Pt> legs(int t0, int t1, Pt from, List<Pt> spots, Pt centre, double bpt, KRandom random) {
        return legs(t0, t1, from, spots, centre, bpt, random, null);
    }
}
