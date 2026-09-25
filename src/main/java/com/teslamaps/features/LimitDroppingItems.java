package com.teslamaps.features;

// Ported from Devonian's LimitDroppingItems.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ServerTickTimer;
import com.teslamaps.utils.SkyblockUtils;

/** At most 7 drop inputs per 3 server ticks, so spammed Q during lag can't throw a whole stack later. */
public final class LimitDroppingItems {

    private static final int MAX_DROPS = 7;
    private static final int TICK_SPAN = 3;

    private static final int[] hist = new int[TICK_SPAN];
    private static long lastTick = Long.MIN_VALUE;

    private LimitDroppingItems() {}

    /** True = swallow this drop. */
    public static boolean block() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.limitDroppingItems || !c.section("Inventory") || !SkyblockUtils.isInSkyblock()) return false;
        long now = ServerTickTimer.now();
        if (lastTick == Long.MIN_VALUE || now - lastTick >= TICK_SPAN || now < lastTick) {
            java.util.Arrays.fill(hist, 0);
        } else {
            for (long t = lastTick + 1; t <= now; t++) hist[(int) (t % TICK_SPAN)] = 0;
        }
        lastTick = now;
        int count = 0;
        for (int h : hist) count += h;
        if (count >= MAX_DROPS) return true;
        hist[(int) (now % TICK_SPAN)]++;
        return false;
    }
}
