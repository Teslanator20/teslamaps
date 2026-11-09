package com.teslamaps.features;

import com.mojang.datafixers.util.Pair;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Hypixel does not play a client death animation for mobs: it strips their armor and leaves the
// body standing until the entity is removed. Health/deathTime never change, so the only reliable
// death signal is an equipment packet that clears an armor slot.
public class DeadMobHider {

    private static final int HEALTH_ID = 9;   // LivingEntity.DATA_HEALTH_ID
    private static final long HIDE_MS = 4000L;
    private static final Map<Integer, Long> stripped = new ConcurrentHashMap<>();
    public static boolean pickGuard;

    public static void register() {
        com.teslamaps.registry.Feature.of("DeadMobPick").tickAlways(() -> {
            TeslaMapsConfig c = TeslaMapsConfig.get();
            pickGuard = c.hideDeadMobs && c.deadMobsUnpickable && !LegitMode.blocksCheats();
        });
    }

    // netty thread
    public static void onSetEquipment(ClientboundSetEquipmentPacket packet) {
        if (!TeslaMapsConfig.get().hideDeadMobs) return;
        List<Pair<EquipmentSlot, ItemStack>> slots = packet.getSlots();
        if (slots.isEmpty()) return;
        // One packet can carry several slots. Judge the armor slots only: a death packet that
        // clears the armor but still names a mainhand item used to be read as "alive" purely
        // because the non-empty slot happened to come first in the list.
        boolean clearedArmor = false;
        boolean equippedArmor = false;
        for (Pair<EquipmentSlot, ItemStack> slot : slots) {
            if (!slot.getFirst().isArmor()) continue;
            if (slot.getSecond().isEmpty()) clearedArmor = true;
            else equippedArmor = true;
        }
        if (equippedArmor) {                 // re-equipping means it is alive
            stripped.remove(packet.getEntity());
            return;
        }
        if (!clearedArmor) return;           // a bare mainhand clear is a weapon swap, not a death
        if (stripped.size() > 64) prune();
        stripped.put(packet.getEntity(), System.currentTimeMillis());
    }

    // vanilla death: entity event 3 starts the death animation
    public static void onEntityEvent(int entityId, int eventId) {
        if (eventId != 3) return;
        if (!TeslaMapsConfig.get().hideDeadMobs) return;
        if (stripped.size() > 64) prune();
        stripped.put(entityId, System.currentTimeMillis());
    }

    // Hypixel drops a mob to 0 hp and leaves the body standing
    public static void onSetEntityData(net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket packet) {
        if (!TeslaMapsConfig.get().hideDeadMobs) return;
        for (net.minecraft.network.syncher.SynchedEntityData.DataValue<?> value : packet.packedItems()) {
            if (value.id() != HEALTH_ID || !(value.value() instanceof Float health)) continue;
            if (health <= 0f) {
                if (stripped.size() > 64) prune();
                stripped.put(packet.id(), System.currentTimeMillis());
            } else {
                stripped.remove(packet.id());
            }
            return;
        }
    }

    public static void onRemoveEntities(net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket packet) {
        if (stripped.isEmpty()) return;
        for (int id : packet.entityIds().toIntArray()) stripped.remove(id);
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        stripped.entrySet().removeIf(e -> now - e.getValue() > HIDE_MS);
    }

    private static boolean isStripped(int id) {
        Long at = stripped.get(id);
        if (at == null) return false;
        if (System.currentTimeMillis() - at > HIDE_MS) {
            stripped.remove(id);
            return false;
        }
        return true;
    }

    /**
     * Most Hypixel mobs are skinned <b>player</b> entities, so skipping every Player - which is
     * what this did - skipped nearly every mob in the game. A real player is in the tab list;
     * the server's mob-players are not, which is the only reliable way to tell them apart.
     */
    public static boolean isRealPlayer(Entity entity) {
        if (!(entity instanceof Player p)) return false;
        net.minecraft.client.multiplayer.ClientPacketListener conn =
                net.minecraft.client.Minecraft.getInstance().getConnection();
        if (conn == null) return true;   // cannot tell, so play it safe and never hide
        return conn.getPlayerInfo(p.getUUID()) != null;
    }

    // the tag sits roughly head height above the body, so this is generous vertically
    private static final double TAG_RADIUS_SQ = 4.0;
    private static final double TAG_HEIGHT = 3.0;

