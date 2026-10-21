// Ported from ADM's p3sim/clear/secrets/SecretFinales.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.List;

public final class BonusBat {
    private BonusBat() {}

    private static final List<Pair<Integer, Integer>> WEIGHTS = List.of(Pair.of(1, 70), Pair.of(2, 24), Pair.of(3, 5), Pair.of(4, 1));

    public static int draw(KRandom random) {
        int total = 0;
        for (Pair<Integer, Integer> p : WEIGHTS) total += p.second;
        int n = random.nextInt(total);
        for (Pair<Integer, Integer> p : WEIGHTS) { n -= p.second; if (n < 0) return p.first; }
        return 1;
    }
}
