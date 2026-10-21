// Ported from ADM's p3sim/run/RunEvents.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;

public final class SavepointRestored implements RunEvent {
    public final int t;
    public final String savepointId;

    public SavepointRestored(int t, String savepointId) {
        this.t = t;
        this.savepointId = savepointId;
    }

    @Override public int t() { return t; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof SavepointRestored x && Objects.equals(t, x.t) && Objects.equals(savepointId, x.savepointId);
    }
    @Override public int hashCode() { return Objects.hash(t, savepointId); }
    @Override public String toString() { return "SavepointRestored(" + "t=" + t + ", " + "savepointId=" + savepointId + ")"; }
}
