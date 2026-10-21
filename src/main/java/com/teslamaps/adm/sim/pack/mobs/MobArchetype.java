// Ported from ADM's p3sim/pack/mobs/MobPack.kt
package com.teslamaps.adm.sim.pack.mobs;

import java.util.Map;

/** {@code index} is the position in {@link MobPack#archetypes}; rooms refer to archetypes by it. */
public class MobArchetype {
    public final int index;
    public final String id;
    public final String name;
    /** {@code clear}, {@code miniboss} or {@code special}. */
    public final String cls;
    /** Body entity type id, {@code minecraft:zombie} ... {@code minecraft:player} for a disguise. */
    public final String entity;
    public final boolean disguise;
    public final MobSkin skin;
    public final String bodyName;
    public final MobEquipment equipment;
    /** Base HP per floor key ({@code F7}, {@code M7}). */
    public final Map<String, Long> hp;
    public final boolean star;
    public final MobTag tag;
    /** {@code static}, {@code sleeper}, {@code patrol} or {@code teleport}. */
    public final String movement;
    public final MobWake wake;
    /** Per-archetype override of the global modifier weights; null = use the global table. */
    public final Map<String, Double> modifierWeights;
    public final Map<String, Integer> seen;

    public MobArchetype(int index, String id, String name, String cls, String entity, boolean disguise, MobSkin skin, String bodyName,
                        MobEquipment equipment, Map<String, Long> hp, boolean star, MobTag tag, String movement, MobWake wake,
                        Map<String, Double> modifierWeights, Map<String, Integer> seen) {
        this.index = index;
        this.id = id;
        this.name = name;
        this.cls = cls;
        this.entity = entity;
        this.disguise = disguise;
        this.skin = skin;
        this.bodyName = bodyName;
        this.equipment = equipment;
        this.hp = hp;
        this.star = star;
        this.tag = tag;
        this.movement = movement;
        this.wake = wake;
        this.modifierWeights = modifierWeights;
        this.seen = seen;
    }

    public Long hpFor(String floor) { return hp.get(floor); }
}
