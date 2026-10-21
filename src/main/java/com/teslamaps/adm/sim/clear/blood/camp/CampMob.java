// Ported from ADM's p3sim/clear/blood/camp/CampMobs.kt
package com.teslamaps.adm.sim.clear.blood.camp;

/** What a blood-camp mob is made of. */
public class CampMob {
    /** `player` or `giant`. */
    public final String entity;
    public final String name;
    /** Null for none. */
    public final String modifier;
    public final double hp;
    public final String tagHead;
    public final boolean counted;

    public CampMob(String entity, String name, String modifier, double hp, String tagHead, boolean counted) {
        this.entity = entity;
        this.name = name;
        this.modifier = modifier;
        this.hp = hp;
        this.tagHead = tagHead;
        this.counted = counted;
    }

    public String tagText() { return (modifier != null ? modifier + " " : "") + name; }
}
