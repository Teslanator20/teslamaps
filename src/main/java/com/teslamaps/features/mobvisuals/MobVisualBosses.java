package com.teslamaps.features.mobvisuals;

import com.teslamaps.utils.EntityCache;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.squid.GlowSquid;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Area bosses, body-type sea creatures, player-type mobs and Invisibugs.
 * Ported from GanKura's EntityHighlightManager.
 */
final class MobVisualBosses {

    private static final Vec3 BLADESOUL_POS = new Vec3(-294.5, 83.5, -517.5);
    private static final Vec3 ASHFANG_POS = new Vec3(-484.5, 137.5, -1015.5);
    private static final double BLADESOUL_AREA_XZ = 35.0;
    private static final double ASHFANG_AREA_XZ = 30.0;
    private static final double CRIMSON_AREA_Y = 5.0;
    private static final double BLADESOUL_PAIR_XZ = 3.0;
    private static final double BLADESOUL_PAIR_Y = 6.0;
    private static final float LOTUM_MAX_WIDTH = 0.7f;
    private static final double INVISIBUG_RADIUS = 5.0;
    private static final int MAX_PENDING_CRITS = 256;
    private static final String CORLEONE_SKIN_HASH = "3c37b434bbb65fe5838afced8301604126214b2a";

    /** Entity names of the NPC-player mobs; sea creature NPCs use their label. */
    private static final Map<Mob, String> PLAYER_NAMES = new LinkedHashMap<>();
    /** Sea creatures found by nametag, then the body type next to it. Longest names first. */
    private static final Map<Mob, Pattern> SEA_PATTERNS = new LinkedHashMap<>();
    private static final Pattern SEA_DECOYS = Pattern.compile("(?i)Wiki Tiki Laser Totem");

    static {
        PLAYER_NAMES.put(Mob.BARBARIAN_DUKE_X, "DukeBarb");
        PLAYER_NAMES.put(Mob.MAGE_OUTLAW, "Mage Outlaw");
        PLAYER_NAMES.put(Mob.MATCHO, "Matcho");
        PLAYER_NAMES.put(Mob.HIDEYHO, "Hideyho");
        PLAYER_NAMES.put(Mob.GRIZZLY_BEAR, "Grizzly Bear");
        PLAYER_NAMES.put(Mob.LITTLEFOOT, "Littlefoot");
        for (Mob m : new Mob[]{Mob.ALLIGATOR, Mob.YETI, Mob.PHANTOM_FISHER, Mob.GRIM_REAPER,
                Mob.GREAT_WHITE_SHARK, Mob.ABYSSAL_MINER, Mob.RAGNAROK}) {
            PLAYER_NAMES.put(m, m.plainLabel());
        }

        List<Mob> sea = new ArrayList<>();
        for (Mob m : Mob.of(MobArea.SEA_CREATURE)) if (bodyType(m) != null) sea.add(m);
        sea.sort(Comparator.comparingInt((Mob m) -> m.plainLabel().length()).reversed());
        for (Mob m : sea) {
            SEA_PATTERNS.put(m, Pattern.compile("(?i)(?<![A-Za-z])" + Pattern.quote(m.plainLabel()) + "(?![A-Za-z])"));
        }
    }

    private static final ArrayDeque<Vec3> pendingCrits = new ArrayDeque<>();
    private static final Set<Entity> invisibugs = new HashSet<>();
    private static final Set<Entity> claimed = new HashSet<>();

    private MobVisualBosses() {}

    /** Main thread only; the netty-thread call of the same hook is ignored. */
    static void onParticle(ClientboundLevelParticlesPacket packet) {
        if (!Minecraft.getInstance().isSameThread()) return;
        if (packet.getParticle().getType() != ParticleTypes.CRIT) return;
        if (!MobArea.MOONGLADE_MARSH.isHere() || !invisibugWanted()) return;
        if (pendingCrits.size() >= MAX_PENDING_CRITS) return;
        pendingCrits.add(new Vec3(packet.getX(), packet.getY(), packet.getZ()));
    }

    private static boolean any(Mob... mobs) {
        for (Mob m : mobs) if (m.anyEnabled()) return true;
        return false;
    }

