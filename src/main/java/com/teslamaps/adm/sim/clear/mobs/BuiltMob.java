// Ported from ADM's p3sim/clear/mobs/MobEntities.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.teslamaps.adm.sim.SimStand;
import net.minecraft.world.entity.Entity;

import java.util.List;

public class BuiltMob {
    public final Entity body;
    public final List<Entity> fillers;
    public final SimStand stand;

    public BuiltMob(Entity body, List<Entity> fillers, SimStand stand) { this.body = body; this.fillers = fillers; this.stand = stand; }
}
