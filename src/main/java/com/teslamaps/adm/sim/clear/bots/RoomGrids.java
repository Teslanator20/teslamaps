// Ported from ADM's p3sim/clear/bots/RoomGrid.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.RoomVolume;
import com.teslamaps.adm.sim.util.Pair;

/** Makes the RoomGrid of a placed room from the room's block volume. */
public final class RoomGrids {
    private RoomGrids() {}

    /** The feet height of a walker on the dungeon's floor. */
    public static final int FEET_Y = 69;

    /** A floor with fewer walkable columns than this at the usual level is searched for another level. */
    private static final int USUAL_MIN = 150;

    /** The grid of the room [volume] stands for, placed by [placement]. */
    public static RoomGrid fromVolume(RoomVolume volume, Placement placement) {
        int n = volume.palette.size();
        boolean[] supports = new boolean[n];
        boolean[] passable = new boolean[n];
        for (int i = 0; i < n; i++) supports[i] = FloorRules.supports(volume.palette.get(i));
        for (int i = 0; i < n; i++) passable[i] = FloorRules.passable(volume.palette.get(i));
        RoomGrid usual = gridAt(volume, placement, FEET_Y, supports, passable);
        if (usual.openCount() >= USUAL_MIN) return usual;
        RoomGrid best = usual;
        for (int y = volume.y0 + 1; y <= volume.y0 + volume.h - 3; y++) {
            if (y == FEET_Y) continue;
            RoomGrid g = gridAt(volume, placement, y, supports, passable);
            if (g.openCount() > best.openCount()) best = g;
        }
        return best;
    }

    private static RoomGrid gridAt(RoomVolume volume, Placement placement, int feetY, boolean[] supports, boolean[] passable) {
        int x0 = placement.originX, z0 = placement.originZ;
        int w = placement.boxW(), d = placement.boxD();
        boolean[] open = new boolean[w * d];
        for (int wz = z0; wz < z0 + d; wz++) for (int wx = x0; wx < x0 + w; wx++) {
            if (!placement.ownsColumn(wx, wz)) continue;
            Pair<Integer, Integer> c = placement.toCanonical(wx, wz);
            if (c == null) continue;
            int cx = c.first, cz = c.second;
            if (!volume.contains(cx, feetY - 1, cz) || !volume.contains(cx, feetY + 1, cz)) continue;
            open[(wz - z0) * w + (wx - x0)] =
                    supports[volume.paletteIndexAt(cx, feetY - 1, cz)] && passable[volume.paletteIndexAt(cx, feetY, cz)] && passable[volume.paletteIndexAt(cx, feetY + 1, cz)];
        }
        return new RoomGrid(x0, z0, w, d, open);
    }
}
