// Ported from ADM's p3sim/clear/bots/RoomWalk.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.List;

/** A floor that is the room's tiles with nothing in them. The stand-in when a room has no block data. */
public class FreeArea implements WalkArea {
    private final List<Rect> rects;

    public FreeArea(List<Rect> rects) { this.rects = rects; }

    @Override
    public Pt wander(Pt from, KRandom random) {
        if (rects.isEmpty()) return null;
        double total = 0.0;
        for (Rect r : rects) total += r.area();
        double pick = random.nextDouble() * total;
        Rect r = null;
        for (Rect it : rects) { pick -= it.area(); if (pick < 0) { r = it; break; } }
        if (r == null) r = rects.get(rects.size() - 1);
        return new Pt(r.x0 + random.nextDouble() * (r.x1 - r.x0), r.z0 + random.nextDouble() * (r.z1 - r.z0));
    }

    @Override
    public List<Pt> route(Pt a, Pt b) { return List.of(b); }

    /** Blocks kept clear of the walls of a tile. */
    public static final double MARGIN = 3.0;

    public static FreeArea of(PlacedRoom room) {
        ArrayList<Rect> out = new ArrayList<>();
        for (Tile t : room.tiles) {
            double x = DungeonFrame.tileMin(t.x);
            double z = DungeonFrame.tileMin(t.z);
            out.add(new Rect(x + MARGIN, z + MARGIN, x + DungeonFrame.TILE_SIZE - MARGIN, z + DungeonFrame.TILE_SIZE - MARGIN));
        }
        return new FreeArea(out);
    }
}