    static void scan(Minecraft mc) {
        claimed.clear();
        boolean end = MobArea.THE_END.isHere();
        boolean den = MobArea.SPIDERS_DEN.isHere();
        boolean crimson = MobArea.CRIMSON_ISLE.isHere();
        boolean safari = MobArea.SAFARI_CAVERN.isHere();
        boolean marsh = MobArea.MOONGLADE_MARSH.isHere();

        if (end && any(Mob.GOLEM, Mob.DRAGON)) scanEnd(mc);
        if (crimson && Mob.ASHFANG.anyEnabled()) scanAshfang(mc);
        if (crimson && Mob.BLADESOUL.anyEnabled()) scanBladesoul(mc);
        if (safari && any(Mob.WUMPA, Mob.DOOMSPIRAL)) scanSafariTypes(mc);
        scanPlayers(mc);
        scanSeaTypes(mc);

        boolean nametags = (den && any(Mob.BROODMOTHER, Mob.ARACHNE, Mob.ARACHNE_BROOD))
                || (crimson && any(Mob.ASHFANG_FOLLOWER, Mob.ASHFANG_ACOLYTE, Mob.ASHFANG_UNDERLING,
                        Mob.MAGMA_BOSS, Mob.MAGMA_GLARE))
                || (marsh && any(Mob.STAG_BEETLE, Mob.WOODLOUSE))
                || anySeaPattern();
        if (nametags) scanNametags(mc, den, crimson, marsh);

        if (marsh && invisibugWanted()) scanInvisibugs(mc);
        else {
            invisibugs.clear();
            pendingCrits.clear();
        }
    }

    private static boolean anySeaPattern() {
        for (Mob m : SEA_PATTERNS.keySet()) if (m.anyEnabled()) return true;
        return false;
    }

    // --- type only ---

    private static void scanEnd(Minecraft mc) {
        for (Entity e : EntityCache.all()) {
            if (e.isRemoved() || e.isInvisible()) continue;
            if (e instanceof IronGolem) MobVisuals.register(mc, e, Mob.GOLEM);
            else if (e instanceof EnderDragon) MobVisuals.register(mc, e, Mob.DRAGON);
        }
    }

    private static void scanSafariTypes(Minecraft mc) {
        for (Entity e : EntityCache.all()) {
            if (e.isRemoved()) continue;
            Mob mob = e instanceof Ravager ? Mob.WUMPA : e instanceof Warden ? Mob.DOOMSPIRAL : null;
            if (mob != null && MobVisuals.inSafariBiome(mc.player, mob.area()) && MobVisuals.inSafariBiome(e, mob.area())) {
                MobVisuals.register(mc, e, mob);
            }
        }
    }

