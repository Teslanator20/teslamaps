// Ported from ADM's p3sim/pack/runstate/RunStatePack.kt
package com.teslamaps.adm.sim.pack.runstate;

import java.util.Map;

/** One room type's transition paths ({@code UNWG}) and tick distributions ({@code NtoW}, {@code WtoG}, {@code NtoG}). */
public class TypeStates {
    public final Map<String, Integer> paths;
    public final Map<String, Dist> ticks;

    public TypeStates(Map<String, Integer> paths, Map<String, Dist> ticks) { this.paths = paths; this.ticks = ticks; }
}
