// Ported from ADM's p3sim/clear/bots/BotEffects.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The teammate deaths of a clear: rare, one count drawn per run. */
public final class TeamDeaths {
    private TeamDeaths() {}

    /** Deaths in the clear to number of recorded clears. */
    public static final List<Pair<Integer, Integer>> DISTRIBUTION = List.of(Pair.of(0, 409), Pair.of(1, 19), Pair.of(2, 5));

    /** Earliest a teammate dies. */
    public static final int EARLIEST_TICK = 150;

    /** Death tick after the first Mort line as (share of deaths, tick). */
    public static final List<Pair<Double, Double>> TICK_CURVE = PlanMath.pts(0.0, 150.0, 0.05, 151.0, 0.25, 456.0, 0.5, 806.0, 0.75, 1151.0, 0.95, 1748.0, 1.0, 1900.0);

    public static int drawCount(KRandom random) {
        int total = 0;
        for (Pair<Integer, Integer> p : DISTRIBUTION) total += p.second;
        int pick = random.nextInt(total);
        for (Pair<Integer, Integer> p : DISTRIBUTION) { if (pick < p.second) return p.first; pick -= p.second; }
        return 0;
    }

    /** The run ticks of this run's teammate deaths, sorted, between EARLIEST_TICK and [lastTick]. */
    public static List<Integer> schedule(KRandom random, int lastTick) {
        int n = drawCount(random);
        int hi = Math.max(lastTick, EARLIEST_TICK + 1);
        ArrayList<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(Math.clamp((long) (int) PlanMath.curve(TICK_CURVE, random.nextDouble()), EARLIEST_TICK, hi - 1));
        Collections.sort(out);
        return out;
    }
}
