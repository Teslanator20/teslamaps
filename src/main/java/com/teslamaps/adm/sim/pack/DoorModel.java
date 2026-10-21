// Ported from ADM's p3sim/pack/DoorModel.kt
package com.teslamaps.adm.sim.pack;

import com.teslamaps.adm.sim.pack.layouts.DoorKind;

import java.util.ArrayList;
import java.util.List;

public final class DoorModel {
    private DoorModel() {}

    public static final int BODY_Y_MIN = DungeonFrame.DOOR_Y_MIN;   // 69
    public static final int BODY_Y_MAX = DungeonFrame.DOOR_Y_MAX;   // 72
    /** The frame top Odin reads (air there = no door). */
    public static final int LINTEL_Y = 73;
    public static final int THRESHOLD_Y = 68;
    public static final int HALF = 1;
    public static final int BODY_SIZE = 36;
    public static final String FALLING_BLOCK = "minecraft:barrier";
    public static final String AIR = "minecraft:air";

    /** The block state string of a CLOSED door of [kind]; null for kinds with no body. */
    public static String bodyBlock(DoorKind kind) {
        return switch (kind) {
            case WITHER, FAIRY -> "minecraft:coal_block";
            case BLOOD -> "minecraft:red_terracotta";
            case ENTRANCE -> "minecraft:infested_chiseled_stone_bricks";
            default -> null;
        };
    }

    /** The 36 cells of the body around the slot centre, in order y, then z, then x. */
    public static List<Pos> bodyCells(int cx, int cz) {
        List<Pos> out = new ArrayList<>(BODY_SIZE);
        for (int y = BODY_Y_MIN; y <= BODY_Y_MAX; y++)
            for (int z = cz - HALF; z <= cz + HALF; z++)
                for (int x = cx - HALF; x <= cx + HALF; x++) out.add(new Pos(x, y, z));
        return out;
    }

    public static boolean inBody(int cx, int cz, int x, int y, int z) {
        return y >= BODY_Y_MIN && y <= BODY_Y_MAX && x >= cx - HALF && x <= cx + HALF && z >= cz - HALF && z <= cz + HALF;
    }
}
