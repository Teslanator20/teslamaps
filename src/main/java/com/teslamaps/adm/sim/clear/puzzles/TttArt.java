// Ported from ADM's p3sim/clear/puzzles/TttLogic.kt
package com.teslamaps.adm.sim.clear.puzzles;

import java.util.Arrays;

/**
 * The picture on the two maps of Tic Tac Toe: map 30876 (the room's red X) and 30877 (the party's red O). 128 x 128 colour ids: 14
 * outside the inner area, 33 inside it, 114 for the stroke. Shapes approximated from a few numbers. Pure: no Minecraft.
 */
public final class TttArt {
    private TttArt() {}

    public static final int X_MAP_ID = 30876;
    public static final int O_MAP_ID = 30877;
    public static final int SIZE = 128;
    public static final byte BORDER = 14;
    public static final byte BACKGROUND = 33;
    public static final byte STROKE = 114;

    /** The map's colour bytes, row by row. */
    public static byte[] pixels(boolean x) {
        byte[] out = new byte[SIZE * SIZE];
        Arrays.fill(out, BORDER);
        for (int y = 9; y <= 118; y++) for (int px = 8; px <= 120; px++) {
            boolean on = x ? onX(px, y) : onO(px, y);
            out[y * SIZE + px] = on ? STROKE : BACKGROUND;
        }
        return out;
    }

    private static boolean onO(int px, int py) {
        double dx = px - 62.8, dy = py - 64.2;
        double outer = (dx / 53.8) * (dx / 53.8) + (dy / 55.7) * (dy / 55.7);
        double inner = (dx / 31.0) * (dx / 31.0) + (dy / 31.8) * (dy / 31.8);
        return outer <= 1.0 && inner > 1.0;
    }

    private static boolean onX(int px, int py) {
        if (px < 17 || px > 106 || py < 24 || py > 111) return false;
        double t = py - 60.9;
        return Math.abs(px - (64.1 + 1.2 * t)) <= 14.0 || Math.abs(px - (64.1 - 1.2 * t)) <= 14.0;
    }
}
