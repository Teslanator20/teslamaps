// Ported from ADM's p3sim/run/api/MobApi.kt
package com.teslamaps.adm.sim.run.api;

import com.teslamaps.adm.sim.run.MobRef;
import java.util.UUID;

/** A mob without an entity. Written ONLY by the mobs module. hp is SkyBlock scale. */
public class MobRecord {
    /** The body entity's UUID (a disguised mob: the server body's). */
    public final UUID id;
    /** Mob-pack archetype id or a module special. */
    public final String kind;
    /** Without star, modifier or HP. */
    public final String name;
    public final Integer roomIndex;
    public final boolean starred;
    /** Must die for WHITE. */
    public final boolean required;
    public final boolean miniboss;
    public final boolean disguised;

    public MobRecord(UUID id, String kind, String name, Integer roomIndex, boolean starred, boolean required, boolean miniboss, boolean disguised) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.roomIndex = roomIndex;
        this.starred = starred;
        this.required = required;
        this.miniboss = miniboss;
        this.disguised = disguised;
    }

    public volatile double hp = 0.0;
    public volatile double maxHp = 0.0;
    public volatile boolean alive = true;
    /** Last known position. */
    public volatile double x = 0.0;
    public volatile double y = 0.0;
    public volatile double z = 0.0;
    /** null before it spawns. */
    public volatile Integer entityId = null;

    public MobRef ref() { return new MobRef(id, kind, roomIndex, starred, required); }
}
