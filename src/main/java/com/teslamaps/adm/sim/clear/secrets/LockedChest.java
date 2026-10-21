// Ported from ADM's p3sim/clear/secrets/SecretFinales.kt
package com.teslamaps.adm.sim.clear.secrets;

public final class LockedChest {
    private LockedChest() {}

    public static final int QUIET_TICKS = 12;

    public static boolean answers(Integer lastAnswered, int now) { return lastAnswered == null || now - lastAnswered >= QUIET_TICKS; }
}
