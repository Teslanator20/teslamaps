// Ported from ADM's p3sim/run/Run.kt
package com.teslamaps.adm.sim.run;

import java.util.Objects;

public final class BossEntryOptions {
    public final boolean giveHotbar;
    public final int firstLineDelayTicks;

    public BossEntryOptions(boolean giveHotbar, int firstLineDelayTicks) {
        this.giveHotbar = giveHotbar;
        this.firstLineDelayTicks = firstLineDelayTicks;
    }

    public BossEntryOptions(boolean giveHotbar) { this(giveHotbar, 2); }

    public BossEntryOptions() { this(false, 2); }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof BossEntryOptions x && Objects.equals(giveHotbar, x.giveHotbar) && Objects.equals(firstLineDelayTicks, x.firstLineDelayTicks);
    }
    @Override public int hashCode() { return Objects.hash(giveHotbar, firstLineDelayTicks); }
    @Override public String toString() { return "BossEntryOptions(" + "giveHotbar=" + giveHotbar + ", " + "firstLineDelayTicks=" + firstLineDelayTicks + ")"; }
}
