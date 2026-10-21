// Ported from ADM's p3sim/clear/state/StateText.kt
package com.teslamaps.adm.sim.clear.state;

import java.util.Objects;

public final class Cue {
    public final String id;
    public final float volume;
    public final float pitch;
    public final CueSource source;

    public Cue(String id, float volume, float pitch, CueSource source) {
        this.id = id;
        this.volume = volume;
        this.pitch = pitch;
        this.source = source;
    }

    public Cue(String id, float volume, float pitch) { this(id, volume, pitch, CueSource.MASTER); }

    @Override public boolean equals(Object o) {
        return o instanceof Cue c && Objects.equals(id, c.id) && Float.compare(volume, c.volume) == 0
                && Float.compare(pitch, c.pitch) == 0 && source == c.source;
    }
    @Override public int hashCode() { return Objects.hash(id, volume, pitch, source); }
    @Override public String toString() { return "Cue(id=" + id + ", volume=" + volume + ", pitch=" + pitch + ", source=" + source + ")"; }
}
