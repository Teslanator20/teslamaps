// Ported from ADM's p3sim/clear/mobs/MobWorld.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.mojang.authlib.GameProfile;
import com.teslamaps.adm.sim.SimStand;
import com.teslamaps.adm.sim.pack.mobs.MobArchetype;
import com.teslamaps.adm.sim.pack.mobs.MobModifier;
import com.teslamaps.adm.sim.pack.mobs.MobTag;
import com.teslamaps.adm.sim.run.api.MobRecord;
import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.List;

public class MobEntry {
    public final MobRecord record;
    public final PlannedMob plan;
    public final MobArchetype arch;
    public final MobModifier modifier;

    public double x;
    public double y;
    public double z;
    public Entity body = null;
    public List<Entity> fillers = Collections.emptyList();
    public SimStand stand = null;
    public boolean asleep;
    public boolean hidden;
    public GameProfile profile = null;
    public boolean announced = false;
    public String lastTag = "";
    public final AiState ai = new AiState();
    public int navCooldown = 0;
    public int builtAt = Integer.MIN_VALUE / 2;
    public int retryAt = Integer.MIN_VALUE / 2;
    public int vanishes = 0;
    public long builtGameTime = 0L;
    public int buildFailures = 0;
    public boolean plainBody = false;
    public double lastNavX = Double.NaN;
    public double lastNavZ = Double.NaN;
    public boolean live = false;

    public MobEntry(MobRecord record, PlannedMob plan, MobArchetype arch, MobModifier modifier) {
        this.record = record;
        this.plan = plan;
        this.arch = arch;
        this.modifier = modifier;
        this.x = plan.x;
        this.y = plan.y;
        this.z = plan.z;
        this.asleep = "sleeper".equals(arch.movement);
        this.hidden = "teleport".equals(arch.movement);
    }

    public MobTag tag() { return arch.tag != null ? arch.tag : MobWorldKt.DEFAULT_TAG; }

    public boolean alive() { return record.alive; }

    public boolean materialised() { return live; }
}
