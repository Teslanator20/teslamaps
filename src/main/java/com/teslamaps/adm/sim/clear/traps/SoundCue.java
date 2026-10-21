package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/RoomHazards.kt

/** A sound at a canonical position (block coordinates, a cell's middle at +0.5). */
public class SoundCue {
    public final HazardSound sound;
    public final double x;
    public final double y;
    public final double z;

    public SoundCue(HazardSound sound, double x, double y, double z) {
        this.sound = sound;
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
