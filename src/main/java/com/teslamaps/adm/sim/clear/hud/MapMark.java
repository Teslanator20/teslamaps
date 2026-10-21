// Ported from ADM's p3sim/clear/hud/MapModel.kt
package com.teslamaps.adm.sim.clear.hud;

import java.util.Objects;

/** One map decoration: a blue marker for a teammate or the frame for the viewer. x, y are the signed bytes of the packet. */
public final class MapMark {
    public final boolean frame;
    public final int x;
    public final int y;
    public final int rot;

    public MapMark(boolean frame, int x, int y, int rot) {
        this.frame = frame;
        this.x = x;
        this.y = y;
        this.rot = rot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MapMark m)) return false;
        return frame == m.frame && x == m.x && y == m.y && rot == m.rot;
    }

    @Override
    public int hashCode() { return Objects.hash(frame, x, y, rot); }

    @Override
    public String toString() { return "MapMark(frame=" + frame + ", x=" + x + ", y=" + y + ", rot=" + rot + ")"; }
}
