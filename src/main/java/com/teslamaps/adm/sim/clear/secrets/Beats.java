// Ported from ADM's p3sim/clear/secrets/SecretFinales.kt
package com.teslamaps.adm.sim.clear.secrets;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Beats {
    private Beats() {}

    public static List<Integer> due(List<Integer> ats, int elapsed, Set<Integer> done) {
        if (elapsed < 0) return List.of();
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < ats.size(); i++) if (ats.get(i) <= elapsed && done.add(i)) out.add(i);
        return out;
    }

    public static Set<Integer> playedBy(List<Integer> ats, int elapsed) {
        Set<Integer> out = new HashSet<>();
        for (int i = 0; i < ats.size(); i++) if (ats.get(i) <= elapsed) out.add(i);
        return out;
    }
}
