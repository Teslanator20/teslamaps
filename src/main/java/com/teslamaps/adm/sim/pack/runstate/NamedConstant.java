// Ported from ADM's p3sim/pack/runstate/RunStatePack.kt
package com.teslamaps.adm.sim.pack.runstate;

/** The {@code constants.UNMEASURED} list: named defaults the pack could not measure. */
public class NamedConstant {
    public final String name;
    public final Object value;
    public final String why;

    public NamedConstant(String name, Object value, String why) { this.name = name; this.value = value; this.why = why; }
}
