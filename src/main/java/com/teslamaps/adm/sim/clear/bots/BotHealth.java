// Ported from ADM's p3sim/clear/bots/BotHealth.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.adm.sim.util.Triple;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** What a teammate's health looks like from outside. Pure. */
public final class BotHealth {
    private BotHealth() {}

    /** Hits a teammate takes while it works in a room with living mobs, a minute (measured). */
    public static final double HITS_PER_MINUTE = 2.2;

    /** Size of one hit, percent of max health, as (share of hits, size) points. Unmeasured. */
    public static final List<Pair<Double, Double>> HIT_PERCENT = List.of(Pair.of(0.0, 4.0), Pair.of(0.5, 9.0), Pair.of(0.9, 16.0), Pair.of(1.0, 24.0));

    /** An ordinary hit never takes a teammate below this. */
    public static final double HP_FLOOR = 25.0;

    /** A death is preceded by two to four hits within this many ticks, at least RUN_UP_GAP ticks apart. */
    public static final int RUN_UP_WINDOW = 40;
    public static final int RUN_UP_GAP = 10;

    /** Each run-up hit takes this share of the health the teammate has then (start, end). Unmeasured. */
    public static final double[] RUN_UP_SHARE = new double[]{0.25, 0.45};

    /** The share of scheduled deaths that are a dropped connection. */
    public static final double DISCONNECT_SHARE = 0.17;

    /** A scheduled death waits this long for a teammate in a fight to take the blame. Unmeasured. */
    public static final int VICTIM_PATIENCE = 400;

    /** The highest sidebar health value of a teammate over a run, by class: (p10, p50, p90). */
    private static final Map<String, Triple<Integer, Integer, Integer>> MAX_HP = Map.of(
            "ARCHER", Triple.of(21_000, 26_700, 35_800),
            "MAGE", Triple.of(24_200, 37_200, 48_300),
            "HEALER", Triple.of(16_700, 24_200, 47_800),
            "TANK", Triple.of(22_200, 30_500, 45_600),
            "BERSERK", Triple.of(15_600, 33_000, 45_900)
    );

    /** The health number a teammate of [cls] shows at full health, from the uniform draw [u]. */
    public static int maxHp(String cls, double u) {
        Triple<Integer, Integer, Integer> t = MAX_HP.get(cls.toUpperCase(Locale.ROOT));
        if (t == null) t = MAX_HP.get("MAGE");
        int p10 = t.first, p50 = t.second, p90 = t.third;
        return (int) PlanMath.curve(PlanMath.pts(0.0, p10 * 0.9, 0.1, p10, 0.5, p50, 0.9, p90, 1.0, p90 * 1.1), u);
    }

    /** The size of an ordinary hit from the uniform draw [u]. */
    public static double hitPercent(double u) { return PlanMath.curve(HIT_PERCENT, u); }
}
