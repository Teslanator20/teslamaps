package com.teslamaps.adm.sim.clear.traps;

// Ported from ADM's p3sim/clear/traps/RoomHazards.kt

/** The sounds a room's hazards make, with the recorded volume and pitch. */
public enum HazardSound {
    /** A tripwire's hooks click on when the string is touched. */
    LEVER_ON(0.4f, 0.5873016f),
    /** ... and click off when they release. */
    LEVER_OFF(0.4f, 0.4920635f),
    /** The blast on the player a tripwire hit. */
    TRIPWIRE_BLAST(4.0f, 2.0f),
    /** A crusher arm reaching full extension. */
    CRUSHER_SLAM(1.0f, 0.4920635f),
    /** A falling floor letting go. */
    FLOOR_COLLAPSE(5.0f, 0.4920635f),
    /** A flamethrower's shot. */
    FLAME_SHOOT(1.0f, 0.6984127f);

    public final float volume;
    public final float pitch;

    HazardSound(float volume, float pitch) {
        this.volume = volume;
        this.pitch = pitch;
    }
}
