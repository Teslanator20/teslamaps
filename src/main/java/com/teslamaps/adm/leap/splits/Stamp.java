package com.teslamaps.adm.leap.splits;

// Ported from ADM's splits/SplitsModel.kt
public record Stamp(long realMs, int tick) {
    public Stamp plus(int ticks) { return new Stamp(realMs + ticks * 50L, tick + ticks); }
    public Stamp minus(int ticks) { return new Stamp(realMs - ticks * 50L, tick - ticks); }
    public static Stamp later(Stamp a, Stamp b) { return a.tick >= b.tick ? a : b; }
}
