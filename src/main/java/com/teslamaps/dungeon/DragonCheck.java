package com.teslamaps.dungeon;

import com.teslamaps.config.TeslaMapsConfig;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class DragonCheck {

    /** health index in the synched entity data (LivingEntity.DATA_HEALTH_ID) */
    private static final int HEALTH_ID = 9;

    public record HealthEntry(EnderDragon dragon, float health) {}

    public static final Map<UUID, HealthEntry> dragonHealthMap = new LinkedHashMap<>();
    public static WitherDragons.Dragon lastDragonDeath = null;

    public static void dragonUpdate(ClientboundSetEntityDataPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!(mc.level.getEntity(packet.id()) instanceof EnderDragon entity)) return;

        Float health = null;
        for (SynchedEntityData.DataValue<?> value : packet.packedItems()) {
            if (value.id() == HEALTH_ID && value.value() instanceof Float f) { health = f; break; }
        }
        if (health == null) return;

        dragonHealthMap.put(entity.getUUID(), new HealthEntry(entity, health));

        for (WitherDragons.Dragon dragon : WitherDragons.Dragon.values()) {
            if (!entity.getUUID().equals(dragon.entityUUID)) continue;
            if (health <= 0 && dragon.state != WitherDragons.State.DEAD) dragon.setDead(true);
            break;
        }
    }

    public static void dragonSpawn(ClientboundAddEntityPacket packet) {
        if (packet.getType() != EntityTypes.ENDER_DRAGON) return;
        Vec3 pos = new Vec3(packet.getX(), packet.getY(), packet.getZ());
        for (WitherDragons.Dragon dragon : WitherDragons.Dragon.values()) {
            if (dragon.box.contains(pos)) { dragon.setAlive(packet.getUUID()); return; }
        }
    }

    public static void dragonSprayed(ClientboundSetEquipmentPacket packet) {
        boolean ice = false;
        for (Pair<EquipmentSlot, ItemStack> slot : packet.getSlots()) {
            if (slot.getSecond().getItem() == Items.PACKED_ICE) { ice = true; break; }
        }
        if (!ice) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!(mc.level.getEntity(packet.getEntity()) instanceof ArmorStand sprayedEntity)) return;

        for (WitherDragons.Dragon dragon : WitherDragons.Dragon.values()) {
            if (dragon.entityUUID == null) continue;
            Entity entity = byUuid(dragon.entityUUID);
            if (!(entity instanceof EnderDragon)) continue;
            if (dragon.isSprayed || dragon.state != WitherDragons.State.ALIVE
                    || sprayedEntity.distanceTo(entity) > 8) continue;

            if (TeslaMapsConfig.get().witherDragonSendSpray) {
                long ticks = WitherDragons.currentTick - dragon.spawnedTime;
                WitherDragons.msg("§" + dragon.colorCode + dragon.dispName + " §fdragon was sprayed in §c"
                        + ticks + " §ftick" + (ticks > 1 ? "s" : "") + ".");
            }
            dragon.isSprayed = true;
        }
    }

    private static Entity byUuid(UUID uuid) {
        HealthEntry entry = dragonHealthMap.get(uuid);
        if (entry != null) return entry.dragon();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getUUID().equals(uuid)) return entity;
        }
        return null;
    }
}
