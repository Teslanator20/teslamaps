// Ported from ADM's p3sim/clear/blood/BloodRoom.kt
package com.teslamaps.adm.sim.clear.blood;

import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.RoomVolume;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.PlacedDoor;
import com.teslamaps.adm.sim.run.PlacedRoom;
import com.teslamaps.adm.sim.run.plugin.BloodRoomSpec;
import com.teslamaps.adm.sim.run.plugin.HeadSlot;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.adm.sim.util.Triple;
import java.util.ArrayList;
import java.util.List;

/** The blood room as the camp sees it, in world coordinates. */
public class BloodRoom implements BloodRoomSpec {
    private final int roomIndex;
    private final String designId;
    private final String buildKey;
    private final double doorX, doorY, doorZ;
    /** The middle of the room at floor height (his body hangs RISE higher, see watcherHomeY). */
    private final double watcherX, watcherY, watcherZ;
    private final List<HeadSlot> heads;
    private final List<double[]> mobCells;
    /** Clockwise quarter turns (0 SOUTH, 1 WEST, 2 NORTH, 3 EAST). */
    public final int quarterTurns;
    /** The exit alcove, or null when the room's blocks are not available. */
    public final BloodGeometry.ExitSite exit;

    public BloodRoom(int roomIndex, String designId, String buildKey, double doorX, double doorY, double doorZ,
                     double watcherX, double watcherY, double watcherZ, List<HeadSlot> heads, List<double[]> mobCells,
                     int quarterTurns, BloodGeometry.ExitSite exit) {
        this.roomIndex = roomIndex;
        this.designId = designId;
        this.buildKey = buildKey;
        this.doorX = doorX;
        this.doorY = doorY;
        this.doorZ = doorZ;
        this.watcherX = watcherX;
        this.watcherY = watcherY;
        this.watcherZ = watcherZ;
        this.heads = heads;
        this.mobCells = mobCells;
        this.quarterTurns = quarterTurns;
        this.exit = exit;
    }

    public BloodRoom(int roomIndex, String designId, String buildKey, double doorX, double doorY, double doorZ,
                     double watcherX, double watcherY, double watcherZ, List<HeadSlot> heads, List<double[]> mobCells, int quarterTurns) {
        this(roomIndex, designId, buildKey, doorX, doorY, doorZ, watcherX, watcherY, watcherZ, heads, mobCells, quarterTurns, null);
    }

    public BloodRoom(int roomIndex, String designId, String buildKey, double doorX, double doorY, double doorZ,
                     double watcherX, double watcherY, double watcherZ, List<HeadSlot> heads, List<double[]> mobCells) {
        this(roomIndex, designId, buildKey, doorX, doorY, doorZ, watcherX, watcherY, watcherZ, heads, mobCells, 0, null);
    }

    public BloodRoom(int roomIndex, String designId, String buildKey, double doorX, double doorY, double doorZ,
                     double watcherX, double watcherY, double watcherZ, List<HeadSlot> heads) {
        this(roomIndex, designId, buildKey, doorX, doorY, doorZ, watcherX, watcherY, watcherZ, heads, new ArrayList<>(), 0, null);
    }

    @Override public int roomIndex() { return roomIndex; }
    @Override public String designId() { return designId; }
    @Override public String buildKey() { return buildKey; }
    @Override public double doorX() { return doorX; }
    @Override public double doorY() { return doorY; }
    @Override public double doorZ() { return doorZ; }
    @Override public double watcherX() { return watcherX; }
    @Override public double watcherY() { return watcherY; }
    @Override public double watcherZ() { return watcherZ; }
    @Override public List<HeadSlot> heads() { return heads; }
    @Override public List<double[]> mobCells() { return mobCells; }

    /** Where the Watcher's feet are when the room opens. */
    public double watcherHomeY() { return watcherY + WatcherStats.RISE; }

    /** The yaw he faces: toward the blood door. */
    public float watcherYaw() { return WatcherStats.yawTowardDoor(quarterTurns); }

    /** Standing height assumed without the room's blocks. */
    public static final double DEFAULT_STAND_Y = 69.0;

    /** Centre y of the blood door's 4-block body. */
    public static final double DOOR_CENTRE_Y = 71.0;

    /** The blood room of the plan, or null when there is none. */
    public static BloodRoom of(DungeonPlan plan, RoomVolume volume) {
        PlacedRoom room = plan.blood();
        if (room == null) return null;
        return of(plan, room, volume);
    }

    public static BloodRoom of(DungeonPlan plan, PlacedRoom room, RoomVolume volume) {
        PlacedDoor door = null;
        for (PlacedDoor d : plan.doors) if (d.kind == DoorKind.BLOOD) { door = d; break; }
        int dx, dz;
        if (door != null) {
            Pair<Integer, Integer> s = door.spot();
            dx = s.first; dz = s.second;
        } else {
            dx = tileCentre(room, true); dz = tileCentre(room, false);
        }
        Placement place = room.placement;
        double cx = tileCentre(room, true) + 0.5;
        double cz = tileCentre(room, false) + 0.5;
        double cy = DEFAULT_STAND_Y;
        List<HeadSlot> heads = new ArrayList<>();
        BloodGeometry.ExitSite exit = null;
        if (volume != null && place != null) {
            Triple<Double, Double, Double> c = BloodGeometry.worldCentre(volume, place);
            if (c != null) { cx = c.first; cy = c.second; cz = c.third; }
            heads = BloodGeometry.worldSlots(volume, place);
            Integer sy = BloodGeometry.standY(volume);
            if (sy != null) exit = BloodGeometry.exitSite(place, sy);
        }
        int q = place != null ? place.quarterTurns() : room.rotation.q;
        return new BloodRoom(room.index, room.designId, room.design != null ? room.design.buildKey : null, dx + 0.5, DOOR_CENTRE_Y, dz + 0.5,
                cx, cy, cz, heads, new ArrayList<>(), q, exit);
    }

    private static int tileCentre(PlacedRoom room, boolean x) {
        Tile t = room.tiles.get(0);
        return DungeonFrame.tileCentre(x ? t.x : t.z);
    }
}
