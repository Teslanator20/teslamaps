package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/TrapTimings.kt

/**
 * How fast a crusher moves: one block every ticksPerBlock ticks, stillTicks of rest at the retracted end. sweep (when set) replaces the
 * pack's sweep; pincer splits the sweep into two arms closing on each other.
 */
public class CrusherTiming {
    public final int ticksPerBlock;
    public final int stillTicks;
    public final Integer sweep;
    public final boolean pincer;

    public CrusherTiming(int ticksPerBlock, int stillTicks, Integer sweep, boolean pincer) {
        if (!(ticksPerBlock >= 1 && stillTicks >= 1)) throw new IllegalArgumentException("a crusher moves at least every tick and rests at least a tick");
        this.ticksPerBlock = ticksPerBlock;
        this.stillTicks = stillTicks;
        this.sweep = sweep;
        this.pincer = pincer;
    }

    public CrusherTiming(int ticksPerBlock, int stillTicks, Integer sweep) { this(ticksPerBlock, stillTicks, sweep, false); }

    public CrusherTiming(int ticksPerBlock, int stillTicks) { this(ticksPerBlock, stillTicks, null, false); }

    /** Ticks in one out-and-back cycle: (2 * sweep - 1) move intervals and the rest. */
    public int period(int sweep) { return (2 * sweep - 1) * ticksPerBlock + stillTicks; }
}
