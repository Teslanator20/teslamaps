package com.teslamaps.slayer;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.NametagMobs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResult;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

public class SlayerHUD {
    private static final Pattern INFERNO_PATTERN = Pattern.compile("Inferno Demonlord (I{1,3}V?|IV|V)\\s*(ᛤ)?\\s*([\\d.]+)([kKmMbB]?)❤");

    private static final Pattern PHASE_PATTERN = Pattern.compile("(SPIRIT|ASHEN|CRYSTAL|AURIC)\\s*♨(\\d+)");

    private static final Pattern MINIBOSS_PATTERN = Pattern.compile("♨\\s*(Burningsoul|Kindleheart)\\s*Demon\\s*([\\d.]+)([kKmMbB]?)❤");

    private static final Pattern QUAZII_PATTERN = Pattern.compile("ⓆⓊⒶⓏⒾⒾ\\s*([\\d.]+)([kKmMbB]?)❤");
    private static final Pattern TYPHOEUS_PATTERN = Pattern.compile("ⓉⓎⓅⒽⓄⒺⓊⓈ\\s*([\\d.]+)([kKmMbB]?)❤");

    private static final Pattern SPAWNED_BY_PATTERN = Pattern.compile("(?i)spawned\\s*by:?\\s*(\\w+)");

    // non-Inferno slayer bosses/minibosses, matched against nametag armor stands (need a ❤ HP tag)
    private static final String[] GENERIC_BOSSES = {
        "Revenant Horror", "Atoned Horror", "Tarantula Broodfather", "Sven Packmaster",
        "Voidgloom Seraph", "Riftstalker Bloodfiend"
    };
    private static final String[] GENERIC_MINIBOSSES = {
        "Revenant Sycophant", "Revenant Champion", "Deformed Revenant", "Atoned Champion", "Atoned Revenant",
        "Tarantula Vermin", "Tarantula Beast", "Mutant Tarantula",
        "Pack Enforcer", "Sven Follower", "Sven Alpha",
        "Voidling Devotee", "Voidling Radical", "Voidcrazed Maniac"
    };

    private static boolean containsAny(String name, String[] names) {
        for (String n : names) if (name.contains(n)) return true;
        return false;
    }

    private static final double[] INFERNO_MAX_HP = {
        0,          // Tier 0 (unused)
        250_000,    // Tier I
        1_000_000,  // Tier II
        5_000_000,  // Tier III
        50_000_000, // Tier IV
        200_000_000 // Tier V
    };

    private static String currentBossName = null;
    private static double currentHP = 0;
    private static double maxHP = 0;
    private static double phaseMaxHP = 0;  // Max HP for current phase
    private static int bossTier = 0;
    private static boolean hasShield = false;
    private static long lastUpdateTime = 0;
    private static Entity bossEntity = null;  // The actual boss entity for ESP

    private static String currentPhaseType = null;  // SPIRIT, ASHEN, CRYSTAL, AURIC
    private static int phaseCount = 0;

    private static final Set<Entity> minibossEntities = new HashSet<>();
    private static final java.util.IdentityHashMap<Entity, Integer> minibossColors = new java.util.IdentityHashMap<>();
    private static String miniboss1Type = null;
    private static String miniboss2Type = null;

    private static double quaziiHP = 0;
    private static double typhoeusHP = 0;
    private static long quaziiLastSeen = 0;
    private static long typhoeusLastSeen = 0;
    private static boolean quaziiDead = false;
    private static boolean typhoeusDead = false;
    private static final long MINION_TIMEOUT = 5000; // 5 seconds before hiding if not seen

    private static long questStartTime = 0;       // When the slayer quest FIRST started (persists through demon phases)
    private static long lastKillTime = 0;         // When the last boss was killed
    private static boolean questActive = false;   // Is a slayer quest currently active?

    /** True while a slayer quest is running. */
    public static boolean isActive() { return questActive; }
    private static final List<Long> killTimes = new ArrayList<>();    // Fight durations
    private static final List<Long> spawnTimes = new ArrayList<>();   // Time between kills
    private static int totalKills = 0;            // Total kills this session

