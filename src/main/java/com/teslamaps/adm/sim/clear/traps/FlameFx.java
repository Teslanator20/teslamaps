package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/RoomHazards.kt

/** One stretch of a flame stream, from..to blocks out from the emitter's face. */
public class FlameFx {
    public final Emitter emitter;
    public final double from;
    public final double to;

    public FlameFx(Emitter emitter, double from, double to) {
        this.emitter = emitter;
        this.from = from;
        this.to = to;
    }
}
