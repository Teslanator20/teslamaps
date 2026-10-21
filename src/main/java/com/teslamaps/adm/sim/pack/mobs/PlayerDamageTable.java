// Ported from ADM's p3sim/pack/mobs/MobPack.kt
package com.teslamaps.adm.sim.pack.mobs;

import java.util.List;
import java.util.Map;

public class PlayerDamageTable {
    public final String unit;
    /** Hits closer together than this many ticks are ignored (10, measured). */
    public final int immunityTicks;
    /** Pooled melee table per floor key. */
    public final Map<String, MeleeTable> melee;
    /** Floor key -> archetype id -> table; overrides the pooled table when present. */
    public final Map<String, Map<String, HitQuantiles>> meleeByArchetype;
    public final Map<String, HitQuantiles> projectile;
    /** Traps hit for exactly this percent of max HP, true damage (15.0). */
    public final double trapPercent;
    public final List<String> trapKinds;

    public PlayerDamageTable(String unit, int immunityTicks, Map<String, MeleeTable> melee, Map<String, Map<String, HitQuantiles>> meleeByArchetype,
                             Map<String, HitQuantiles> projectile, double trapPercent, List<String> trapKinds) {
        this.unit = unit;
        this.immunityTicks = immunityTicks;
        this.melee = melee;
        this.meleeByArchetype = meleeByArchetype;
        this.projectile = projectile;
        this.trapPercent = trapPercent;
        this.trapKinds = trapKinds;
    }
}
