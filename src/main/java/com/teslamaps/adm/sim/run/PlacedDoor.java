// Ported from ADM's p3sim/run/DungeonPlan.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.pack.DoorModel;
import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import com.teslamaps.adm.sim.util.Pair;
import java.util.List;

/** One door slot between two different rooms. */
public class PlacedDoor {
    public final int index;
    /** 'E': (a) and the tile east of it; 'S': (a) and the tile south of it. */
    public final char axis;
    public final Tile a;
    public final Tile b;
    public final int roomA;
    public final int roomB;
    public final DoorKind kind;
    /** When the recorded run's door fell (ticks from T0), or null. */
    public final Integer recordedOpen;

    public PlacedDoor(int index, char axis, Tile a, Tile b, int roomA, int roomB, DoorKind kind, Integer recordedOpen) {
        this.index = index;
        this.axis = axis;
        this.a = a;
        this.b = b;
        this.roomA = roomA;
        this.roomB = roomB;
        this.kind = kind;
        this.recordedOpen = recordedOpen;
    }

    /** World (x, z) of the door's centre. */
    public Pair<Integer, Integer> spot() { return DungeonFrame.doorSpot(axis, a.x, a.z); }

    /** The 36 cells of the door's body. */
    public List<Pos> bodyCells() {
        Pair<Integer, Integer> s = spot();
        return DoorModel.bodyCells(s.first, s.second);
    }

    public boolean contains(int x, int y, int z) {
        Pair<Integer, Integer> s = spot();
        return DoorModel.inBody(s.first, s.second, x, y, z);
    }

    /** The closed body block, or null when the door has no body. */
    public String bodyBlock() { return DoorModel.bodyBlock(kind); }

    /** A WITHER or BLOOD door. */
    public boolean needsKey() { return kind == DoorKind.WITHER || kind == DoorKind.BLOOD; }
}