    public static boolean shouldHide(Entity entity) {
        if (!TeslaMapsConfig.get().hideDeadMobs) return false;
        if (isRealPlayer(entity)) return false;

        boolean nametag = entity instanceof ArmorStand
                || entity instanceof net.minecraft.world.entity.Display.TextDisplay;
        if (nametag) {
            // A tag that actually rides the corpse is the easy case.
            Entity vehicle = entity.getVehicle();
            if (vehicle != null) return !isRealPlayer(vehicle) && isDead(vehicle) && isHealthTag(entity);
            return tagOfCorpse(entity);
        }
        return isDead(entity);
    }

    /**
     * Hypixel does not make the nametag a passenger - it places a separate stand next to the mob,
     * so {@code getVehicle()} is null and the tag outlived the body it belongs to. Match it to a
     * corpse by position instead.
     * <p>
     * Guarded by {@code stripped.isEmpty()}: with nothing dying this costs one map check per
     * entity, and the map is capped at 64 anyway.
     */
    // shouldRender asks per entity per frame; the answer only changes when the world ticks
    private static final Map<Entity, Boolean> tagCache = new java.util.IdentityHashMap<>();
    private static long tagCacheTick = Long.MIN_VALUE;

    private static boolean tagOfCorpse(Entity tag) {
        if (stripped.isEmpty()) return false;
        net.minecraft.client.multiplayer.ClientLevel lvl =
                net.minecraft.client.Minecraft.getInstance().level;
        if (lvl == null) return false;
        long tick = lvl.getGameTime();
        if (tick != tagCacheTick) {
            tagCache.clear();
            tagCacheTick = tick;
        }
        Boolean known = tagCache.get(tag);
        if (known != null) return known;
        boolean result = computeTagOfCorpse(tag);
        tagCache.put(tag, result);
        return result;
    }

    // Only a mob health bar ("[Lv100] Name 521k/1M❤") may be matched to a corpse by position.
    // Kuudra places progress/supply bars right next to dying mobs; those must stay visible.
    private static final java.util.regex.Pattern HEALTH_TAG =
            java.util.regex.Pattern.compile("\\[Lv\\d+\\]|[\\d.,]+[kKmMbB]?/[\\d.,]+[kKmMbB]?❤");

    private static boolean isHealthTag(Entity tag) {
        String text = null;
        if (tag.getCustomName() != null) {
            text = tag.getCustomName().getString();
        } else if (tag instanceof net.minecraft.world.entity.Display.TextDisplay td) {
            var state = td.textRenderState();
            if (state != null && state.text() != null) text = state.text().getString();
        }
        if (text == null || text.isEmpty()) return false;
        return HEALTH_TAG.matcher(com.teslamaps.utils.StringUtil.strip(text)).find();
    }

    private static boolean computeTagOfCorpse(Entity tag) {
        net.minecraft.client.multiplayer.ClientLevel level =
                net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return false;
        if (!isHealthTag(tag)) return false;

        for (Map.Entry<Integer, Long> e : stripped.entrySet()) {
            if (!isStripped(e.getKey())) continue;
            Entity corpse = level.getEntity(e.getKey());
            if (corpse == null || corpse == tag) continue;

            double dy = tag.getY() - corpse.getY();
            if (dy < -1.0 || dy > TAG_HEIGHT) continue;
            double dx = tag.getX() - corpse.getX();
            double dz = tag.getZ() - corpse.getZ();
            if (dx * dx + dz * dz <= TAG_RADIUS_SQ) return true;
        }
        return false;
    }

    /** No Mob Leftover: corpses stop catching clicks and hits. */
    public static boolean unpickable(LivingEntity le) {
        if (!le.level().isClientSide()) return false;
        if (le == net.minecraft.client.Minecraft.getInstance().player || isRealPlayer(le)) return false;
        return le.isDeadOrDying() || le.deathTime > 0 || isStripped(le.getId());
    }

    private static boolean isDead(Entity entity) {
        if (!(entity instanceof LivingEntity le)) return false;
        if (le.isDeadOrDying() || le.deathTime > 0) return true;
        if (isStripped(le.getId())) return true;
        // corpses that slip through only show for a few ticks, far too short to catch with a
        // manual dump, so the probe reports them itself
        if (le.hurtTime > 0 || le.getHealth() <= 0f) com.teslamaps.debug.DeathProbe.miss(entity);
        return false;
    }
}
