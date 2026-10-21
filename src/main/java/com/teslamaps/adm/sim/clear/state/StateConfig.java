// Ported from ADM's p3sim/clear/state/StateRules.kt
package com.teslamaps.adm.sim.clear.state;

import java.util.Objects;

public final class StateConfig {
    public final int countdownDelayTicks;
    public final double pickupRadius;
    public final boolean synthesiseClears;
    public final int keyFallbackTicks;

    public StateConfig(int countdownDelayTicks, double pickupRadius, boolean synthesiseClears, int keyFallbackTicks) {
        this.countdownDelayTicks = countdownDelayTicks;
        this.pickupRadius = pickupRadius;
        this.synthesiseClears = synthesiseClears;
        this.keyFallbackTicks = keyFallbackTicks;
    }

    public StateConfig(int countdownDelayTicks, double pickupRadius, boolean synthesiseClears) { this(countdownDelayTicks, pickupRadius, synthesiseClears, 5); }

    public StateConfig(int countdownDelayTicks, double pickupRadius) { this(countdownDelayTicks, pickupRadius, true, 5); }

    public StateConfig(int countdownDelayTicks) { this(countdownDelayTicks, 2.5, true, 5); }

    public StateConfig() { this(60, 2.5, true, 5); }

    @Override public boolean equals(Object o) {
        return o instanceof StateConfig c && countdownDelayTicks == c.countdownDelayTicks && Double.compare(pickupRadius, c.pickupRadius) == 0
                && synthesiseClears == c.synthesiseClears && keyFallbackTicks == c.keyFallbackTicks;
    }
    @Override public int hashCode() { return Objects.hash(countdownDelayTicks, pickupRadius, synthesiseClears, keyFallbackTicks); }
    @Override public String toString() {
        return "StateConfig(countdownDelayTicks=" + countdownDelayTicks + ", pickupRadius=" + pickupRadius + ", synthesiseClears=" + synthesiseClears
                + ", keyFallbackTicks=" + keyFallbackTicks + ")";
    }
}
