// Ported from ADM's p3sim/run/RunState.kt
package com.teslamaps.adm.sim.run;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** What the team found. Secrets/score modules. */
public class SecretLedger {
    public int teamFound = 0;
    /** Sum of the rooms' maxima. */
    public int teamMax = 0;
    public final Map<UUID, Integer> byPlayer = new HashMap<>();
    /** Uncapped; the score uses at most 5. */
    public int crypts = 0;
    public boolean mimicKilled = false;
    public boolean princeKilled = false;
    public boolean batKilled = false;
    public int witherEssence = 0;
    public final Map<String, Integer> blessings = new HashMap<>();

    /** One decimal as Hypixel prints it. */
    public double percent() { return teamMax <= 0 ? 0.0 : Math.round(1000.0 * teamFound / teamMax) / 10.0; }
}
