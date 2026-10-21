// Ported from ADM's p3sim/run/RunState.kt
package com.teslamaps.adm.sim.run;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One party member, player or bot. */
public class PlayerRecord {
    public final UUID uuid;
    public String name;
    public String dungeonClass;
    public final boolean isBot;

    public PlayerRecord(UUID uuid, String name, String dungeonClass, boolean isBot) {
        this.uuid = uuid;
        this.name = name;
        this.dungeonClass = dungeonClass;
        this.isBot = isBot;
    }

    /** Percent of max HP; vanilla health is 40 * hpPercent / 100. */
    public double hpPercent = 100.0;
    public boolean ghost = false;
    public int deaths = 0;
    public int secretsFound = 0;
    /** Doors/state module. */
    public Integer roomIndex = null;
    /** Refreshed at the start of every tick. */
    public double x = 0.0;
    public double y = 0.0;
    public double z = 0.0;
    /** Run tick of the last hit taken. */
    public int lastHitT = Integer.MIN_VALUE;
    public Integer reviveAtT = null;
    public final Map<String, Object> extras = new HashMap<>();
}