    private static void scanAshfang(Minecraft mc) {
        AABB area = new AABB(ASHFANG_POS.x - ASHFANG_AREA_XZ, ASHFANG_POS.y - CRIMSON_AREA_Y, ASHFANG_POS.z - ASHFANG_AREA_XZ,
                ASHFANG_POS.x + ASHFANG_AREA_XZ, ASHFANG_POS.y + CRIMSON_AREA_Y, ASHFANG_POS.z + ASHFANG_AREA_XZ);
        List<Blaze> blazes = new ArrayList<>(mc.level.getEntitiesOfClass(Blaze.class, area, e -> true));
        if (blazes.size() < 2) return;
        blazes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(ASHFANG_POS)));
        Blaze a = blazes.get(0), b = blazes.get(1);
        Blaze top = a.getY() >= b.getY() ? a : b;
        Blaze bottom = top == a ? b : a;
        MobVisuals.register(mc, top, Mob.ASHFANG);
        glowOnly(bottom, Mob.ASHFANG);
        claimed.add(a);
        claimed.add(b);
    }

    private static void scanBladesoul(Minecraft mc) {
        AABB area = new AABB(BLADESOUL_POS.x - BLADESOUL_AREA_XZ, BLADESOUL_POS.y - CRIMSON_AREA_Y, BLADESOUL_POS.z - BLADESOUL_AREA_XZ,
                BLADESOUL_POS.x + BLADESOUL_AREA_XZ, BLADESOUL_POS.y + CRIMSON_AREA_Y, BLADESOUL_POS.z + BLADESOUL_AREA_XZ);
        List<WitherSkeleton> skeletons = mc.level.getEntitiesOfClass(WitherSkeleton.class, area, e -> true);
        if (skeletons.isEmpty()) return;
        List<Blaze> blazes = mc.level.getEntitiesOfClass(Blaze.class, area.inflate(0, BLADESOUL_PAIR_Y, 0), e -> true);
        for (WitherSkeleton skeleton : skeletons) {
            for (Blaze blaze : blazes) {
                double dx = blaze.getX() - skeleton.getX(), dz = blaze.getZ() - skeleton.getZ();
                if (dx * dx + dz * dz > BLADESOUL_PAIR_XZ * BLADESOUL_PAIR_XZ) continue;
                if (Math.abs(blaze.getY() - skeleton.getY()) > BLADESOUL_PAIR_Y) continue;
                MobVisuals.register(mc, skeleton, Mob.BLADESOUL);
                glowOnly(blaze, Mob.BLADESOUL);
                claimed.add(skeleton);
                claimed.add(blaze);
                return;
            }
        }
    }

    // --- NPC players ---

    private static void scanPlayers(Minecraft mc) {
        boolean corleone = MobArea.CRYSTAL_HOLLOWS.isHere() && Mob.BOSS_CORLEONE.anyEnabled();
        boolean named = false;
        for (Mob m : PLAYER_NAMES.keySet()) if (m.anyEnabled()) { named = true; break; }
        if (!named && !corleone) return;

        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isRemoved()) continue;
            if (corleone && p instanceof AbstractClientPlayer acp
                    && acp.getSkin().body().texturePath().toString().endsWith(CORLEONE_SKIN_HASH)) {
                MobVisuals.register(mc, p, Mob.BOSS_CORLEONE);
                continue;
            }
            if (!named || p.getUUID().version() == 4) continue;
            String name = p.getName().getString().trim();
            for (Map.Entry<Mob, String> entry : PLAYER_NAMES.entrySet()) {
                Mob mob = entry.getKey();
                if (!mob.anyEnabled() || !name.equalsIgnoreCase(entry.getValue())) continue;
                if (mob.area().isSafari() && !MobVisuals.inSafariBiome(mc.player, mob.area())) break;
                MobVisuals.register(mc, p, mob);
                if (mob == Mob.RAGNAROK && p.getVehicle() != null) glowOnly(p.getVehicle(), mob);
                break;
            }
        }
    }

    // --- sea creatures by body type ---

    private static void scanSeaTypes(Minecraft mc) {
        boolean crimson = FishArea.CRIMSON_ISLE.isHere() && any(Mob.THUNDER, Mob.LORD_JAWBUS);
        boolean canyon = FishArea.TORRHUS_CANYON.isHere() && Mob.SILKBREEZE.anyEnabled();
        boolean jerry = FishArea.JERRYS_WORKSHOP.isHere() && Mob.REINDRAKE.anyEnabled();
        boolean atoll = FishArea.LOTUS_ATOLL.isHere() && any(Mob.FROG_PRINCE, Mob.PUDDLE_JUMPER);
        boolean marsh = FishArea.MOONGLADE_MARSH.isHere() && Mob.THE_LOCH_EMPEROR.anyEnabled();
        if (!crimson && !canyon && !jerry && !atoll && !marsh) return;

        for (Entity e : EntityCache.all()) {
            if (e.isRemoved() || e.isInvisible()) continue;
            if (crimson) {
                if (e instanceof ElderGuardian) seaCreature(mc, e, Mob.THUNDER);
                else if (e instanceof IronGolem) seaCreature(mc, e, Mob.LORD_JAWBUS);
            } else if (canyon) {
                if (e instanceof Breeze) seaCreature(mc, e, Mob.SILKBREEZE);
            } else if (jerry) {
                if (e instanceof EnderDragon) seaCreature(mc, e, Mob.REINDRAKE);
            } else if (atoll) {
                if (e instanceof Frog frog && frog.getBbWidth() > LOTUM_MAX_WIDTH) {
                    seaCreature(mc, e, frog.getVehicle() != null ? Mob.FROG_PRINCE : Mob.PUDDLE_JUMPER);
                }
            } else if (marsh && isLochEmperorBody(e)) {
                seaCreature(mc, e, Mob.THE_LOCH_EMPEROR);
            }
        }
    }

    /** Glow the whole build, tracer and nameplate on the part at eye height. */
    private static void seaCreature(Minecraft mc, Entity body, Mob mob) {
        if (!mob.anyEnabled() || claimed.contains(body)) return;
        Entity visual = body;
        if (mob == Mob.FROG_PRINCE && body.getVehicle() != null) {
            glowOnly(body.getVehicle(), mob);
            claimed.add(body.getVehicle());
        }
        if (mob == Mob.THE_LOCH_EMPEROR) {
            for (Entity passenger : body.getPassengers()) {
                if (passenger instanceof Skeleton) { visual = passenger; break; }
            }
            if (visual != body) glowOnly(body, mob);
        }
        claimed.add(body);
        claimed.add(visual);
        MobVisuals.register(mc, visual, mob);
    }

    private static boolean isLochEmperorBody(Entity e) {
        if (!(e instanceof Guardian) || e instanceof ElderGuardian) return false;
        for (Entity passenger : e.getPassengers()) if (passenger instanceof Skeleton) return true;
        return false;
    }

    private static Class<? extends Entity> bodyType(Mob mob) {
        return switch (mob) {
            case THE_LOCH_EMPEROR -> Guardian.class;
            case PUDDLE_JUMPER, FROG_PRINCE -> Frog.class;
            case SILKBREEZE -> Breeze.class;
            case THUNDER -> ElderGuardian.class;
            case LORD_JAWBUS -> IronGolem.class;
            case REINDRAKE -> EnderDragon.class;
            case MITHRIL_GRUBBER, WATER_WORM, FLAMING_WORM -> Silverfish.class;
            case OASIS_SHEEP -> Sheep.class;
            case OASIS_RABBIT, CARROT_KING -> Rabbit.class;
            case AGARIMOO -> MushroomCow.class;
            case POISONED_WATER_WORM -> Endermite.class;
            case LAVA_BLAZE -> Blaze.class;
            case LAVA_PIGMAN -> ZombifiedPiglin.class;
            case PLHLEGBLAST -> Squid.class;
            default -> null;
        };
    }

    private static boolean bodyMatches(Mob mob, Entity e) {
        if (e.isInvisible()) return false;
        return switch (mob) {
            case CARROT_KING -> e instanceof Rabbit r && r.getVariant() == Rabbit.Variant.EVIL;
            case OASIS_RABBIT -> e instanceof Rabbit r && r.getVariant() != Rabbit.Variant.EVIL;
            case PLHLEGBLAST -> !(e instanceof GlowSquid);
            case THE_LOCH_EMPEROR -> isLochEmperorBody(e);
            case FROG_PRINCE -> e.getVehicle() != null;
            case PUDDLE_JUMPER -> e.getVehicle() == null;
            default -> true;
        };
    }

    // --- nametag driven ---

    private static void scanNametags(Minecraft mc, boolean den, boolean crimson, boolean marsh) {
        boolean sea = anySeaPattern();
        for (ArmorStand tag : EntityCache.namedArmorStands()) {
            if (tag.isRemoved() || tag.getCustomName() == null) continue;
            String name = tag.getCustomName().getString();

            if (den) {
                if (name.contains("Broodmother") && Mob.BROODMOTHER.anyEnabled()) {
                    bossNear(mc, tag, Mob.BROODMOTHER, Spider.class, 8.0);
                    continue;
                }
                if (name.contains("Arachne's Brood") && (name.contains("Lv100") || name.contains("Lv200"))) {
                    if (Mob.ARACHNE_BROOD.anyEnabled()) bossNear(mc, tag, Mob.ARACHNE_BROOD, net.minecraft.world.entity.Mob.class, 8.0);
                    continue;
                }
                if (name.contains("Arachne") && (name.contains("Lv300") || name.contains("Lv500"))) {
                    if (Mob.ARACHNE.anyEnabled()) bossNear(mc, tag, Mob.ARACHNE, net.minecraft.world.entity.Mob.class, 8.0);
                    continue;
                }
            }

            if (crimson) {
                Mob follower = name.contains("Ashfang Follower") ? Mob.ASHFANG_FOLLOWER
                        : name.contains("Ashfang Acolyte") ? Mob.ASHFANG_ACOLYTE
                        : name.contains("Ashfang Underling") ? Mob.ASHFANG_UNDERLING : null;
                if (follower != null) {
                    if (follower.anyEnabled()) bossNear(mc, tag, follower, net.minecraft.world.entity.Mob.class, 8.0);
                    continue;
                }
                if (name.contains("Magma Glare")) {
                    if (Mob.MAGMA_GLARE.anyEnabled()) bossNear(mc, tag, Mob.MAGMA_GLARE, MagmaCube.class, 3.0);
                    continue;
                }
                if (name.contains("Magma Boss")) {
                    if (Mob.MAGMA_BOSS.anyEnabled()) bossNear(mc, tag, Mob.MAGMA_BOSS, MagmaCube.class, 20.0);
                    continue;
                }
            }

            if (marsh) {
                Mob bug = name.contains("Stag Beetle") ? Mob.STAG_BEETLE : name.contains("Woodlouse") ? Mob.WOODLOUSE : null;
                if (bug != null) {
                    if (bug.anyEnabled()) headStandUnder(mc, tag, bug);
                    continue;
                }
            }

            if (sea && !SEA_DECOYS.matcher(name).find()) {
                for (Map.Entry<Mob, Pattern> entry : SEA_PATTERNS.entrySet()) {
                    Mob mob = entry.getKey();
                    if (!mob.anyEnabled() || !entry.getValue().matcher(name).find()) continue;
                    Entity body = nearest(mc, tag, bodyType(mob), 4.0, e -> bodyMatches(mob, e) && !claimed.contains(e));
                    if (body != null) seaCreature(mc, body, mob);
                    else MobVisuals.registerNoGlow(mc, tag, mob);
                    break;
                }
            }
        }
    }

    /** Body next to the nametag; without one the nametag still carries tracer and nameplate. */
    private static void bossNear(Minecraft mc, Entity tag, Mob mob, Class<? extends Entity> type, double radius) {
        Entity body = nearest(mc, tag, type, radius,
                e -> !(e instanceof AbstractSkeleton) && !(e instanceof ArmorStand) && !claimed.contains(e));
        if (body == null) {
            MobVisuals.registerNoGlow(mc, tag, mob);
            return;
        }
        claimed.add(body);
        MobVisuals.register(mc, body, mob);
    }

    private static void headStandUnder(Minecraft mc, Entity tag, Mob mob) {
        Entity head = nearest(mc, tag, ArmorStand.class, 3.0, e -> e != tag && e instanceof ArmorStand s
                && s.getCustomName() == null && !s.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !claimed.contains(e));
        if (head == null) {
            MobVisuals.registerNoGlow(mc, tag, mob);
            return;
        }
        claimed.add(head);
        MobVisuals.register(mc, head, mob);
    }

    private static Entity nearest(Minecraft mc, Entity center, Class<? extends Entity> type, double radius,
                                  java.util.function.Predicate<Entity> filter) {
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntitiesOfClass(type, center.getBoundingBox().inflate(radius), filter)) {
            double d = e.distanceToSqr(center);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    private static void glowOnly(Entity e, Mob mob) {
        if (!mob.glow()) return;
        MobVisuals.glowing.add(e);
        MobVisuals.glowColors.put(e, mob.glowColorRGB());
    }

    // --- Invisibug: nearest bare armor stand to a CRIT particle ---

    private static void scanInvisibugs(Minecraft mc) {
        invisibugs.removeIf(Entity::isRemoved);
        Vec3 pos;
        while ((pos = pendingCrits.poll()) != null) {
            boolean known = false;
            for (Entity e : invisibugs) {
                if (e.distanceToSqr(pos) < INVISIBUG_RADIUS * INVISIBUG_RADIUS) { known = true; break; }
            }
            if (known) continue;
            AABB box = AABB.ofSize(pos, INVISIBUG_RADIUS * 2, INVISIBUG_RADIUS * 2, INVISIBUG_RADIUS * 2);
            ArmorStand best = null;
            double bestD = Double.MAX_VALUE;
            for (ArmorStand stand : mc.level.getEntitiesOfClass(ArmorStand.class, box, e -> true)) {
                double d = stand.distanceToSqr(pos);
                if (d < bestD) { bestD = d; best = stand; }
            }
            if (best != null && isBareStand(best)) invisibugs.add(best);
        }
        for (Entity e : invisibugs) MobVisuals.register(mc, e, Mob.INVISIBUG);
    }

    static java.util.Set<Entity> invisibugs() { return invisibugs; }

    private static boolean invisibugWanted() {
        return Mob.INVISIBUG.anyEnabled() || com.teslamaps.config.TeslaMapsConfig.get().mobVisualInvisibugBox;
    }

    private static boolean isBareStand(ArmorStand stand) {
        if (stand.getCustomName() != null) return false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!stand.getItemBySlot(slot).isEmpty()) return false;
        }
        return true;
    }
}
