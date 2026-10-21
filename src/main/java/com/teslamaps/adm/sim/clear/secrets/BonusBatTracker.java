// Ported from ADM's p3sim/clear/secrets/SecretFinales.kt
package com.teslamaps.adm.sim.clear.secrets;

public class BonusBatTracker {
    public int index;
    public int counted;

    public BonusBatTracker(int index, int counted) { this.index = index; this.counted = counted; }

    public BonusBatTracker(int index) { this(index, 0); }

    public boolean count() { counted++; return index > 0 && counted == index; }
}
