// Ported from ADM's p3sim/clear/bots/RoomWalk.kt
package com.teslamaps.adm.sim.clear.bots;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Plans the way through a room round what is in it. */
public final class RouteExpander {
    private RouteExpander() {}

    /** [route] with every stretch inside a room replaced by the way round what is in that room. Door points stay as they are. */
    public static List<RoutePoint> expand(List<RoutePoint> route, Function<Pt, WalkArea> areaAt) {
        if (route.size() < 2) return route;
        ArrayList<RoutePoint> out = new ArrayList<>(route.size() + 8);
        out.add(route.get(0));
        for (int i = 1; i < route.size(); i++) {
            Pt a = route.get(i - 1).pt;
            Pt c = route.get(i).pt;
            if (a.dist(c) > MIN_STRETCH) {
                WalkArea area = areaAt.apply(a.lerp(c, 0.5));
                if (area != null) for (Pt p : area.route(a, c)) if (p.dist(c) > 1e-6 && p.dist(out.get(out.size() - 1).pt) > 0.05) out.add(new RoutePoint(p));
            }
            out.add(new RoutePoint(c, route.get(i).gate));
        }
        return out;
    }

    /** A stretch shorter than this is not worth routing. */
    public static final double MIN_STRETCH = 2.0;
}
