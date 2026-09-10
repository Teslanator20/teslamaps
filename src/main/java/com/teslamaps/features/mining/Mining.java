package com.teslamaps.features.mining;

import com.teslamaps.utils.SkyblockUtils;

public class Mining {

    private static boolean cached = false;
    private static long cachedTime = 0;

    public static boolean onMiningIsland() {
        long now = System.currentTimeMillis();
        if (now - cachedTime < 1000) return cached;
        cachedTime = now;
        String area = SkyblockUtils.getArea().toLowerCase();
        return cached = area.contains("dwarven")
                || area.contains("crystal hollows")
                || area.contains("mineshaft")
                || area.contains("glacite")
                || area.contains("gold mine")
                || area.contains("deep cavern");
    }
}
