// Ported from ADM's p3sim/clear/hud/HudText.kt
package com.teslamaps.adm.sim.clear.hud;

/** Cosmetic and unmeasured values of the channels, named in one place. */
public final class HudConstants {
    private HudConstants() {}

    public static final String NAME_COLOUR = "§b";
    public static final double DISCOVERY_RATIO = 1.1;
    public static final int SIDEBAR_NAME_MAX = 12;
    public static final int DEFAULT_VITALITY = 127;
    public static final int DEFAULT_SB_LEVEL = 250;
    public static final int DEFAULT_CLASS_LEVEL = 50;
    public static final String TITLE = "§e§lSKYBLOCK";
    public static final String FOOTER = "§ewww.hypixel.net";
    public static final String SEASON = " Early Autumn 19th";
    public static final int CLOCK_START_MINUTES = 18 * 60 + 10;
    public static final int CLOCK_TICKS_PER_STEP = 160;
    public static final int CLEARED_GOLD_FROM = 40;
    public static final int CLEARED_GREEN_FROM = 75;
    public static final double HP_GREEN_FROM = 50.0;
    public static final double HP_YELLOW_FROM = 25.0;
    public static final String LOBBY_SERVER = "m100AV";
    public static final int TAB_REFRESH_TICKS = 20;
    public static final int MAP_TICKS = 5;
    public static final int ACTION_BAR_TICKS = 10;
    /** Map id of the dungeon map: Odin and Devonian accept ids with `(id and 1000) == 0`. */
    public static final int MAP_ID = 1024;
}
