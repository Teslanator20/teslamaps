// Ported from ADM's p3sim/clear/mobs/MobWorld.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.TransientStand;
import com.teslamaps.adm.sim.pack.mobs.MobArchetype;
import com.teslamaps.adm.sim.pack.mobs.MobEquipment;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class MobWorld {
    private MobWorld() {}

    public static String tagText(MobEntry e) {
        if (BloodLooks.isBlood(e.arch)) return BloodLooks.tag(e.arch, e.plan.modifier, e.record.hp, e.record.maxHp);
        if (LevelLooks.isLevel(e.arch)) return LevelLooks.tag(e.arch, e.record.hp, e.record.maxHp);
        return MobTags.nametagLegacy(e.tag(), e.arch.name, e.modifier, e.record.hp, e.record.maxHp, e.plan.star);
    }

    public static boolean materialise(MobEntry e, ServerLevel level, boolean fakePlayers, boolean healthData) {
        MobArchetype arch = e.arch;
        boolean disguised = arch.disguise && fakePlayers && !e.plainBody;
        GameProfile profile = null;
        if (disguised) {
            profile = e.profile != null ? e.profile : Disguise.profile(e.plan.id, Disguise.profileName(arch), arch.skin != null ? arch.skin : Disguise.missingSkin(arch));
            if (!e.announced) e.announced = Disguise.announce(profile);
            if (!e.announced) { disguised = false; profile = null; }
        }
        e.profile = profile;
        BuiltMob built;
        final boolean asDisguise = disguised;
        try {
            built = MobBodies.build(level, Math.max(e.tag().idOffset, 1), () -> makeBody(level, arch, asDisguise));
        } catch (Throwable t) {
            Ec.LOG.warn("[ads] cannot build a " + arch.id + ": " + t.getMessage());
            return false;
        }
        Entity body = built.body;
        try {
            body.setUUID(e.plan.id);
            body.snapTo(e.x, e.y, e.z, e.plan.yaw, 0f);
            if (body instanceof LivingEntity le) { le.yHeadRot = e.plan.yaw; le.yBodyRot = e.plan.yaw; }
            body.setSilent(true);
            body.setPermanentlyInvulnerable(true);
            if (body instanceof Mob m) { m.setPersistenceRequired(); m.setCanPickUpLoot(false); }
            dress(body, e);
            if (healthData && body instanceof LivingEntity le) MobBodies.setHealthData(le, e.record.hp);
            String text = tagText(e);
            MobBodies.dressTag(built.stand, Sim.legacy(text));
            if (built.stand instanceof TransientStand ts) { ts.trackSquare = TAG_RANGE; tags.add(ts); }
            built.stand.snapTo(e.x, e.y + e.tag().dy, e.z, 0f, 0f);
            for (Entity f : built.fillers) f.snapTo(e.x, e.y, e.z, 0f, 0f);
            Sim.spawn(body);
            e.builtGameTime = level.getGameTime();
            for (Entity f : built.fillers) Sim.spawn(f);
            Sim.spawn(built.stand);
            e.body = body; e.fillers = built.fillers; e.stand = built.stand; e.lastTag = text;
            e.record.entityId = body.getId();
            e.live = true;
            return true;
        } catch (Throwable t) {
            Ec.LOG.warn("[ads] cannot place a " + arch.id + ": " + t.getMessage());
            body.discard();
            for (Entity f : built.fillers) f.discard();
            built.stand.discard();
            return false;
        }
    }

    private static Entity makeBody(ServerLevel level, MobArchetype arch, boolean disguised) {
        if (disguised) return new AdsDisguise(level);
        return switch (arch.entity) {
            case "minecraft:skeleton" -> new AdsSkeleton(level);
            case "minecraft:wither_skeleton" -> new AdsWitherSkeleton(level);
            case "minecraft:enderman" -> new AdsEnderman(level);
            case "minecraft:silverfish" -> new AdsSilverfish(level);
            case "minecraft:giant" -> new AdsGiant(level);
            default -> new AdsZombie(level);
        };
    }

    private static void dress(Entity body, MobEntry e) {
        MobArchetype arch = e.arch;
        if (arch.bodyName != null) { body.setCustomName(Component.literal(arch.bodyName)); body.setCustomNameVisible(false); }
        if (!(body instanceof LivingEntity le)) return;
        MobEquipment eq = arch.equipment;
        if (BloodLooks.isBlood(arch) && !(le instanceof AdsGiant)) {
            le.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
            le.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
            le.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
            le.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.CHAINMAIL_BOOTS));
        } else if (le instanceof AdsGiant) {
            le.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            le.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            le.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
            le.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        } else if (MobGear.has(arch.id)) {
            MobGear.dress(le, arch.id, e.plan.id);
        } else if (LevelLooks.isLevel(arch)) {
            le.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack("minecraft:bone".equals(LevelLooks.hand(arch)) ? Items.BONE : Items.BOW));
        } else if (eq != null) {
            if ("minecraft:iron_sword".equals(eq.mainhand)) {
                ItemStack sword = new ItemStack(Items.IRON_SWORD);
                if (eq.mainhandName != null) sword.set(DataComponents.CUSTOM_NAME, Component.literal(eq.mainhandName));
                le.setItemSlot(EquipmentSlot.MAINHAND, sword);
            }
            if ("minecraft:leather_boots".equals(eq.feet)) le.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        } else if (le instanceof AdsDisguise) {
            le.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        } else if (le instanceof AdsSkeleton) {
            le.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        } else if (le instanceof AdsWitherSkeleton) {
            le.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        }
        if (e.asleep || e.hidden) hide(le);
    }

    private static void hide(LivingEntity body) {
        body.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, true, true, false));
    }

    private static void show(Entity body) {
        if (body instanceof LivingEntity le) { le.removeEffect(MobEffects.INVISIBILITY); le.setInvisible(false); }
    }

    public static void wake(MobEntry e) {
        e.asleep = false;
        show(e.body);
    }

    public static void reveal(MobEntry e) {
        e.hidden = false;
        show(e.body);
    }

    public static void updateTag(MobEntry e) {
        if (e.stand == null) return;
        String text = tagText(e);
        if (text.equals(e.lastTag)) return;
        e.lastTag = text;
        e.stand.setCustomName(Sim.legacy(text));
    }

    public static void follow(MobEntry e) {
        Entity body = e.body;
        if (body == null) return;
        e.x = body.getX(); e.y = body.getY(); e.z = body.getZ();
        e.record.x = body.getX(); e.record.y = body.getY(); e.record.z = body.getZ();
        if (e.stand != null) {
            Entity s = e.stand;
            if (s.getX() != body.getX() || s.getY() != body.getY() + e.tag().dy || s.getZ() != body.getZ()) s.setPos(body.getX(), body.getY() + e.tag().dy, body.getZ());
        }
        for (Entity f : e.fillers) if (f.getX() != body.getX() || f.getY() != body.getY() || f.getZ() != body.getZ()) f.setPos(body.getX(), body.getY(), body.getZ());
    }

    public static void dematerialise(MobEntry e) {
        if (e.body != null && !e.body.isRemoved()) e.body.discard();
        for (Entity f : e.fillers) if (!f.isRemoved()) f.discard();
        if (e.stand != null && !e.stand.isRemoved()) e.stand.discard();
        e.body = null; e.fillers = Collections.emptyList(); e.stand = null;
        e.live = false;
    }

    private static final Set<String> LONG_DEATH = Set.of("crypt_undead", "giant", "withermancer", "wither_miner", "wither_guard", "skeletor_prime", "undead_skeleton", "parasite");
    public static final long DEATH_TICKS = 2L;
    public static final long LONG_DEATH_TICKS = 21L;

    public static long deathDelay(MobArchetype arch) { return LONG_DEATH.contains(arch.id) ? LONG_DEATH_TICKS : DEATH_TICKS; }

    private static final class Dying {
        final Entity body;
        final List<Entity> fillers;
        final Entity stand;
        final long removeAt;

        Dying(Entity body, List<Entity> fillers, Entity stand, long removeAt) { this.body = body; this.fillers = fillers; this.stand = stand; this.removeAt = removeAt; }
    }

    private static final ArrayList<Dying> dying = new ArrayList<>();

    public static void die(MobEntry e, ServerLevel level) {
        Entity body = e.body;
        if (body != null && !body.isRemoved()) {
            Consumer<Packet<ClientGamePacketListener>> send = p -> level.getChunkSource().sendToTrackingPlayers(body, p);
            send.accept(new ClientboundHurtAnimationPacket(body.getId(), 0f));
            if (body instanceof AdsDisguise) {
                List<Pair<EquipmentSlot, ItemStack>> slots = new ArrayList<>();
                for (EquipmentSlot s : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD)) slots.add(Pair.of(s, ItemStack.EMPTY));
                send.accept(new ClientboundSetEquipmentPacket(body.getId(), slots));
            }
            send.accept(new ClientboundEntityEventPacket(body, (byte) 3));
            long delay = deathDelay(e.arch);
            if (delay > DEATH_TICKS) updateTag(e);
            dying.add(new Dying(body, e.fillers, e.stand, level.getGameTime() + delay));
            e.body = null; e.fillers = Collections.emptyList(); e.stand = null;
            e.live = false;
            return;
        }
        dematerialise(e);
    }

    public static final int TAG_RANGE = 24;

    public static final int LOOKUP_GRACE = 40;

    private static final ArrayList<TransientStand> tags = new ArrayList<>();

    private static Field entityMapField;
    private static Method updatePlayer = null;

    private static synchronized Field entityMapField() throws NoSuchFieldException {
        if (entityMapField == null) {
            Field f = ChunkMap.class.getDeclaredField("entityMap");
            f.setAccessible(true);
            entityMapField = f;
        }
        return entityMapField;
    }

    private static void trackTags(ServerLevel level) {
        tags.removeIf(Entity::isRemoved);
        if (tags.isEmpty()) return;
        try {
            Int2ObjectMap<?> map = (Int2ObjectMap<?>) entityMapField().get(level.getChunkSource().chunkMap);
            List<ServerPlayer> players = level.players();
            for (TransientStand t : tags) {
                Object tracked = map.get(t.getId());
                if (tracked == null) continue;
                Method m = updatePlayer;
                if (m == null) {
                    m = tracked.getClass().getMethod("updatePlayer", ServerPlayer.class);
                    m.setAccessible(true);
                    updatePlayer = m;
                }
                for (ServerPlayer p : players) m.invoke(tracked, p);
            }
        } catch (Throwable t) {
            if (!trackWarned) { trackWarned = true; Ec.LOG.warn("[ads] tag tracking: " + t.getMessage()); }
        }
    }

    private static boolean trackWarned = false;

    public static void tick(ServerLevel level) {
        trackTags(level);
        if (dying.isEmpty()) return;
        long now = level.getGameTime();
        Iterator<Dying> it = dying.iterator();
        while (it.hasNext()) {
            Dying d = it.next();
            if (now < d.removeAt) continue;
            it.remove();
            discardDying(d);
        }
    }

    public static void flushDying() {
        for (Dying d : dying) discardDying(d);
        dying.clear();
    }

    private static void discardDying(Dying d) {
        if (!d.body.isRemoved()) d.body.discard();
        for (Entity f : d.fillers) if (!f.isRemoved()) f.discard();
        if (d.stand != null && !d.stand.isRemoved()) d.stand.discard();
    }

    public static void retract(MobEntry e) {
        GameProfile p = e.profile;
        if (p == null) return;
        if (e.announced) Disguise.retract(p.id(), p.name());
        e.announced = false;
    }
}