    private static String bossOwner = null;       // Who spawned this boss
    private static boolean isOwnBoss = false;     // Is this our boss?
    private static ArmorStand lockedTag = null;   // foreign boss the player hit last
    private static ArmorStand carryTag = null;    // boss picked with middle click in carry mode
    private static ArmorStand chosenTag = null;
    private static boolean middleDown = false;
    private static long lastCompleteMs = 0;

    private static String lastWorldName = null;
    private static Object lastLevel = null;
    private static final java.util.HashMap<Integer, String> ownerCache = new java.util.HashMap<>();
    private static final java.util.HashMap<Integer, Entity> pairedCache = new java.util.HashMap<>();

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            resetQuest();
            lastWorldName = null;
            return;
        }

        String currentWorldName = mc.level.dimension().identifier().toString();
        if (lastWorldName != null && !lastWorldName.equals(currentWorldName)) {
            resetQuest();
        }
        lastWorldName = currentWorldName;
        if (mc.level != lastLevel || ownerCache.size() > 256 || pairedCache.size() > 256) {
            lastLevel = mc.level;
            ownerCache.clear();
            pairedCache.clear();
        }

        minibossEntities.clear();
        minibossColors.clear();
        miniboss1Type = null;
        miniboss2Type = null;

        boolean foundQuazii = false;
        boolean foundTyphoeus = false;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        AABB searchBox = new AABB(px - 30, py - 10, pz - 30, px + 30, py + 10, pz + 30);

        boolean foundBoss = false;
        currentPhaseType = null;
        phaseCount = 0;
        bossEntity = null;

        List<ArmorStand> stands = mc.level.getEntitiesOfClass(ArmorStand.class, searchBox, as -> true);
        String[] names = new String[stands.size()];
        for (int i = 0; i < names.length; i++) names[i] = stands.get(i).getName().getString();
        tickCarryClick(mc);
        ArmorStand chosen = pickBoss(mc, stands, names);
        chosenTag = chosen;

        for (int si = 0; si < names.length; si++) {
            ArmorStand armorStand = stands.get(si);
            String name = names[si];

            Matcher bossMatcher = INFERNO_PATTERN.matcher(name);
            if (bossMatcher.find()) {
                if (armorStand != chosen) continue;
                String tierStr = bossMatcher.group(1);
                String shield = bossMatcher.group(2);
                String hpNum = bossMatcher.group(3);
                String hpSuffix = bossMatcher.group(4);

                bossTier = parseTier(tierStr);
                currentHP = parseHP(hpNum, hpSuffix);
                hasShield = shield != null && !shield.isEmpty();

                double baseMax = bossTier > 0 && bossTier < INFERNO_MAX_HP.length ? INFERNO_MAX_HP[bossTier] : 50_000_000;
                if (hasShield) {
                    maxHP = baseMax * 3;
                    phaseMaxHP = baseMax * 3;
                } else {
                    maxHP = baseMax;
                    phaseMaxHP = baseMax;
                }

                currentBossName = "Inferno Demonlord " + tierStr;
                lastUpdateTime = System.currentTimeMillis();
                foundBoss = true;

                if (currentHP <= 0) {
                    clearBossVisuals();
                    foundBoss = false;
                    continue;
                }

                if (!questActive) {
                    questStartTime = System.currentTimeMillis();
                    questActive = true;
                }

                findBossEntity(mc, armorStand, name);
                continue;
            }

            Matcher phaseMatcher = PHASE_PATTERN.matcher(name);
            if (phaseMatcher.find()) {
                currentPhaseType = phaseMatcher.group(1);
                phaseCount = Integer.parseInt(phaseMatcher.group(2));
                continue;
            }

            Matcher minibossMatcher = MINIBOSS_PATTERN.matcher(name);
            if (minibossMatcher.find()) {
                String type = minibossMatcher.group(1);
                if (miniboss1Type == null) {
                    miniboss1Type = type;
                } else if (miniboss2Type == null && !type.equals(miniboss1Type)) {
                    miniboss2Type = type;
                }
                findMinibossEntity(mc, armorStand, type);
            }

            Matcher quaziiMatcher = QUAZII_PATTERN.matcher(name);
            if (quaziiMatcher.find()) {
                double hp = parseHP(quaziiMatcher.group(1), quaziiMatcher.group(2));
                quaziiLastSeen = System.currentTimeMillis();
                foundQuazii = true;
                if (hp <= 0) {
                    quaziiDead = true;
                    quaziiHP = 0;
                } else {
                    quaziiHP = hp;
                }
            }

            Matcher typhoeusMatcher = TYPHOEUS_PATTERN.matcher(name);
            if (typhoeusMatcher.find()) {
                double hp = parseHP(typhoeusMatcher.group(1), typhoeusMatcher.group(2));
                typhoeusLastSeen = System.currentTimeMillis();
                foundTyphoeus = true;
                if (hp <= 0) {
                    typhoeusDead = true;
                    typhoeusHP = 0;
                } else {
                    typhoeusHP = hp;
                }
            }

            if (name.contains("❤")) {
                if (containsAny(name, GENERIC_BOSSES)) {
                    if (armorStand != chosen) continue;
                    if (!questActive) {
                        questStartTime = System.currentTimeMillis();
                        questActive = true;
                    }
                    lastUpdateTime = System.currentTimeMillis();
                    foundBoss = true;
                    findBossEntity(mc, armorStand, name);
                    continue;
                }
                if (containsAny(name, GENERIC_MINIBOSSES)) {
                    findMinibossEntity(mc, armorStand, name);
                    continue;
                }
            }
        }

        if (!foundBoss && System.currentTimeMillis() - lastUpdateTime > 10000) {
            clearBossVisuals();
        }

        long now = System.currentTimeMillis();
        if (!foundQuazii && !quaziiDead && now - quaziiLastSeen > MINION_TIMEOUT) {
            quaziiHP = 0; // Hide after timeout
        }
        if (!foundTyphoeus && !typhoeusDead && now - typhoeusLastSeen > MINION_TIMEOUT) {
            typhoeusHP = 0; // Hide after timeout
        }
    }

    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!level.isClientSide() || !TeslaMapsConfig.get().section("Combat")) return InteractionResult.PASS;
            if (entity instanceof ArmorStand || !(entity instanceof net.minecraft.world.entity.LivingEntity)) return InteractionResult.PASS;
            for (ArmorStand stand : NametagMobs.namedStands(entity, 1.5)) {
                if (isBossTag(stand.getName().getString())) {
                    lockedTag = stand;
                    break;
                }
            }
            return InteractionResult.PASS;
        });
    }

    private static final Pattern TIER_PATTERN = Pattern.compile("(?:Horror|Broodfather|Packmaster|Seraph|Demonlord|Bloodfiend) (V|IV|III|II|I)\\b");

    /** Slayer tier from a boss tag, 5 for Atoned Horror / Conjoined Brood, 0 if unknown. */
    public static int tagTier(String name) {
        Matcher m = TIER_PATTERN.matcher(name);
        if (m.find()) return parseTier(m.group(1));
        return name.contains("Atoned Horror") || name.contains("Conjoined Brood") ? 5 : 0;
    }

    // Skytils slayerCarryMode: middle click a boss to follow it
    private static void tickCarryClick(Minecraft mc) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        boolean down = c.slayerCarryMode && mc.screen == null && org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                mc.getWindow().handle(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (!c.slayerCarryMode) carryTag = null;
        if (down && !middleDown) {
            Entity target = mc.crosshairPickEntity;
            if (target != null && !(target instanceof ArmorStand)) {
                for (ArmorStand stand : NametagMobs.namedStands(target, 1.5)) {
                    if (isBossTag(stand.getName().getString())) {
                        carryTag = stand;
                        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(
                                "§6[TeslaMaps] §aFollowing §f" + com.teslamaps.utils.StringUtil.stripTrim(stand.getName().getString())));
                        break;
                    }
                }
            }
        }
        middleDown = down;
        if (carryTag != null && !carryTag.isAlive()) carryTag = null;
    }

    public static boolean isBossTag(String name) {
        return INFERNO_PATTERN.matcher(name).find() || (name.contains("❤") && containsAny(name, GENERIC_BOSSES));
    }

    /** Ported from Skyblocker's SlayerManager.findBoss: own boss first, then the one last hit. */
    private static ArmorStand pickBoss(Minecraft mc, List<ArmorStand> stands, String[] names) {
        String me = mc.getUser().getName();
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        boolean carry = cfg.slayerCarryMode;
        if (carry && carryTag != null && carryTag.isAlive()) {
            for (ArmorStand as : stands) {
                if (as != carryTag) continue;
                bossOwner = ownerOf(as);
                isOwnBoss = bossOwner != null && bossOwner.equalsIgnoreCase(me);
                return as;
            }
        }
        ArmorStand carried = null;
        String carriedOwner = null;
        ArmorStand foreign = null;
        String foreignOwner = null;
        ArmorStand locked = null;
        String lockedOwner = null;
        for (int i = 0; i < names.length; i++) {
            ArmorStand as = stands.get(i);
            String name = names[i];
            if (!isBossTag(name)) continue;
            if (!as.isAlive() || name.endsWith(" 0❤")) continue;
            String owner = ownerOf(as);
            if (owner != null && owner.equalsIgnoreCase(me)) {
                bossOwner = owner;
                isOwnBoss = true;
                return as;
            }
            if (as == lockedTag) {
                locked = as;
                lockedOwner = owner;
            } else if (carry && carried == null && tagTier(name) == cfg.slayerCarryTier) {
                carried = as;
                carriedOwner = owner;
            } else if (foreign == null) {
                foreign = as;
                foreignOwner = owner;
            }
        }
        if (lockedTag != null && !lockedTag.isAlive()) lockedTag = null;
        ArmorStand pick = locked != null ? locked
                : carried != null ? carried
                : cfg.slayerOnlyOwnBoss ? null : foreign;
        bossOwner = pick == null ? null : pick == locked ? lockedOwner : pick == carried ? carriedOwner : foreignOwner;
        isOwnBoss = false;
        return pick;
    }

    public static String ownerOf(ArmorStand bossTag) {
        String cached = ownerCache.get(bossTag.getId());
        if (cached != null) return cached;
        for (ArmorStand s : NametagMobs.namedStands(bossTag, 1.5)) {
            Matcher m = SPAWNED_BY_PATTERN.matcher(s.getName().getString());
            if (m.find()) {
                ownerCache.put(bossTag.getId(), m.group(1));
                return m.group(1);
            }
        }
        return null;
    }

    private static Entity cachedPair(ArmorStand tag) {
        Entity e = pairedCache.get(tag.getId());
        if (e == null) return null;
        if (e.isAlive()) return e;
        pairedCache.remove(tag.getId());
        return null;
    }

    private static net.minecraft.world.entity.EntityType<?> bossType(String name) {
        if (name.contains("Revenant") || name.contains("Atoned")) return net.minecraft.world.entity.EntityType.ZOMBIE;
        if (name.contains("Tarantula") || name.contains("Conjoined")) return net.minecraft.world.entity.EntityType.SPIDER;
        if (name.contains("Sven")) return net.minecraft.world.entity.EntityType.WOLF;
        if (name.contains("Voidgloom")) return net.minecraft.world.entity.EntityType.ENDERMAN;
        if (name.contains("Demonlord")) return net.minecraft.world.entity.EntityType.BLAZE;
        if (name.contains("Bloodfiend")) return net.minecraft.world.entity.EntityType.PLAYER;
        return null;
    }

    private static void findBossEntity(Minecraft mc, ArmorStand armorStand, String name) {
        Entity cached = cachedPair(armorStand);
        if (cached != null) {
            bossEntity = cached;
            return;
        }
        var type = bossType(name);
        Entity paired = type != null ? NametagMobs.mobForTag(type, armorStand) : NametagMobs.mobForTag(armorStand);
        if (paired != null) {
            bossEntity = paired;
            pairedCache.put(armorStand.getId(), paired);
            return;
        }
        AABB searchBox = armorStand.getBoundingBox().inflate(2, 3, 2);
        for (Entity entity : mc.level.getEntitiesOfClass(Entity.class, searchBox, e -> !(e instanceof ArmorStand))) {
            if (entity instanceof Mob || entity.getName().getString().contains("Demonlord")) {
                bossEntity = entity;
                pairedCache.put(armorStand.getId(), entity);
                break;
            }
        }
    }

    private static void findMinibossEntity(Minecraft mc, ArmorStand armorStand, String type) {
        Entity paired = cachedPair(armorStand);
        if (paired == null) paired = NametagMobs.mobForTag(armorStand);
        if (paired != null) {
            pairedCache.put(armorStand.getId(), paired);
            if (minibossEntities.add(paired)) minibossColors.put(paired, scanMinibossColor(mc, paired));
            return;
        }
        AABB searchBox = armorStand.getBoundingBox().inflate(2, 3, 2);
        for (Entity entity : mc.level.getEntitiesOfClass(Entity.class, searchBox, e -> !(e instanceof ArmorStand))) {
            if (entity instanceof Mob) {
                pairedCache.put(armorStand.getId(), entity);
                if (minibossEntities.add(entity)) minibossColors.put(entity, scanMinibossColor(mc, entity));
                break;
            }
        }
    }

    /** Cheap pre-check so the ESP render path can skip its entity sweep entirely. */
    public static boolean hasGlowTargets() {
        return bossEntity != null || !minibossEntities.isEmpty();
    }

    public static boolean shouldGlow(Entity entity) {
        if (com.teslamaps.features.LegitMode.blocksCheats()) return false;
        if (bossEntity != null && entity == bossEntity && TeslaMapsConfig.get().slayerBossESP) {
            return true;
        }
        if (minibossEntities.contains(entity) && TeslaMapsConfig.get().slayerMinibossESP) {
            return true;
        }
        return false;
    }

    public static int getGlowColor(Entity entity) {
        if (bossEntity != null && entity == bossEntity) {
            return getPhaseColor(currentPhaseType) & 0x00FFFFFF;
        }
        if (minibossEntities.contains(entity)) {
            Integer c = minibossColors.get(entity);
            return c != null ? c : 0xFF5555;
        }
        return 0xFFFFFF;
    }

    private static int scanMinibossColor(Minecraft mc, Entity entity) {
        AABB searchBox = entity.getBoundingBox().inflate(1, 3, 1);
        for (ArmorStand as : mc.level.getEntitiesOfClass(ArmorStand.class, searchBox, a -> true)) {
            String name = as.getName().getString();
            if (name.contains("Burningsoul")) {
                return 0xFF5555; // Red
            } else if (name.contains("Kindleheart")) {
                return 0xFFFFFF; // White
            } else if (containsAny(name, GENERIC_MINIBOSSES)) {
                return 0xFFAA00; // Gold
            }
        }
        return 0xFF5555; // Default red
    }

    public static boolean isSlayerMiniboss(String name) {
        return name.contains("Burningsoul Demon") || name.contains("Kindleheart Demon")
                || containsAny(name, GENERIC_MINIBOSSES);
    }

    private static int parseTier(String tierStr) {
        return switch (tierStr) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            default -> 4;
        };
    }

    private static double parseHP(String num, String suffix) {
        try {
            double value = Double.parseDouble(num);
            return switch (suffix.toUpperCase()) {
                case "K" -> value * 1_000;
                case "M" -> value * 1_000_000;
                case "B" -> value * 1_000_000_000;
                default -> value;
            };
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static int getPhaseColor(String phase) {
        if (phase == null) return 0xFFFFFFFF;
        return switch (phase) {
            case "SPIRIT" -> 0xFFFFFFFF;  // White
            case "ASHEN" -> 0xFFAAAAAA;   // Gray
            case "CRYSTAL" -> 0xFF55FFFF; // Light blue
            case "AURIC" -> 0xFFFFD700;   // Gold (more golden)
            default -> 0xFFFFFFFF;
        };
    }

    /** Boss tag armor stand currently tracked, null without one. */
    public static ArmorStand bossTag() { return chosenTag != null && chosenTag.isAlive() ? chosenTag : null; }
    public static double currentHP() { return currentHP; }
    public static int bossTier() { return bossTier; }
    public static String bossName() { return currentBossName; }
    public static boolean ownBoss() { return isOwnBoss; }
    public static String bossOwner() { return bossOwner; }

    public static String getCurrentPhaseType() {
        return currentPhaseType;
    }

    public static Entity getBossEntity() {
        return bossEntity;
    }

    public static Set<Entity> getMinibossEntities() {
        return minibossEntities;
    }

    public static void renderWorld(com.mojang.blaze3d.vertex.PoseStack matrices, net.minecraft.world.phys.Vec3 cameraPos) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        boolean legit = com.teslamaps.features.LegitMode.blocksCheats();

        if (cfg.slayerMinibossTracer && !legit) {
            for (Entity e : minibossEntities) {
                if (e == null || !e.isAlive()) continue;
                com.teslamaps.render.ESPRenderer.drawTracerFromCamera(matrices,
                        com.teslamaps.render.ESPRenderer.lerpBox(e).getCenter(), 0xFFFF5555, cameraPos);
            }
        }

        if (legit) return;

        if (cfg.slayerMinibossESP) {
            for (Entity e : minibossEntities) {
                if (e == null || !e.isAlive()) continue;
                com.teslamaps.render.ESPRenderer.drawBoxOutline(matrices, com.teslamaps.render.ESPRenderer.lerpBox(e),
                        getGlowColor(e) | 0xFF000000, 2.0f, cameraPos, true);
            }
        }

        if (cfg.slayerBossESP && bossEntity != null && bossEntity.isAlive()) {
            com.teslamaps.render.ESPRenderer.drawBoxOutline(matrices, com.teslamaps.render.ESPRenderer.lerpBox(bossEntity),
                    getGlowColor(bossEntity) | 0xFF000000, 2.0f, cameraPos, true);
        }
    }

    public static void onSlayerComplete() {
        long killTime = System.currentTimeMillis();
        // "NICE! SLAYER BOSS SLAIN!" and "SLAYER QUEST COMPLETE!" can both come for one kill
        if (killTime - lastCompleteMs < 3000) return;
        lastCompleteMs = killTime;
        com.teslamaps.features.SkillTracker.onBossKill();

        // the scoreboard "Slay the boss!" line is our own quest, exact and range independent
        long scoreboardSpawn = SlayerQuest.spawnMs();
        if (scoreboardSpawn == 0 && (!questActive || questStartTime == 0)) {
            SlayerQuest.onQuestEnd();
            return;
        }

        if (scoreboardSpawn == 0 && TeslaMapsConfig.get().slayerOnlyOwnBoss && bossOwner != null && !isOwnBoss) {
            resetQuest();
            SlayerQuest.onQuestEnd();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        long start = scoreboardSpawn > 0 ? scoreboardSpawn : questStartTime;
        long fightDuration = killTime - start;
        long questStart = SlayerQuest.questStartMs();
        long spawn = questStart > 0 && scoreboardSpawn > questStart ? scoreboardSpawn - questStart
                : lastKillTime > 0 ? start - lastKillTime : -1;
        if (spawn > 0) spawnTimes.add(spawn);
        killTimes.add(fightDuration);
        lastKillTime = killTime;
        totalKills++;

        long ticks = scoreboardSpawn > 0 ? SlayerQuest.fightTicks() : -1;
        String pb = SlayerTimer.onKill(SlayerQuest.key(), label(), fightDuration, ticks, SlayerQuest.died());
        if (TeslaMapsConfig.get().slayerKillChat) sendKillMessage(mc, fightDuration, ticks, pb);

        resetQuest();
        SlayerQuest.onQuestEnd();
    }

    private static String label() {
        String boss = SlayerQuest.boss();
        if (boss == null) return "Slayer boss";
        String[] roman = {"", "I", "II", "III", "IV", "V"};
        int t = SlayerQuest.tier();
        return boss + (t > 0 && t < roman.length ? " " + roman[t] : "");
    }

    public static void onSlayerFailed() {
        resetQuest();
        SlayerQuest.onQuestEnd();
    }

    public static void resetSession() {
        resetQuest();
        killTimes.clear();
        spawnTimes.clear();
        totalKills = 0;
        lastKillTime = 0;
    }

    private static void clearBossVisuals() {
        currentBossName = null;
        currentHP = 0;
        maxHP = 0;
        phaseMaxHP = 0;
        bossTier = 0;
        hasShield = false;
        currentPhaseType = null;
        phaseCount = 0;
        bossEntity = null;
    }

    private static void resetQuest() {
        clearBossVisuals();
        questActive = false;
        questStartTime = 0;
        bossOwner = null;
        isOwnBoss = false;
        quaziiHP = 0;
        typhoeusHP = 0;
        quaziiDead = false;
        typhoeusDead = false;
    }

    private static void sendKillMessage(Minecraft mc, long fightDuration, long ticks, String pb) {
        if (mc.player == null) return;

        StringBuilder msg = new StringBuilder();
        msg.append(ChatFormatting.GOLD).append("[TeslaMaps] ").append(ChatFormatting.GREEN).append("Boss killed! ");
        msg.append(ChatFormatting.GRAY).append("(#").append(totalKills).append(") ");

        msg.append(ChatFormatting.WHITE).append("Time: ").append(ChatFormatting.YELLOW).append(formatTime(fightDuration));
        if (TeslaMapsConfig.get().slayerKillTicks && ticks > 0) {
            msg.append(ChatFormatting.DARK_GRAY).append(" (").append(ticks).append(" ticks)");
        }
        msg.append(pb);

        if (spawnTimes.size() > 0) {
            long lastSpawnTime = spawnTimes.get(spawnTimes.size() - 1);
            msg.append(ChatFormatting.WHITE).append(" | Spawn: ").append(ChatFormatting.AQUA).append(formatTime(lastSpawnTime));
        }

        double bph = com.teslamaps.features.SkillTracker.bossesPerHour();
        if (bph > 0) {
            msg.append(ChatFormatting.WHITE).append(" | B/h: ").append(ChatFormatting.GOLD).append(String.format("%.1f", bph));
        }

        mc.player.sendSystemMessage(Component.literal(msg.toString()));

        if (killTimes.size() >= 2) {
            long avgKill = killTimes.stream().mapToLong(Long::longValue).sum() / killTimes.size();
            StringBuilder avgMsg = new StringBuilder();
            avgMsg.append(ChatFormatting.GOLD).append("[TeslaMaps] ").append(ChatFormatting.GRAY).append("Avg (").append(totalKills).append(" kills): ");
            avgMsg.append(ChatFormatting.WHITE).append("Kill: ").append(ChatFormatting.YELLOW).append(formatTime(avgKill));

            if (spawnTimes.size() >= 2) {
                long avgSpawn = spawnTimes.stream().mapToLong(Long::longValue).sum() / spawnTimes.size();
                avgMsg.append(ChatFormatting.WHITE).append(" | Spawn: ").append(ChatFormatting.AQUA).append(formatTime(avgSpawn));
            }

            mc.player.sendSystemMessage(Component.literal(avgMsg.toString()));
        }
    }

    private static String formatTime(long ms) {
        long seconds = ms / 1000;
        long millis = (ms % 1000) / 100; // tenths of a second

        if (seconds >= 60) {
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("%d:%02d.%d", minutes, seconds, millis);
        } else {
            return String.format("%d.%ds", seconds, millis);
        }
    }
}
