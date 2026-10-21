// Ported from ADM's p3sim/run/RunState.kt
package com.teslamaps.adm.sim.run;

import com.teslamaps.adm.sim.util.Pair;
import java.util.ArrayList;
import java.util.List;

/** The team score, every 200 ticks from T0+200. Score module. */
public class ScoreState {
    public int score = 0;
    public int clearedPct = 0;
    public int lastUpdateT = Integer.MIN_VALUE;
    /** (t, score) after each recompute. */
    public final List<Pair<Integer, Integer>> history = new ArrayList<>();
}
