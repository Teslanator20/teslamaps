// Ported from ADM's p3sim/clear/blood/WatcherLines.kt
package com.teslamaps.adm.sim.clear.blood;

/** The line that ends the Watcher's stage (his camp lines are in camp/CampLines). */
public final class WatcherLines {
    private WatcherLines() {}

    public static final String NAME = "The Watcher";

    public static final String PASS = "You have proven yourself. You may pass.";

    /** A `[BOSS]` chat line in Hypixel's form. */
    public static String chat(String line, String name) { return "§c[BOSS] " + name + "§r§f: " + line; }

    public static String chat(String line) { return chat(line, NAME); }
}
