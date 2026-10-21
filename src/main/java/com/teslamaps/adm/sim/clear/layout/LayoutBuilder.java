package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/LayoutBuilder.kt

import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import com.teslamaps.adm.sim.pack.rooms.CoreEntry;
import com.teslamaps.adm.sim.pack.rooms.OdinCore;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.RoomDesign;
import com.teslamaps.adm.sim.pack.rooms.RoomPack;
import com.teslamaps.adm.sim.pack.rooms.RoomVolume;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.PlacedDoor;
import com.teslamaps.adm.sim.run.PlacedRoom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LayoutBuilder {
    private LayoutBuilder() {}

    public static class Built {
        public final List<RoomInstance> instances;
        public final Map<Slot, DoorKind> doors;
        public final List<Edit> edits;
        public final List<String> warnings;

        public Built(List<RoomInstance> instances, Map<Slot, DoorKind> doors, List<Edit> edits, List<String> warnings) {
            this.instances = instances;
            this.doors = doors;
            this.edits = edits;
            this.warnings = warnings;
        }
    }

    public interface Result {
        final class Ok implements Result {
            public final Built built;

            public Ok(Built built) { this.built = built; }
        }

        final class Failed implements Result {
            public final String message;

            public Failed(String message) { this.message = message; }
        }
    }

    public static Map<Slot, DoorKind> doorKinds(DungeonPlan plan) {
        LinkedHashMap<Slot, DoorKind> m = new LinkedHashMap<>();
        for (PlacedDoor d : plan.doors) m.put(new Slot(d.axis, d.a.x, d.a.z), d.kind);
        return m;
    }

    public static Result build(DungeonPlan plan, RoomPack rooms) { return build(plan, rooms, doorKinds(plan)); }

    public static Result build(DungeonPlan plan, RoomPack rooms, Map<Slot, DoorKind> doors) {
        ArrayList<RoomInstance> instances = new ArrayList<>(plan.rooms.size());
        for (PlacedRoom r : plan.rooms) {
            RoomDesign design = r.design;
            if (design == null) return new Result.Failed(r.name + " has no design in the room library, so " + plan.layoutId + " cannot be built.");
            Placement placement = r.placement;
            if (placement == null) return new Result.Failed(r.name + " has no placement in " + plan.layoutId + ".");
            RoomVolume volume = rooms.volumeFor(design, r.variantId);
            if (volume == null) return new Result.Failed("The blocks of " + r.name + " (" + design.id + ") could not be read from the room library, so " + plan.layoutId + " cannot be built.");
            instances.add(new RoomInstance(design, volume, placement));
        }
        return new Result.Ok(assemble(instances, doors, rooms, plan.seed));
    }

    public static Built assemble(List<RoomInstance> instances, Map<Slot, DoorKind> doors, RoomPack rooms) { return assemble(instances, doors, rooms, 0L); }

    public static Built assemble(List<RoomInstance> instances, Map<Slot, DoorKind> doors, RoomPack rooms, long seed) {
        ArrayList<String> warnings = new ArrayList<>();
        if (rooms != null) for (RoomInstance r : instances) {
            for (Tile t : Placement.canonicalTiles(r.design.shape)) {
                int core = OdinCore.ofVolume(r.volume, 32 * t.x + 15, 32 * t.z + 15);
                CoreEntry entry = rooms.designByCore(core);
                String name = null;
                if (entry != null) {
                    RoomDesign d = rooms.design(entry.design);
                    name = d != null ? d.name : null;
                }
                Tile at = r.placement.worldTileOf(t.x, t.z);
                if (entry == null) warnings.add(r.design.name + ": Odin will not recognise tile " + at.x + "," + at.z + " (core " + core + " is not in the library's table).");
                else if (!Objects.equals(name, r.design.name)) warnings.add(r.design.name + ": Odin will call tile " + at.x + "," + at.z + " " + name + ".");
            }
        }
        return new Built(instances, doors, RegionEdits.build(instances, doors, seed), warnings);
    }
}
