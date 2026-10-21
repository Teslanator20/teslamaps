// Ported from ADM's p3sim/clear/hud/MapModel.kt
package com.teslamaps.adm.sim.clear.hud;

import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.Tile;
import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import com.teslamaps.adm.sim.run.CheckMark;
import com.teslamaps.adm.sim.run.DoorPhase;
import com.teslamaps.adm.sim.run.DoorRecord;
import com.teslamaps.adm.sim.run.DungeonPlan;
import com.teslamaps.adm.sim.run.PlacedDoor;
import com.teslamaps.adm.sim.run.PlayerRecord;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.RoomType;
import com.teslamaps.adm.sim.run.RunState;
import com.teslamaps.adm.sim.run.api.MapCanvas;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The dungeon map as Hypixel draws it: a 128 x 128 raster, the 6 x 6 room grid at pixel (5,5), rooms 16 pixels square on a 20 pixel
 * pitch, doors 5 x 4 in the gap, check marks as a 9 row glyph. Pure; the packet is built in HudPackets.
 */
public final class MapModel {
    private MapModel() {}

    public static final int SIZE = 128;
    public static final int ORIGIN = 5;
    public static final int PITCH = 20;
    public static final int ROOM = 16;

    // Map colour bytes (colour id * 4 + brightness) as recorded.
    public static final int C_EMPTY = 0;
    public static final int C_UNKNOWN = 85;
    public static final int C_QUESTION = 119;
    public static final int C_NORMAL = 63;
    public static final int C_TRAP = 62;
    public static final int C_PUZZLE = 66;
    public static final int C_YELLOW = 74;
    public static final int C_FAIRY = 82;
    public static final int C_BLOOD = 18;
    public static final int C_ENTRANCE = 30;
    public static final int C_GREEN = 30;
    public static final int C_WHITE = 34;
    public static final int C_RED = 18;
    public static final int C_WITHER_DOOR = 119;

    public static int roomColour(RoomType t) {
        return switch (t) {
            case NORMAL, RARE -> C_NORMAL;
            case PUZZLE -> C_PUZZLE;
            case TRAP -> C_TRAP;
            case YELLOW -> C_YELLOW;
            case FAIRY -> C_FAIRY;
            case BLOOD -> C_BLOOD;
            case ENTRANCE -> C_ENTRANCE;
        };
    }

    // The check glyph, rows 4 to 12 of the 16 x 16 tile.
    private static final List<String> CHECK = List.of(
            "...........XX...",
            "..........XXX...",
            ".........XXX....",
            "........XXX.....",
            "....XX.XXX......",
            "....XXXXX.......",
            "....XXXX........",
            ".....XXX........",
            ".....XX.........");
    private static final int CHECK_ROW0 = 4;

    // The "?" of a room that is known but not entered (tile-local column, row).
    private static final int[][] QUESTION = {{6, 5}, {7, 5}, {8, 5}, {5, 6}, {9, 6}, {9, 7}, {8, 8}, {7, 9}, {7, 10}, {7, 12}, {7, 13}};

    /** Whether the map shows room in its own colour. */
    public static boolean isShown(RunState state, int room) {
        RoomRecord r = state.rooms.get(room);
        if (r.plan.type == RoomType.ENTRANCE || !r.visitors.isEmpty()) return true;
        switch (r.check) {
            case UNDISCOVERED -> { return false; }
            case WHITE, GREEN, RED -> { return true; }
            default -> {}
        }
        for (PlacedDoor d : state.plan.doors) {
            if (d.kind.connected() && d.kind != DoorKind.FAIRY && state.doors.get(d.index).phase != DoorPhase.CLOSED && (d.roomA == room || d.roomB == room)) {
                RoomRecord o = state.rooms.get(d.roomA == room ? d.roomB : d.roomA);
                if (o.plan.type == RoomType.ENTRANCE || !o.visitors.isEmpty()) return true;
            }
        }
        return false;
    }

