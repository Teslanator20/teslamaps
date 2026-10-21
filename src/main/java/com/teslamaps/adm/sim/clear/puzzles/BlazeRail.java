// Ported from ADM's p3sim/clear/puzzles/BlazeLogic.kt
package com.teslamaps.adm.sim.clear.puzzles;

/**
 * The chest on the centre pillar of Higher Or Lower: from the first correct kill it moves one block every STEP_TICKS ticks for STEPS
 * moves (Lower down 69 to 20, Higher up 70 to 119). Lower leaves a bar behind it; Higher eats the bars above it. Pure: no Minecraft.
 */
public class BlazeRail {
    public final boolean higher;

    public BlazeRail(boolean higher) { this.higher = higher; }

    /** Where the chest stands before the first kill: Lower 69, Higher 70. */
    public int startY() { return higher ? 70 : 69; }

    private int dir() { return higher ? 1 : -1; }

    /** The chest's height after step moves (0 to STEPS). */
    public int yAt(int step) { return startY() + dir() * Math.clamp(step, 0, STEPS); }

    /** Where the chest ends: Lower 20, Higher 119. */
    public int endY() { return yAt(STEPS); }

    /** The tick of move step (0-based; the first move is on the tick of the first kill). */
    public int moveTick(int firstKill, int step) { return firstKill + STEP_TICKS * step; }

    /** The tick the chest stops (the last move). */
    public int endTick(int firstKill) { return moveTick(firstKill, STEPS - 1); }

    /** The tick the room's check mark turns at the earliest. */
    public int greenTick(int firstKill) { return endTick(firstKill) + GREEN_AFTER_END; }

    /** What a rail cell holds. */
    public enum Cell { CHEST, BARS, AIR }

    /** The heights the chest can stand at, lowest first. */
    public int[] cells() {
        int lo = Math.min(startY(), endY()), hi = Math.max(startY(), endY());
        int[] out = new int[hi - lo + 1];
        for (int i = 0; i < out.length; i++) out[i] = lo + i;
        return out;
    }

    /** What the rail cell at height y holds when the chest has made step moves. */
    public Cell cellAt(int step, int y) {
        int at = yAt(step);
        if (y == at) return Cell.CHEST;
        boolean behind = higher ? y < at : y > at;
        return behind != higher ? Cell.BARS : Cell.AIR;
    }

    public static final int STEP_TICKS = 5;
    public static final int STEPS = 49;

    /** Measured: the room turns green 244 ticks after the first kill, 4 after the last move. */
    public static final int GREEN_AFTER_END = 4;
}
