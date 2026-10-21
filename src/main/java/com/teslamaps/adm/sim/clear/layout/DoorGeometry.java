package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/DoorGeometry.kt

import com.teslamaps.adm.sim.pack.DoorModel;
import com.teslamaps.adm.sim.pack.DungeonFrame;
import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.pack.layouts.DoorKind;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.adm.sim.util.Triple;
import java.util.ArrayList;
import java.util.List;

public final class DoorGeometry {
    private DoorGeometry() {}

    public static final int OPEN_MIN = 14;
    public static final int OPEN_MAX = 16;
    public static final int FRAME_MIN = 13;
    public static final int FRAME_MAX = 17;

    public static final String FRAME_BLOCK = "minecraft:stone_bricks";

    private static final String SIDES = "NESW";

    public static char turn(char side, int quarterTurns) {
        int i = SIDES.indexOf(side);
        if (i < 0) throw new IllegalArgumentException("side must be N, E, S or W, not '" + side + "'");
        return SIDES.charAt(Math.floorMod(i + quarterTurns, 4));
    }

    public static Slot slotOf(int tx, int tz, char side) {
        Slot s = switch (side) {
            case 'E' -> new Slot('E', tx, tz);
            case 'W' -> new Slot('E', tx - 1, tz);
            case 'S' -> new Slot('S', tx, tz);
            case 'N' -> new Slot('S', tx, tz - 1);
            default -> throw new IllegalArgumentException("side must be N, E, S or W, not '" + side + "'");
        };
        return DungeonFrame.isSlot(s.axis, s.tx, s.tz) ? s : null;
    }

    public static List<Triple<Integer, Integer, Character>> sidesOf(Slot slot) {
        if (slot.axis == 'E') return List.of(new Triple<>(slot.tx, slot.tz, 'E'), new Triple<>(slot.tx + 1, slot.tz, 'W'));
        return List.of(new Triple<>(slot.tx, slot.tz, 'S'), new Triple<>(slot.tx, slot.tz + 1, 'N'));
    }

    public static Pair<Integer, Integer> sideCell(int tx, int tz, char side, int a, int depth) {
        int ox = DungeonFrame.tileMin(tx);
        int oz = DungeonFrame.tileMin(tz);
        return switch (side) {
            case 'N' -> Pair.of(ox + a, oz + depth);
            case 'S' -> Pair.of(ox + a, oz + 30 - depth);
            case 'W' -> Pair.of(ox + depth, oz + a);
            case 'E' -> Pair.of(ox + 30 - depth, oz + a);
            default -> throw new IllegalArgumentException("side must be N, E, S or W, not '" + side + "'");
        };
    }

    public static Pair<Integer, Integer> gapCell(Slot slot, int a) {
        if (slot.axis == 'E') return Pair.of(DungeonFrame.tileMin(slot.tx) + 31, DungeonFrame.tileMin(slot.tz) + a);
        return Pair.of(DungeonFrame.tileMin(slot.tx) + a, DungeonFrame.tileMin(slot.tz) + 31);
    }

    public static List<Triple<Integer, Integer, Integer>> openingCells(int tx, int tz, char side, int depth) {
        List<Triple<Integer, Integer, Integer>> out = new ArrayList<>(12);
        for (int a = OPEN_MIN; a <= OPEN_MAX; a++) {
            Pair<Integer, Integer> c = sideCell(tx, tz, side, a, depth);
            for (int y = DoorModel.BODY_Y_MIN; y <= DoorModel.BODY_Y_MAX; y++) out.add(new Triple<>(c.first, y, c.second));
        }
        return out;
    }

    public static List<Edit> bodyEdits(Slot slot, DoorKind kind) {
        String block = DoorModel.bodyBlock(kind);
        if (block == null) return List.of();
        Pair<Integer, Integer> c = DungeonFrame.doorSpot(slot.axis, slot.tx, slot.tz);
        List<Edit> out = new ArrayList<>();
        for (Pos p : DoorModel.bodyCells(c.first, c.second)) out.add(new Edit(p.x, p.y, p.z, block));
        return out;
    }
}
