// Ported from ADM's p3sim/pack/runstate/RunStatePack.kt
package com.teslamaps.adm.sim.pack.runstate;

/** A SkyBlock stat preset. */
public class SbStatPreset {
    public final String id;
    public final String label;
    public final int floor;
    public final int maxHp;
    public final int defense;
    public final int maxMana;
    public final int speed;
    public final Integer vitality;

    public SbStatPreset(String id, String label, int floor, int maxHp, int defense, int maxMana, int speed, Integer vitality) {
        this.id = id;
        this.label = label;
        this.floor = floor;
        this.maxHp = maxHp;
        this.defense = defense;
        this.maxMana = maxMana;
        this.speed = speed;
        this.vitality = vitality;
    }
}
