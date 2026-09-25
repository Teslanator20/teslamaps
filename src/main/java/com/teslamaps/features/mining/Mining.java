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

    public static boolean inHollows() {
        return SkyblockUtils.isInArea("Crystal Hollows");
    }

    public static boolean inNucleus() {
        var p = net.minecraft.client.Minecraft.getInstance().player;
        if (p == null || !inHollows()) return false;
        double x = p.getX(), y = p.getY(), z = p.getZ();
        return x >= 463 && x <= 561 && z >= 460 && z <= 564 && y > 64;
    }
}
