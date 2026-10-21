// Ported from ADM's p3sim/run/api/PuzzleApi.kt
package com.teslamaps.adm.sim.run.api;

import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.RoomDesign;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.PuzzleRecord;
import com.teslamaps.adm.sim.run.Run;
import java.util.ArrayList;
import java.util.List;

/** What a puzzle knows about its place. */
public class PuzzleContext {
    public final Run run;
    public final PlacedRoom room;
    public final PuzzleRecord record;
    public final PuzzleHost host;

    public PuzzleContext(Run run, PlacedRoom room, PuzzleRecord record, PuzzleHost host) {
        this.run = run;
        this.room = room;
        this.record = record;
        this.host = host;
    }

    /** World positions of a named anchor of the room's design (chest, chest1..3, pillar1..3); empty if absent. */
    public List<WorldPos> anchor(String name) {
        RoomDesign design = room.design;
        if (design == null) return List.of();
        Placement placement = room.placement;
        if (placement == null) return List.of();
        if (design.puzzle == null || design.puzzle.anchors == null) return List.of();
        List<Pos> l = design.puzzle.anchors.get(name);
        if (l == null) return List.of();
        List<WorldPos> out = new ArrayList<>();
        for (Pos p : l) out.add(placement.toWorld(p.x, p.y, p.z));
        return out;
    }
}
