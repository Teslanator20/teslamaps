// Ported from ADM's p3sim/clear/secrets/CellGroups.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.pack.Pos;
import java.util.List;
import java.util.NoSuchElementException;

public class CellGroup {
    public final String id;
    public final int room;
    public final GroupKind kind;
    public final List<Pos> cells;
    public final int minX, maxX, minY, maxY, minZ, maxZ;

    public CellGroup(String id, int room, GroupKind kind, List<Pos> cells) {
        this.id = id;
        this.room = room;
        this.kind = kind;
        this.cells = cells;
        if (cells.isEmpty()) throw new NoSuchElementException();
        int ax = Integer.MAX_VALUE, bx = Integer.MIN_VALUE, ay = Integer.MAX_VALUE, by = Integer.MIN_VALUE, az = Integer.MAX_VALUE, bz = Integer.MIN_VALUE;
        for (Pos c : cells) {
            ax = Math.min(ax, c.x); bx = Math.max(bx, c.x);
            ay = Math.min(ay, c.y); by = Math.max(by, c.y);
            az = Math.min(az, c.z); bz = Math.max(bz, c.z);
        }
        minX = ax; maxX = bx; minY = ay; maxY = by; minZ = az; maxZ = bz;
    }

    public boolean within(double cx, double cy, double cz, double r) {
        if (cx < minX - r || cx > maxX + 1 + r || cy < minY - r || cy > maxY + 1 + r || cz < minZ - r || cz > maxZ + 1 + r) return false;
        double r2 = r * r;
        for (Pos c : cells) {
            double dx = c.x + 0.5 - cx, dy = c.y + 0.5 - cy, dz = c.z + 0.5 - cz;
            if (dx * dx + dy * dy + dz * dz <= r2) return true;
        }
        return false;
    }
}
