package com.teslamaps.features.foraging;

import com.teslamaps.utils.SkyblockUtils;

/** Shared island checks for the foraging features. */
public class Foraging {

    public static final String MOONGLADE = "Moonglade Marsh";
    public static final String TORRHUS = "Torrhus Canyon";

    // asked per entity per frame from the render filters; getArea is throttled and
    // hands back the same instance, so the lowercase + contains only runs on a change
    private static String areaCache;
    private static boolean moonglade;
    private static boolean torrhus;

    private static void refresh() {
        String a = SkyblockUtils.getArea();
        if (a == areaCache) return;
        areaCache = a;
        String l = a.toLowerCase();
        moonglade = l.contains("moonglade") || l.contains("galatea");
        torrhus = l.contains("torrhus") && !l.contains("safari");
    }

    public static boolean inMoonglade() {
        refresh();
        return moonglade;
    }

    /** The canyon itself, not the Critter Safari inside it. */
    public static boolean inTorrhus() {
        refresh();
        return torrhus;
    }

    /** Both islands that carry the custom foraging trees. */
    public static boolean onForagingIsland() {
        return inMoonglade() || inTorrhus();
    }

    /** SkyHanni scopes the sweep details to FORAGING (custom trees + The Park) plus the Hub. */
    public static boolean whereTreesGrow() {
        String a = SkyblockUtils.getArea();
        return onForagingIsland() || a.equalsIgnoreCase("The Park") || a.equalsIgnoreCase("Hub");
    }

    public static String strip(String s) {
        return com.teslamaps.utils.StringUtil.strip(s);
    }
}
