// Ported from ADM's p3sim/clear/puzzles/SolveTiming.kt
package com.teslamaps.adm.sim.clear.puzzles;

/** When the three things a solve does land, in server ticks after the puzzle calls for the solve. Pure: no Minecraft. */
public class SolveTiming {
    public final int chat;
    public final int green;
    public final int complete;

    public SolveTiming(int chat, int green, int complete) {
        this.chat = chat;
        this.green = green;
        this.complete = complete;
    }

    /** The old single-number form: a lead of 0 or more turns green first; a negative lead finishes at once and turns green later. */
    public static SolveTiming fromLead(int lead) {
        return lead >= 0 ? new SolveTiming(lead, 0, lead) : new SolveTiming(0, -lead, 0);
    }
}