    /** The picture for the run's current state. A fresh array every call. */
    public static byte[] render(RunState state) {
        ByteMapCanvas canvas = new ByteMapCanvas();
        renderInto(state, canvas);
        return canvas.bytes;
    }

    public static void renderInto(RunState state, MapCanvas canvas) {
        DungeonPlan plan = state.plan;
        boolean[] shown = new boolean[plan.rooms.size()];
        for (int i = 0; i < shown.length; i++) shown[i] = isShown(state, i);
        // A room next to a shown room but not entered is drawn as ONE grey tile with a "?": the tile on the far side of the door.
        LinkedHashSet<Tile> grey = new LinkedHashSet<>();
        for (PlacedDoor d : plan.doors) {
            if (!d.kind.connected() || d.roomA == d.roomB || !inRange(d.roomA, shown) || !inRange(d.roomB, shown)) continue;
            if (shown[d.roomA] && !shown[d.roomB]) grey.add(d.b);
            else if (shown[d.roomB] && !shown[d.roomA]) grey.add(d.a);
        }

        for (RoomRecord room : state.rooms) {
            if (!shown[room.index()]) continue;
            int colour = roomColour(room.plan.type);
            List<Tile> tiles = room.plan.tiles;
            for (Tile t : tiles) fill(canvas, px(t.x), px(t.z), ROOM, ROOM, colour);
            // One piece: join neighbouring tiles of the same room.
            Set<Tile> set = new HashSet<>(tiles);
            for (Tile t : tiles) {
                if (set.contains(new Tile(t.x + 1, t.z))) fill(canvas, px(t.x) + ROOM, px(t.z), PITCH - ROOM, ROOM, colour);
                if (set.contains(new Tile(t.x, t.z + 1))) fill(canvas, px(t.x), px(t.z) + ROOM, ROOM, PITCH - ROOM, colour);
                if (set.contains(new Tile(t.x + 1, t.z)) && set.contains(new Tile(t.x, t.z + 1)) && set.contains(new Tile(t.x + 1, t.z + 1)))
                    fill(canvas, px(t.x) + ROOM, px(t.z) + ROOM, PITCH - ROOM, PITCH - ROOM, colour);
            }
        }
        for (Tile t : grey) {
            fill(canvas, px(t.x), px(t.z), ROOM, ROOM, C_UNKNOWN);
            for (int[] q : QUESTION) canvas.set(px(t.x) + q[0], px(t.z) + q[1], C_QUESTION);
        }

        for (PlacedDoor d : plan.doors) {
            if (!d.kind.connected() || d.roomA == d.roomB || !inRange(d.roomA, shown) || !inRange(d.roomB, shown)) continue;
            if (!shown[d.roomA] && !shown[d.roomB]) continue;
            DoorRecord rec = state.doors.get(d.index);
            boolean open = rec.phase != DoorPhase.CLOSED;
            int colour = doorColour(state, d.kind, open, d.roomA, d.roomB, shown);
            if (d.axis == 'E') fill(canvas, px(d.a.x) + ROOM, px(d.a.z) + 6, PITCH - ROOM, 5, colour);
            else fill(canvas, px(d.a.x) + 6, px(d.a.z) + ROOM, 5, PITCH - ROOM, colour);
        }

        for (RoomRecord room : state.rooms) {
            int i = room.index();
            if (!shown[i]) continue;
            Tile first = null;
            for (Tile t : room.plan.tiles) if (first == null || t.z < first.z || (t.z == first.z && t.x < first.x)) first = t;
            if (first == null) continue;
            int x0 = px(first.x);
            int y0 = px(first.z);
            Integer mark = switch (room.check) {
                case WHITE -> C_WHITE;
                case GREEN -> C_GREEN;
                case RED -> C_RED;
                default -> null;
            };
            if (mark != null) {
                Map<Integer, String> rows = new LinkedHashMap<>();
                for (int r = 0; r < CHECK.size(); r++) rows.put(CHECK_ROW0 + r, CHECK.get(r));
                glyph(canvas, x0, y0, rows, mark);
            }
        }
    }

