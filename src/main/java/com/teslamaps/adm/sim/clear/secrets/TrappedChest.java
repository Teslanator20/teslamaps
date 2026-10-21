// Ported from ADM's p3sim/clear/secrets/SecretFinales.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.util.KRandom;
import java.util.List;

public final class TrappedChest {
    private TrappedChest() {}

    public static final double DEFAULT_CHANCE = 0.72;

    /** Kotlin's 4..11: {first, last}, both inclusive. */
    public static final int[] KILL_DELAY = {4, 11};

    public static SecretSite pick(List<SecretSite> candidates, double chance, KRandom random) {
        if (candidates.isEmpty()) return null;
        double u = random.nextDouble();
        int i = random.nextInt(candidates.size());
        return u < chance ? candidates.get(i) : null;
    }
}
