// Ported from ADM's p3sim/clear/mobs/MobEntities.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.SimStand;
import com.teslamaps.adm.sim.Station;
import com.teslamaps.adm.sim.TransientStand;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class MobBodies {
    private MobBodies() {}

    public static BuiltMob build(ServerLevel level, int idOffset, Supplier<Entity> makeBody) {
        for (int attempt = 0; attempt < 12; attempt++) {
            Entity body = makeBody.get();
            List<Entity> fillers = new ArrayList<>();
            for (int i = 1; i < idOffset; i++) fillers.add(filler(level));
            TransientStand stand = new TransientStand(level);
            boolean ok = stand.getId() == body.getId() + idOffset;
            if (ok) for (int i = 0; i < fillers.size(); i++) if (fillers.get(i).getId() != body.getId() + 1 + i) { ok = false; break; }
            if (ok) return new BuiltMob(body, fillers, stand);
        }
        Entity body = makeBody.get();
        List<Entity> fillers = new ArrayList<>();
        for (int i = 1; i < idOffset; i++) fillers.add(filler(level));
        TransientStand stand = new TransientStand(level);
        for (int i = 0; i < fillers.size(); i++) if (level.getEntity(body.getId() + 1 + i) == null) fillers.get(i).setId(body.getId() + 1 + i);
        if (level.getEntity(body.getId() + idOffset) == null) stand.setId(body.getId() + idOffset);
        Ec.LOG.warn("[ads] mob ids were not consecutive after 12 tries; forced body " + body.getId() + " tag " + stand.getId());
        return new BuiltMob(body, fillers, stand);
    }

    private static SimStand filler(ServerLevel level) {
        TransientStand it = new TransientStand(level);
        it.setInvisible(true); it.setSilent(true); it.setPermanentlyInvulnerable(true); Station.setMarker(it);
        return it;
    }

    public static void dressTag(SimStand stand, net.minecraft.network.chat.Component name) {
        stand.setInvisible(true);
        stand.setSilent(true);
        stand.setPermanentlyInvulnerable(true);
        stand.setNoGravity(true);
        Station.setMarker(stand);
        stand.setCustomName(name);
        stand.setCustomNameVisible(true);
    }

    private static boolean healthLoaded = false;
    private static EntityDataAccessor<Float> healthAccessor;

    @SuppressWarnings("unchecked")
    private static synchronized EntityDataAccessor<Float> healthAccessor() {
        if (!healthLoaded) {
            healthLoaded = true;
            try {
                Field f = LivingEntity.class.getDeclaredField("DATA_HEALTH_ID");
                f.setAccessible(true);
                healthAccessor = (EntityDataAccessor<Float>) f.get(null);
            } catch (Throwable t) {
                Ec.LOG.warn("[ads] cannot reach the health data id: " + t.getMessage());
                healthAccessor = null;
            }
        }
        return healthAccessor;
    }

    public static boolean setHealthData(LivingEntity body, double hp) {
        EntityDataAccessor<Float> acc = healthAccessor();
        if (acc == null) return false;
        try { body.getEntityData().set(acc, (float) hp); return true; } catch (Throwable t) { return false; }
    }

    public static ClientboundSetEntityDataPacket healthPacket(Entity body, float hp) {
        EntityDataAccessor<Float> acc = healthAccessor();
        if (acc == null) return null;
        return new ClientboundSetEntityDataPacket(body.getId(), List.<SynchedEntityData.DataValue<?>>of(SynchedEntityData.DataValue.create(acc, hp)));
    }
}
