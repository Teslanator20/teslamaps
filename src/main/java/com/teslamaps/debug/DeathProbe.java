package com.teslamaps.debug;

import com.mojang.datafixers.util.Pair;
import com.teslamaps.TeslaMaps;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Records what the server actually sends when a mob dies, so the hider can key off a real
 * signal instead of a guess. Everything lands in latest.log as [DeathProbe] lines.
 */
public class DeathProbe {

    private static final int HEALTH_ID = 9;
    private static final double RANGE_SQ = 625; // 25 blocks

    private static volatile long until = 0;

    public static void arm(int seconds) {
        until = System.currentTimeMillis() + seconds * 1000L;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(
                    "§b[TeslaMaps] §fDeath probe on for §e" + seconds + "s§f. Kill mobs, then send latest.log."));
        }
        TeslaMaps.LOGGER.info("[DeathProbe] armed for {}s", seconds);
    }

    private static boolean active() {
        long u = until;
        if (u == 0) return false;
        if (System.currentTimeMillis() > u) {
            until = 0;
            TeslaMaps.LOGGER.info("[DeathProbe] finished");
            return false;
        }
        return true;
    }

    private static String name(int id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        Entity e = mc.level.getEntity(id);
        if (e == null) return "id" + id + " (gone)";
        // only skip actual players; Hypixel's mobs are player entities too
        if (com.teslamaps.features.DeadMobHider.isRealPlayer(e)) return null;
        if (mc.player.distanceToSqr(e) > RANGE_SQ) return null;
        String extra = "";
        if (e instanceof LivingEntity le) {
            extra = " hp=" + le.getHealth() + "/" + le.getMaxHealth() + " deathTime=" + le.deathTime
                    + " dying=" + le.isDeadOrDying();
        }
        if (e instanceof ArmorStand as) {
            extra += " stand name=\"" + (as.getCustomName() == null ? "" : as.getCustomName().getString()) + "\""
                    + " riding=" + (as.getVehicle() == null ? "-" : as.getVehicle().getId());
        }
        return "id" + id + " " + e.getType().toShortString() + extra;
    }

    public static void onSetEquipment(ClientboundSetEquipmentPacket packet) {
        if (!active()) return;
        String who = name(packet.getEntity());
        if (who == null) return;
        StringBuilder sb = new StringBuilder();
        for (Pair<EquipmentSlot, ItemStack> slot : packet.getSlots()) {
            ItemStack stack = slot.getSecond();
            sb.append(' ').append(slot.getFirst().getName()).append('=')
              .append(stack.isEmpty() ? "EMPTY" : stack.getItem().toString());
        }
        TeslaMaps.LOGGER.info("[DeathProbe] equip {} ->{}", who, sb);
    }

    public static void onSetEntityData(ClientboundSetEntityDataPacket packet) {
        if (!active()) return;
        String who = name(packet.id());
        if (who == null) return;
        StringBuilder sb = new StringBuilder();
        for (SynchedEntityData.DataValue<?> value : packet.packedItems()) {
            sb.append(' ').append(value.id()).append('=').append(value.value());
            if (value.id() == HEALTH_ID) sb.append("(health)");
        }
        TeslaMaps.LOGGER.info("[DeathProbe] data {} ->{}", who, sb);
    }

    public static void onEntityEvent(ClientboundEntityEventPacket packet, int eventId) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = packet.getEntity(mc.level);
        if (e == null) return;
        String who = name(e.getId());
        if (who == null) return;
        TeslaMaps.LOGGER.info("[DeathProbe] event {} -> {}", who, eventId);
    }

    /** Fires while the probe is armed for anything that looks dead but is still being drawn. */
    private static final java.util.Set<Integer> reportedMisses = new java.util.HashSet<>();

    public static void miss(Entity e) {
        if (!active()) return;
        if (!(e instanceof LivingEntity le)) return;
        if (!reportedMisses.add(e.getId())) return;
        if (reportedMisses.size() > 256) reportedMisses.clear();
        StringBuilder armor = new StringBuilder();
        for (EquipmentSlot s : EquipmentSlot.values()) {
            if (s.isArmor() && !le.getItemBySlot(s).isEmpty()) armor.append(s.getName().charAt(0));
        }
        TeslaMaps.LOGGER.info("[DeathProbe] MISS still rendered: id{} {} hp={} deathTime={} dying={} hurt={} armor={} nograv={} invis={}",
                e.getId(), e.getType().toShortString(), le.getHealth(), le.deathTime,
                le.isDeadOrDying(), le.hurtTime, armor.isEmpty() ? "-" : armor.toString(),
                le.isNoGravity(), le.isInvisible());
    }

    public static void onRemoveEntities(ClientboundRemoveEntitiesPacket packet) {
        if (!active()) return;
        for (int id : packet.entityIds().toIntArray()) {
            String who = name(id);
            if (who != null) TeslaMaps.LOGGER.info("[DeathProbe] remove {}", who);
        }
    }
}