    private static boolean inRange(int i, boolean[] a) { return i >= 0 && i < a.length; }

    private static int doorColour(RunState state, DoorKind kind, boolean open, int a, int b, boolean[] shown) {
        if (!open) {
            if (kind == DoorKind.WITHER) return C_WITHER_DOOR;
            if (kind == DoorKind.BLOOD) return C_BLOOD;
        }
        // The free door to the Fairy room is drawn like a wither door until somebody is in the room.
        if (kind == DoorKind.FAIRY && (!shown[a] || !shown[b])) return C_WITHER_DOOR;
        if (!open && kind == DoorKind.FAIRY) return C_FAIRY;
        // An open plain door takes the colour of the room behind it (the one found later).
        if (!shown[a] || !shown[b]) return C_UNKNOWN;
        RoomRecord ra = state.rooms.get(a);
        RoomRecord rb = state.rooms.get(b);
        int da = ra.tDiscovered != null ? ra.tDiscovered : Integer.MIN_VALUE;
        int db = rb.tDiscovered != null ? rb.tDiscovered : Integer.MIN_VALUE;
        RoomRecord later = da > db ? ra : rb;
        RoomRecord pick = later.plan.type == RoomType.ENTRANCE ? (later == ra ? rb : ra) : later;
        return roomColour(pick.plan.type);
    }

    private static int px(int tile) { return ORIGIN + PITCH * tile; }

    private static void fill(MapCanvas c, int x, int y, int w, int h, int colour) {
        for (int yy = y; yy < y + h; yy++) for (int xx = x; xx < x + w; xx++) c.set(xx, yy, colour);
    }

    private static void glyph(MapCanvas c, int x0, int y0, Map<Integer, String> rows, int colour) {
        for (Map.Entry<Integer, String> e : rows.entrySet()) {
            String line = e.getValue();
            for (int col = 0; col < line.length(); col++) if (line.charAt(col) == 'X') c.set(x0 + col, y0 + e.getKey(), colour);
        }
    }

    /** Decorations for viewer's map: every living teammate as a blue marker in tab order, then the viewer's own frame. */
    public static List<MapMark> decorations(HudContext ctx, UUID viewer, Function<UUID, Float> yaw) {
        List<MapMark> out = new ArrayList<>();
        List<PlayerRecord> members = ctx.membersFor(viewer);
        for (PlayerRecord p : members) {
            if (p.ghost || Objects.equals(p.uuid, viewer)) continue;
            out.add(mark(false, p.x, p.z, yaw.apply(p.uuid)));
        }
        PlayerRecord me = null;
        for (PlayerRecord p : members) if (Objects.equals(p.uuid, viewer)) { me = p; break; }
        if (me != null) out.add(mark(true, me.x, me.z, yaw.apply(me.uuid)));
        return out;
    }

    public static List<MapMark> decorations(HudContext ctx, UUID viewer) { return decorations(ctx, viewer, u -> 0f); }

    /** The map pixel of a world coordinate, as the packet's signed byte `2 * pixel - 128`; the rotation in sixteenths of a turn. */
    public static MapMark mark(boolean frame, double x, double z, float yawDeg) {
        int rot = Math.floorMod(Math.round(yawDeg / 22.5f), 16);
        return new MapMark(frame, b(x, OFFSET_X), b(z, OFFSET_Z), rot);
    }

    private static int b(double v, double c) {
        return Math.clamp(2 * (int) Math.floor((v - DungeonFrame.ORIGIN) * PITCH / DungeonFrame.PITCH + ORIGIN + c) - 128, -128, 126);
    }

    public static final double OFFSET_X = 4.9;
    public static final double OFFSET_Z = 5.2;
}
