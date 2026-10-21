// Ported from ADM's p3sim/clear/hud/HudModel.kt
package com.teslamaps.adm.sim.clear.hud;

import java.util.Objects;

/** The player-side numbers the action bar and the sidebar print (SkyBlock stats). */
public final class HudStats {
    public final int maxHp;
    public final int defense;
    public final int maxMana;
    /** Maximum vitality; null: the vitality segment is not shown. */
    public final Integer vitality;
    public final int sbLevel;
    public final int classLevel;

    public HudStats(int maxHp, int defense, int maxMana, Integer vitality, int sbLevel, int classLevel) {
        this.maxHp = maxHp;
        this.defense = defense;
        this.maxMana = maxMana;
        this.vitality = vitality;
        this.sbLevel = sbLevel;
        this.classLevel = classLevel;
    }

    public HudStats(int maxHp, int defense, int maxMana, Integer vitality, int sbLevel) {
        this(maxHp, defense, maxMana, vitality, sbLevel, HudConstants.DEFAULT_CLASS_LEVEL);
    }

    public HudStats(int maxHp, int defense, int maxMana, Integer vitality) {
        this(maxHp, defense, maxMana, vitality, HudConstants.DEFAULT_SB_LEVEL, HudConstants.DEFAULT_CLASS_LEVEL);
    }

    /** The "typical gear" preset of the pack, used when no pack or preset is there. */
    public static final HudStats DEFAULT = new HudStats(11351, 3681, 13444, HudConstants.DEFAULT_VITALITY);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HudStats h)) return false;
        return maxHp == h.maxHp && defense == h.defense && maxMana == h.maxMana && Objects.equals(vitality, h.vitality)
                && sbLevel == h.sbLevel && classLevel == h.classLevel;
    }

    @Override
    public int hashCode() { return Objects.hash(maxHp, defense, maxMana, vitality, sbLevel, classLevel); }

    @Override
    public String toString() {
        return "HudStats(maxHp=" + maxHp + ", defense=" + defense + ", maxMana=" + maxMana + ", vitality=" + vitality
                + ", sbLevel=" + sbLevel + ", classLevel=" + classLevel + ")";
    }
}
