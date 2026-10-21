// Ported from ADM's p3sim/clear/blood/BloodGeometry.kt
package com.teslamaps.adm.sim.clear.blood;

import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.pack.rooms.RoomVolume;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.run.plugin.HeadSlot;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.adm.sim.util.Triple;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Where things stand in a blood room build (pure). Canonical frame: 31 by 31, blood door on the north wall (z 0), middle column
 * (15, 15), floor y 69.
 */
public final class BloodGeometry {
    private BloodGeometry() {}

    public static final int ROWS = 3;
    public static final int PER_ROW = 10;

    /** Station offsets from the room's middle column (x, z). */
    public static final List<int[]> STATION_OFFSETS = List.of(
            new int[]{-12, -4}, new int[]{-12, 0}, new int[]{-12, 4},
            new int[]{12, -4}, new int[]{12, 0}, new int[]{12, 4},
            new int[]{-9, -12}, new int[]{9, -12}, new int[]{-9, 12}, new int[]{9, 12}
    );

    public static final int[] ROW_RISES = {2, 6, 10};

    public static final double STAND_ABOVE_CELL = 0.75;

    public static final int EXIT_Z = 29;
    public static final int PORTAL_X_MIN = 14;
    public static final int PORTAL_X_MAX = 16;
    public static final int PORTAL_HEIGHT = 4;
    public static final int STRIKE_X = 15;

    /** A canonical-frame head slot. */
    public static final class CanonSlot {
        public final int x;
        public final int y;
        public final int z;
        public final String facing;

        public CanonSlot(int x, int y, int z, String facing) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.facing = facing;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            return o instanceof CanonSlot s && x == s.x && y == s.y && z == s.z && Objects.equals(facing, s.facing);
        }
        @Override public int hashCode() { return Objects.hash(x, y, z, facing); }
        @Override public String toString() { return "CanonSlot(x=" + x + ", y=" + y + ", z=" + z + ", facing=" + facing + ")"; }
    }

    public static Pair<Integer, Integer> centre(RoomVolume vol) { return Pair.of(vol.w / 2, vol.d / 2); }

    /** The y a player stands at in the middle of the room, or null. */
    public static Integer standY(RoomVolume vol) {
        int cx = vol.w / 2, cz = vol.d / 2;
        for (int y = vol.y0; y < vol.y0 + vol.h - 3; y++) {
            if (!vol.isAir(cx, y, cz) && vol.isAir(cx, y + 1, cz) && vol.isAir(cx, y + 2, cz) && vol.isAir(cx, y + 3, cz)) return y + 1;
        }
        return null;
    }

    public static List<CanonSlot> canonicalSlots(RoomVolume vol) {
        Integer stand = standY(vol);
        if (stand == null) return new ArrayList<>();
        int cx = vol.w / 2, cz = vol.d / 2;
        List<CanonSlot> out = new ArrayList<>(ROWS * PER_ROW);
        for (int rise : ROW_RISES) for (int[] o : STATION_OFFSETS) {
            int ox = o[0], oz = o[1];
            out.add(new CanonSlot(cx + ox, stand + rise, cz + oz, facingTowardMiddle(ox, oz)));
        }
        return out;
    }

    private static String facingTowardMiddle(int ox, int oz) {
        if (ox < 0 && Math.abs(ox) >= Math.abs(oz)) return "east";
        if (ox > 0 && Math.abs(ox) >= Math.abs(oz)) return "west";
        if (oz < 0) return "south";
        return "north";
    }

    /** Turns a canonical facing word by clockwise quarter turns. */
    public static String rotateFacing(String facing, int quarterTurns) {
        List<String> order = List.of("north", "east", "south", "west");
        int i = order.indexOf(facing);
        if (i < 0) return facing;
        return order.get(Math.floorMod(i + quarterTurns, 4));
    }

    public static List<HeadSlot> worldSlots(RoomVolume vol, Placement place) {
        List<CanonSlot> c = canonicalSlots(vol);
        List<HeadSlot> out = new ArrayList<>(c.size());
        for (int i = 0; i < c.size(); i++) {
            CanonSlot s = c.get(i);
            WorldPos w = place.toWorld(s.x, s.y, s.z);
            out.add(new HeadSlot(i, w.x, w.y, w.z, rotateFacing(s.facing, place.quarterTurns())));
        }
        return out;
    }

    /** World (x, z) of the middle column and the standing y there; null without a floor. */
    public static Triple<Double, Double, Double> worldCentre(RoomVolume vol, Placement place) {
        Integer y = standY(vol);
        if (y == null) return null;
        int cx = vol.w / 2, cz = vol.d / 2;
        WorldPos w = place.toWorld(cx, y, cz);
        return new Triple<>(w.x + 0.5, (double) w.y, w.z + 0.5);
    }

    /** The exit alcove in the world. */
    public static class ExitSite {
        public final List<WorldPos> cells;
        /** True when the portal plane runs along x. */
        public final boolean axisX;
        public final double strikeX;
        public final double strikeY;
        public final double strikeZ;
        public final double minX;
        public final double maxX;
        public final double minY;
        public final double maxY;
        public final double minZ;
        public final double maxZ;

        public ExitSite(List<WorldPos> cells, boolean axisX, double strikeX, double strikeY, double strikeZ) {
            this.cells = cells;
            this.axisX = axisX;
            this.strikeX = strikeX;
            this.strikeY = strikeY;
            this.strikeZ = strikeZ;
            if (cells.isEmpty()) throw new java.util.NoSuchElementException();
            int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
            for (WorldPos c : cells) {
                x0 = Math.min(x0, c.x); x1 = Math.max(x1, c.x);
                y0 = Math.min(y0, c.y); y1 = Math.max(y1, c.y);
                z0 = Math.min(z0, c.z); z1 = Math.max(z1, c.z);
            }
            this.minX = x0;
            this.maxX = x1 + 1.0;
            this.minY = y0;
            this.maxY = y1 + 1.0;
            this.minZ = z0;
            this.maxZ = z1 + 1.0;
        }

        /** Whether a body with feet at (x, y, z) overlaps the portal blocks. */
        public boolean touches(double x, double y, double z) {
            return x + HALF_WIDTH > minX && x - HALF_WIDTH < maxX
                    && y + HEIGHT > minY && y < maxY
                    && z + HALF_WIDTH > minZ && z - HALF_WIDTH < maxZ;
        }

        public static final double HALF_WIDTH = 0.3;
        public static final double HEIGHT = 1.8;
    }

    /** The exit alcove of a room placed as place whose floor is floorY. */
    public static ExitSite exitSite(Placement place, int floorY) {
        List<WorldPos> cells = new ArrayList<>(3 * PORTAL_HEIGHT);
        for (int dy = 0; dy < PORTAL_HEIGHT; dy++) for (int x = PORTAL_X_MIN; x <= PORTAL_X_MAX; x++) cells.add(place.toWorld(x, floorY + dy, EXIT_Z));
        WorldPos s = place.toWorld(STRIKE_X, floorY, EXIT_Z);
        return new ExitSite(cells, place.quarterTurns() % 2 == 0, s.x + 0.5, floorY + 0.5, s.z + 0.5);
    }
}
