// Ported from ADM's p3sim/clear/bots/BotEffects.kt
package com.teslamaps.adm.sim.clear.bots;

import com.teslamaps.adm.sim.util.KRandom;
import java.util.HashMap;

/** When a walking teammate makes its footstep sound: about once every MEAN_GAP_TICKS ticks while it walks. Standing makes none. */
public class StepClock {
    private final KRandom random;
    private final HashMap<Integer, Integer> next = new HashMap<>();

    public StepClock(KRandom random) { this.random = random; }

    /** True when [seat] should make a step sound at tick [t]. */
    public boolean due(int seat, int t, boolean moving) {
        if (!moving) { next.remove(seat); return false; }
        Integer at = next.get(seat);
        if (at == null) { next.put(seat, t + gap()); return false; }
        if (t < at) return false;
        next.put(seat, t + gap());
        return true;
    }

    private int gap() { return Math.max((int) (MEAN_GAP_TICKS * (0.7 + 0.6 * random.nextDouble())), 2); }

    /** One step sound every 14 ticks while walking (measured). */
    public static final int MEAN_GAP_TICKS = 14;
}
