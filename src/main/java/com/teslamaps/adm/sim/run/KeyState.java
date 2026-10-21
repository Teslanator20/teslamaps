// Ported from ADM's p3sim/run/RunState.kt
package com.teslamaps.adm.sim.run;

/** The party-shared key counters. Doors/state module. */
public class KeyState {
    public int witherHeld = 0;
    public boolean bloodHeld = false;
    public int witherObtained = 0;
    public int witherUsed = 0;
    public boolean bloodObtained = false;
    public boolean bloodUsed = false;
}
