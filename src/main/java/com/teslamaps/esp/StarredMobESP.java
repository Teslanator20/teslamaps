/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.esp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.features.LividSolver;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.scanner.MapScanner;
import com.teslamaps.slayer.SlayerHUD;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class StarredMobESP {
    private static final int STARRED_COLOR = 0xFFF57738; // Orange
    private static final int CUSTOM_ESP_COLOR = 0xFF00FFFF; // Cyan
    private static final int FEL_COLOR = 0xFFFF00FF; // Magenta for fels
    private static final int SNIPER_COLOR = 0xFFFFAA00; // Orange-yellow for snipers
    private static final int PEST_COLOR = 0xFF00FF00; // Green for pests
    private static final int SHADOW_ASSASSIN_COLOR = 0xFF5B2CB2; // Purple for shadow assassins
    private static final int DROPPED_ITEM_COLOR = 0xFFFFFF00; // Yellow for dropped items
    private static final int DUNGEON_BAT_COLOR = 0xFFFF8800; // Orange for dungeon bats
    private static final int WITHER_KEY_COLOR = 0xFF000000; // Black for wither keys
    private static final int BLOOD_KEY_COLOR = 0xFFCC0000; // Red for blood keys
    private static final int LIVID_COLOR = 0xFF00FF00; // Green for the correct Livid

    private static final String[] PEST_NAMES = {
            "Cricket", "Mosquito", "Moth", "Fly", "Locust",
            "Rat", "Slug", "Earthworm", "Beetle", "Mite",
            "Firefly", "Butterfly"  // Garden critters
    };
    private static final List<EntityHighlight> highlightedEntities = new ArrayList<>();

    private static final Map<Entity, Integer> glowingEntities = new WeakHashMap<>();

    private static final List<Vec3> witherKeyPositions = new ArrayList<>();
    private static final List<Vec3> bloodKeyPositions = new ArrayList<>();

    private static final List<Vec3> witherDoorPositions = new ArrayList<>();
    private static final List<Vec3> bloodDoorPositions = new ArrayList<>();

    private static final List<AABB> witherDoorBoxes = new ArrayList<>();
    private static final List<AABB> bloodDoorBoxes = new ArrayList<>();

    private static final List<Vec3> pestPositions = new ArrayList<>();

    private static final List<Vec3> dungeonBatPositions = new ArrayList<>();

    private static final List<AABB> invisibleArmorStandBoxes = new ArrayList<>();
    private static final int INVISIBLE_ARMOR_STAND_COLOR = 0xFF00FFFF; // Cyan

    private static boolean witherKeyPickedUp = false;
    private static boolean bloodKeyPickedUp = false;

    public static void reset() {
        witherKeyPickedUp = false;
        bloodKeyPickedUp = false;
    }

    public static void onWitherDoorOpened() {
        witherKeyPickedUp = false;
        TeslaMaps.LOGGER.info("[KeyESP] Wither door opened, key state reset");
    }

    public static void onBloodDoorOpened() {
        bloodKeyPickedUp = false;
        TeslaMaps.LOGGER.info("[KeyESP] Blood door opened, key state reset");
    }

    private static final List<Entity> witherKeyEntities = new ArrayList<>();
    private static final List<Entity> bloodKeyEntities = new ArrayList<>();

    private static final int TRACER_WITHER_KEY = 0xFF00FFFF; // Cyan
    private static final int TRACER_BLOOD_KEY = 0xFFFF4444; // Red
    private static final int TRACER_LIVID = 0xFF00FF00; // Green
    private static final int TRACER_DOOR = 0xFFFFAA00; // Orange

    private static boolean witherKeyOnGroundNotified = false;
    private static boolean bloodKeyOnGroundNotified = false;
    private static long witherKeyPickupTime = 0;  // Cooldown after pickup
    private static long bloodKeyPickupTime = 0;   // Cooldown after pickup
    private static final long PICKUP_COOLDOWN_MS = 3000; // 3 second cooldown after pickup

    public static void init() {
        TeslaMaps.LOGGER.info("StarredMobESP initialized");
    }

    private static net.minecraft.sounds.SoundEvent getPickupSound() {
        String sound = TeslaMapsConfig.get().keyPickupSound;
        return switch (sound) {
            case "BLAZE_DEATH" -> SoundEvents.BLAZE_DEATH;
            case "GHAST_SHOOT" -> SoundEvents.GHAST_SHOOT;
            case "WITHER_SPAWN" -> SoundEvents.WITHER_SPAWN;
            case "ENDER_DRAGON_GROWL" -> SoundEvents.ENDER_DRAGON_GROWL;
            case "NOTE_PLING" -> SoundEvents.NOTE_BLOCK_PLING.value();
            default -> SoundEvents.PLAYER_LEVELUP; // LEVEL_UP
        };
    }

    private static net.minecraft.sounds.SoundEvent getOnGroundSound() {
        String sound = TeslaMapsConfig.get().keyOnGroundSound;
        return switch (sound) {
            case "NOTE_PLING" -> SoundEvents.NOTE_BLOCK_PLING.value();
            case "EXPERIENCE_ORB" -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case "ANVIL_LAND" -> SoundEvents.ANVIL_LAND;
            default -> SoundEvents.NOTE_BLOCK_CHIME.value(); // NOTE_CHIME
        };
    }

    public static void onWitherKeyPickup() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (TeslaMapsConfig.get().keyPickupSoundEnabled && TeslaMapsConfig.get().keySoundClasses.allowsLocal()) {
            float volume = TeslaMapsConfig.get().keyPickupVolume;
            LoudSound.play(getPickupSound(), volume, 1.5f);
        }

        mc.gui.setTitle(Component.literal("WITHER KEY").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
        mc.gui.setSubtitle(Component.literal("picked up!").withStyle(ChatFormatting.GRAY));
        mc.gui.setTimes(5, 30, 10); // fade in, stay, fade out

        witherKeyOnGroundNotified = false;
        witherKeyPickupTime = System.currentTimeMillis();
        witherKeyPickedUp = true;
    }

    public static void onBloodKeyPickup() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (TeslaMapsConfig.get().keyPickupSoundEnabled && TeslaMapsConfig.get().keySoundClasses.allowsLocal()) {
            float volume = TeslaMapsConfig.get().keyPickupVolume;
            LoudSound.play(getPickupSound(), volume, 1.2f);
        }

        mc.gui.setTitle(Component.literal("BLOOD KEY").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        mc.gui.setSubtitle(Component.literal("picked up!").withStyle(ChatFormatting.RED));
        mc.gui.setTimes(5, 30, 10); // fade in, stay, fade out

        bloodKeyOnGroundNotified = false;
        bloodKeyPickupTime = System.currentTimeMillis();
        bloodKeyPickedUp = true;
    }

    private static void onWitherKeyOnGround() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (TeslaMapsConfig.get().keyOnGroundSoundEnabled && TeslaMapsConfig.get().keySoundClasses.allowsLocal()) {
            float volume = TeslaMapsConfig.get().keyOnGroundVolume;
            LoudSound.play(getOnGroundSound(), volume, 1.0f);
        }

        mc.gui.setTitle(Component.literal("WITHER KEY").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
        mc.gui.setSubtitle(Component.literal("on ground!").withStyle(ChatFormatting.YELLOW));
        mc.gui.setTimes(5, 40, 10);
    }

    private static void onBloodKeyOnGround() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (TeslaMapsConfig.get().keyOnGroundSoundEnabled && TeslaMapsConfig.get().keySoundClasses.allowsLocal()) {
            float volume = TeslaMapsConfig.get().keyOnGroundVolume;
            LoudSound.play(getOnGroundSound(), volume, 0.8f);
        }

        mc.gui.setTitle(Component.literal("BLOOD KEY").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        mc.gui.setSubtitle(Component.literal("on ground!").withStyle(ChatFormatting.YELLOW));
        mc.gui.setTimes(5, 40, 10);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || com.teslamaps.features.LegitMode.blocksCheats()) {
            highlightedEntities.clear();
            glowingEntities.clear();
            return;
        }

        boolean inDungeon = DungeonManager.isInDungeon();
        boolean hasCustomMobs = TeslaMapsConfig.get().customESPMobs != null &&
                                !TeslaMapsConfig.get().customESPMobs.isEmpty();
        boolean droppedItemESP = TeslaMapsConfig.get().droppedItemESP;
        boolean pestESP = TeslaMapsConfig.get().pestESP && SkyblockUtils.isInGarden();

        if (!inDungeon && !hasCustomMobs && !droppedItemESP && !pestESP) {
            highlightedEntities.clear();
            glowingEntities.clear();
            pestPositions.clear();
            return;
        }

        if (inDungeon && !TeslaMapsConfig.get().starredMobESP) {
            highlightedEntities.clear();
            glowingEntities.clear();
            return;
        }

        highlightedEntities.clear();
        glowingEntities.clear();
        witherKeyPositions.clear();
        bloodKeyPositions.clear();
        witherKeyEntities.clear();
        bloodKeyEntities.clear();
        witherDoorPositions.clear();
        bloodDoorPositions.clear();
        witherDoorBoxes.clear();
        bloodDoorBoxes.clear();
        pestPositions.clear();
        dungeonBatPositions.clear();
        invisibleArmorStandBoxes.clear();

        if (inDungeon && TeslaMapsConfig.get().witherKeyESP) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                String entityName = entity.getName().getString();

                if (entityName.equals("Wither Key") || entityName.contains("Wither Key")) {
                    witherKeyPositions.add(new Vec3(entity.getX(), entity.getY(), entity.getZ()));
                    witherKeyEntities.add(entity);
                    glowingEntities.put(entity, WITHER_KEY_COLOR & 0x00FFFFFF);
                    if (System.currentTimeMillis() % 3000 < 50) {
                        TeslaMaps.LOGGER.info("[KeyESP] Found Wither Key: {} at {},{},{}",
                            entity.getClass().getSimpleName(), entity.getX(), entity.getY(), entity.getZ());
                    }
                }
                else if (entityName.equals("Blood Key") || entityName.contains("Blood Key")) {
                    bloodKeyPositions.add(new Vec3(entity.getX(), entity.getY(), entity.getZ()));
                    bloodKeyEntities.add(entity);
                    glowingEntities.put(entity, BLOOD_KEY_COLOR & 0x00FFFFFF);
                    if (System.currentTimeMillis() % 3000 < 50) {
                        TeslaMaps.LOGGER.info("[KeyESP] Found Blood Key: {} at {},{},{}",
                            entity.getClass().getSimpleName(), entity.getX(), entity.getY(), entity.getZ());
                    }
                }
            }

            long now = System.currentTimeMillis();
            if (!witherKeyPositions.isEmpty() && !witherKeyOnGroundNotified) {
                if (now - witherKeyPickupTime > PICKUP_COOLDOWN_MS) {
                    witherKeyOnGroundNotified = true;
                    onWitherKeyOnGround();
                }
            } else if (witherKeyPositions.isEmpty()) {
                witherKeyOnGroundNotified = false;
            }

            if (!bloodKeyPositions.isEmpty() && !bloodKeyOnGroundNotified) {
                if (now - bloodKeyPickupTime > PICKUP_COOLDOWN_MS) {
                    bloodKeyOnGroundNotified = true;
                    onBloodKeyOnGround();
                }
            } else if (bloodKeyPositions.isEmpty()) {
                bloodKeyOnGroundNotified = false;
            }
        }

        if (inDungeon && TeslaMapsConfig.get().lividFinder) {
            Entity correctLividEntity = LividSolver.getCorrectLivid();
            if (correctLividEntity != null && !LividSolver.hasBlindness()) {
                glowingEntities.put(correctLividEntity, LIVID_COLOR & 0x00FFFFFF);
            }
        }

        if (inDungeon && TeslaMapsConfig.get().doorESP && TeslaMapsConfig.get().doorEspClasses.allowsLocal()) {
            scanDoorPositions();
        }

        if (System.currentTimeMillis() % 5000 < 50 && pestESP) {
            TeslaMaps.LOGGER.info("[PestESP] pestESP enabled, in Garden, pestPositions will be tracked from invisible entities");
        }

        int checkedCount = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            checkedCount++;
            if (TeslaMapsConfig.get().lividFinder && isLivid(entity)) {
                continue;
            }

            String entityName = entity.getName().getString();

            if (entityName.equals("Bat") && inDungeon && TeslaMapsConfig.get().dungeonBatESP) {
                dungeonBatPositions.add(new Vec3(entity.getX(), entity.getY(), entity.getZ()));
            }

            if (entity.isInvisible() && !inDungeon && pestESP) {
                if (entityName.equals("Silverfish") || entityName.equals("Bat")) {
                    pestPositions.add(new Vec3(entity.getX(), entity.getY(), entity.getZ()));
                }
            }

            if (shouldHighlight(entity, inDungeon, pestESP)) {
                int color = getHighlightColor(entity);
                highlightedEntities.add(new EntityHighlight(entity, color));
                glowingEntities.put(entity, color & 0x00FFFFFF);
            }
        }


        if (inDungeon && TeslaMapsConfig.get().invisibleArmorStandESP) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof ArmorStand && entity.isInvisible()) {
                    ArmorStand armorStand = (ArmorStand) entity;
                    if (!armorStand.isVehicle()) {
                        invisibleArmorStandBoxes.add(armorStand.getBoundingBox());
                    }
                }
            }
        }

    }

    private static long lastFelDebugTime = 0;

    private static void debugLogNearbyEntities(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;

        long now = System.currentTimeMillis();
        if (now - lastFelDebugTime < 3000) return;
        lastFelDebugTime = now;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        AABB searchBox = new AABB(px - 15, py - 5, pz - 15,
                                px + 15, py + 5, pz + 15);

        TeslaMaps.LOGGER.info("[EntityDebug] === ALL Entities within 15 blocks ===");

        for (Entity entity : mc.level.getEntitiesOfClass(Entity.class, searchBox, e -> !(e instanceof Player) || e != mc.player)) {
            String className = entity.getClass().getSimpleName();
            String name = entity.getName().getString();
            String customName = entity.getCustomName() != null ? entity.getCustomName().getString() : "null";
            AABB box = entity.getBoundingBox();
            double width = box.maxX - box.minX;
            double height = box.maxY - box.minY;
            boolean invisible = entity.isInvisible();
            boolean isArmorStand = entity instanceof ArmorStand;

            TeslaMaps.LOGGER.info("[EntityDebug] class={}, name='{}', customName='{}', size={}x{}, invisible={}, armorStand={}, pos={},{},{}",
                    className, name, customName, String.format("%.2f", width), String.format("%.2f", height), invisible, isArmorStand,
                    String.format("%.1f", entity.getX()), String.format("%.1f", entity.getY()), String.format("%.1f", entity.getZ()));
        }
    }

    private static boolean isLivid(Entity entity) {
        if (entity instanceof ArmorStand) return false;
        String name = entity.getName().getString();
        return name.endsWith(" Livid") || name.equals("Livid");
    }

    private static void scanDoorPositions() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        int[] doorYLevels = {68, 69, 70, 71};

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        double scanRange = 80;

        for (int gridX = 0; gridX < 6; gridX++) {
            for (int gridZ = 0; gridZ < 6; gridZ++) {
                if (gridX < 5) {
                    int doorX = -200 + (gridX + 1) * 32 - 2; // Center of door gap
                    int roomCenterZ = -200 + gridZ * 32 + 16; // Center of room in Z

                    if (Math.abs(doorX - px) <= scanRange && Math.abs(roomCenterZ - pz) <= scanRange) {
                        checkDoorAt(mc, doorX, roomCenterZ, doorYLevels, true); // X-aligned door
                    }
                }

                if (gridZ < 5) {
                    int roomCenterX = -200 + gridX * 32 + 16; // Center of room in X
                    int doorZ = -200 + (gridZ + 1) * 32 - 2; // Center of door gap

                    if (Math.abs(roomCenterX - px) <= scanRange && Math.abs(doorZ - pz) <= scanRange) {
                        checkDoorAt(mc, roomCenterX, doorZ, doorYLevels, false); // Z-aligned door
                    }
                }
            }
        }

    }

    private static void checkDoorAt(Minecraft mc, int x, int z, int[] yLevels, boolean xAligned) {
        for (int dy : yLevels) {
            for (int offset = -2; offset <= 2; offset++) {
                int checkX = xAligned ? x : x + offset;
                int checkZ = xAligned ? z + offset : z;

                BlockPos pos = new BlockPos(checkX, dy, checkZ);
                Block block = mc.level.getBlockState(pos).getBlock();

                if (block == Blocks.COAL_BLOCK) {
                    if (isValidDoor(mc, pos, xAligned)) {
                        AABB doorBox = findDoorBox(mc, pos, true);
                        if (doorBox != null && !containsBox(witherDoorBoxes, doorBox)) {
                            witherDoorBoxes.add(doorBox);
                            witherDoorPositions.add(doorBox.getCenter());
                        }
                        return; // Found door at this location
                    }
                } else if (block == Blocks.RED_TERRACOTTA) {
                    if (isValidDoor(mc, pos, xAligned)) {
                        AABB doorBox = findDoorBox(mc, pos, false);
                        if (doorBox != null && !containsBox(bloodDoorBoxes, doorBox)) {
                            bloodDoorBoxes.add(doorBox);
                            bloodDoorPositions.add(doorBox.getCenter());
                        }
                        return; // Found door at this location
                    }
                }
            }
        }
    }

    private static boolean isValidDoor(Minecraft mc, BlockPos pos, boolean xAligned) {
        if (pos.getX() > 0 || pos.getZ() > 0) {
            return false;
        }

        Block airCheck1, airCheck2;
        if (xAligned) {
            airCheck1 = mc.level.getBlockState(pos.offset(4, 0, 0)).getBlock();
            airCheck2 = mc.level.getBlockState(pos.offset(-4, 0, 0)).getBlock();
        } else {
            airCheck1 = mc.level.getBlockState(pos.offset(0, 0, 4)).getBlock();
            airCheck2 = mc.level.getBlockState(pos.offset(0, 0, -4)).getBlock();
        }

        boolean hasPassage = airCheck1 == Blocks.AIR && airCheck2 == Blocks.AIR;
        if (!hasPassage) return false;

        int verticalCount = 0;
        Block targetBlock = mc.level.getBlockState(pos).getBlock();
        for (int dy = -2; dy <= 3; dy++) {
            Block check = mc.level.getBlockState(pos.offset(0, dy, 0)).getBlock();
            if (check == targetBlock) {
                verticalCount++;
            }
        }

        return verticalCount >= 3;
    }

    private static AABB findDoorBox(Minecraft mc, BlockPos start, boolean isWitherDoor) {
        Block targetBlock = isWitherDoor ? Blocks.COAL_BLOCK : Blocks.RED_TERRACOTTA;

        int minX = start.getX(), maxX = start.getX();
        int minY = start.getY(), maxY = start.getY();
        int minZ = start.getZ(), maxZ = start.getZ();

        for (int i = 0; i < 5; i++) {
            if (mc.level.getBlockState(new BlockPos(minX - 1, start.getY(), start.getZ())).getBlock() == targetBlock) minX--;
            if (mc.level.getBlockState(new BlockPos(maxX + 1, start.getY(), start.getZ())).getBlock() == targetBlock) maxX++;
            if (mc.level.getBlockState(new BlockPos(start.getX(), minY - 1, start.getZ())).getBlock() == targetBlock) minY--;
            if (mc.level.getBlockState(new BlockPos(start.getX(), maxY + 1, start.getZ())).getBlock() == targetBlock) maxY++;
            if (mc.level.getBlockState(new BlockPos(start.getX(), start.getY(), minZ - 1)).getBlock() == targetBlock) minZ--;
            if (mc.level.getBlockState(new BlockPos(start.getX(), start.getY(), maxZ + 1)).getBlock() == targetBlock) maxZ++;
        }

        return new AABB(minX - 0.5, minY, minZ - 0.5, maxX + 1.5, maxY + 3, maxZ + 1.5);
    }

    private static boolean containsBox(List<AABB> boxes, AABB newBox) {
        for (AABB existing : boxes) {
            if (existing.getCenter().distanceToSqr(newBox.getCenter()) < 4) {
                return true;
            }
        }
        return false;
    }

    public static List<EntityHighlight> getHighlightedEntities() {
        return highlightedEntities;
    }

    public static boolean shouldHighlight(Entity entity) {
        return shouldHighlight(entity, DungeonManager.isInDungeon(),
                TeslaMapsConfig.get().pestESP && SkyblockUtils.isInGarden());
    }

    public static boolean shouldHighlight(Entity entity, boolean inDungeon, boolean pestESP) {
        if (entity instanceof ItemEntity && TeslaMapsConfig.get().droppedItemESP) {
            return true;
        }

        String entityName = entity.getName().getString();

        if (entityName.equals("Dinnerbone") && inDungeon && TeslaMapsConfig.get().felESP) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                double dist = entity.distanceToSqr(mc.player);
                int maxRange = TeslaMapsConfig.get().felESPRange;
                if (dist <= maxRange * maxRange) {
                    return true;
                }
            }
        }

        if (inDungeon && TeslaMapsConfig.get().sniperESP && hasSniperArmorStand(entity)) {
            return true;
        }

        if (entityName.equals("Shadow Assassin") && entity.isInvisible() && inDungeon && TeslaMapsConfig.get().shadowAssassinESP) {
            return true;
        }

        if (entity.isInvisible() && pestESP) {
            if (entityName.equals("Silverfish") || entityName.equals("Bat")) {
                return true;
            }
        }

        if (entityName.equals("Bat") && inDungeon && TeslaMapsConfig.get().dungeonBatESP) {
            return true;
        }

        if (entity.isInvisible()) return false;
        if (entity instanceof ArmorStand) return false;

        if (matchesCustomESP(entityName)) {
            return true;
        }

        if (entity.getCustomName() != null) {
            String customName = entity.getCustomName().getString();
            if (matchesCustomESP(customName)) {
                return true;
            }
        }

        if (matchesCustomESPArmorStand(entity)) {
            return true;
        }

        if (!inDungeon) {
            return false;
        }

        if (entity instanceof Player) {
            if (entityName.equals("Lost Adventurer") ||
                entityName.equals("Shadow Assassin") ||
                entityName.equals("Diamond Guy")) {
                return true;
            }
            return isStarred(entity);
        }

        if (entity instanceof Mob) {
            return isStarred(entity);
        }

        return false;
    }

    private static boolean matchesCustomESP(String entityName) {
        List<String> customMobs = TeslaMapsConfig.get().customESPMobs;
        if (customMobs == null || customMobs.isEmpty()) return false;

        String lowerName = entityName.toLowerCase();
        for (String pattern : customMobs) {
            if (lowerName.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesCustomESPArmorStand(Entity entity) {
        List<String> customMobs = TeslaMapsConfig.get().customESPMobs;
        if (customMobs == null || customMobs.isEmpty()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        AABB searchBox = entity.getBoundingBox().inflate(1, 4, 1);
        List<ArmorStand> armorStands = mc.level.getEntitiesOfClass(
                ArmorStand.class, searchBox, as -> true);

        for (ArmorStand armorStand : armorStands) {
            String name = armorStand.getName().getString();
            if (matchesCustomESP(name)) {
                return true;
            }
            if (armorStand.getCustomName() != null) {
                String customName = armorStand.getCustomName().getString();
                if (matchesCustomESP(customName)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean hasShadowAssassinArmorStand(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        AABB searchBox = entity.getBoundingBox().inflate(1, 4, 1);
        List<ArmorStand> armorStands = mc.level.getEntitiesOfClass(
                ArmorStand.class, searchBox, as -> true);

        for (ArmorStand armorStand : armorStands) {
            String name = armorStand.getName().getString();
            if (name.contains("Shadow Assassin")) {
                return true;
            }
            if (armorStand.getCustomName() != null) {
                String customName = armorStand.getCustomName().getString();
                if (customName.contains("Shadow Assassin")) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean hasSniperArmorStand(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        AABB searchBox = entity.getBoundingBox().inflate(1, 4, 1);
        List<ArmorStand> armorStands = mc.level.getEntitiesOfClass(
                ArmorStand.class, searchBox, as -> true);

        for (ArmorStand armorStand : armorStands) {
            String name = armorStand.getName().getString();
            if (name.contains("Sniper")) {
                return true;
            }
            if (armorStand.getCustomName() != null) {
                String customName = armorStand.getCustomName().getString();
                if (customName.contains("Sniper")) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isPest(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        AABB searchBox = entity.getBoundingBox().inflate(1, 3, 1);
        List<ArmorStand> armorStands = mc.level.getEntitiesOfClass(
                ArmorStand.class, searchBox, as -> true);

        for (ArmorStand armorStand : armorStands) {
            String name = armorStand.getName().getString();
            for (String pestName : PEST_NAMES) {
                if (name.contains(pestName)) {
                    return true;
                }
            }
            if (armorStand.getCustomName() != null) {
                String customName = armorStand.getCustomName().getString();
                for (String pestName : PEST_NAMES) {
                    if (customName.contains(pestName)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static boolean isStarred(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;

        AABB searchBox = entity.getBoundingBox().inflate(0, 2, 0);
        List<ArmorStand> armorStands = mc.level.getEntitiesOfClass(
                ArmorStand.class, searchBox, as -> true);

        for (ArmorStand armorStand : armorStands) {
            String name = armorStand.getName().getString();
            if (name.contains("✯")) {
                return true;
            }
        }

        return false;
    }

    public static int getHighlightColor(Entity entity) {
        TeslaMapsConfig config = TeslaMapsConfig.get();

        if (entity instanceof ItemEntity item) {
            if (config.secretItemReadiness && DungeonManager.isInDungeon()) {
                int remaining = config.secretItemPickupTicks - item.getAge();
                if (remaining <= 0) return TeslaMapsConfig.parseColor(config.colorItemReady);
                if (remaining <= 11) return TeslaMapsConfig.parseColor(config.colorItemSoon);
                return TeslaMapsConfig.parseColor(config.colorItemNotReady);
            }
            return DROPPED_ITEM_COLOR;
        }

        String name = entity.getName().getString();

        if (name.equals("Dinnerbone")) {
            return TeslaMapsConfig.parseColor(config.colorESPFel);
        }

        if (hasSniperArmorStand(entity)) {
            return TeslaMapsConfig.parseColor(config.colorESPSniper);
        }

        if (name.equals("Bat") && DungeonManager.isInDungeon()) {
            return TeslaMapsConfig.parseColor(config.colorESPBat);
        }

        if (entity.isInvisible() && (name.equals("Silverfish") || name.equals("Bat"))) {
            return PEST_COLOR;
        }

        if (name.equals("Shadow Assassin") && entity.isInvisible()) {
            return TeslaMapsConfig.parseColor(config.colorESPShadowAssassin);
        }

        if (matchesCustomESP(name)) {
            return CUSTOM_ESP_COLOR;
        }

        if (entity instanceof Player) {
            if (name.equals("Lost Adventurer")) return 0xFFFEE15C; // Yellow
            if (name.equals("Shadow Assassin")) return TeslaMapsConfig.parseColor(config.colorESPShadowAssassin);
            if (name.equals("Diamond Guy")) return 0xFF57C2F7; // Light blue
        }
        return TeslaMapsConfig.parseColor(config.colorESPStarred);
    }

    public record EntityHighlight(Entity entity, int color) {}

    public static List<Vec3> getWitherKeyPositions() {
        return witherKeyPositions;
    }

    public static List<Vec3> getBloodKeyPositions() {
        return bloodKeyPositions;
    }

    public static void renderWorldElements(PoseStack matrices, MultiBufferSource provider,
                                           Vec3 cameraPos, Vec3 playerEyePos) {
        boolean inDungeon = DungeonManager.isInDungeon();
        boolean inGarden = SkyblockUtils.isInGarden();

        if (inGarden && TeslaMapsConfig.get().pestESP && TeslaMapsConfig.get().pestTracers) {
            for (Vec3 pestPos : pestPositions) {
                ESPRenderer.drawTracerFromCamera(matrices, pestPos, PEST_COLOR, cameraPos);
            }
        }

        if (!inDungeon) return;

        if (TeslaMapsConfig.get().dungeonBatESP && TeslaMapsConfig.get().dungeonBatTracers) {
            for (Vec3 batPos : dungeonBatPositions) {
                ESPRenderer.drawTracerFromCamera(matrices, batPos, DUNGEON_BAT_COLOR, cameraPos);
            }
        }

        if (TeslaMapsConfig.get().doorESP && TeslaMapsConfig.get().doorEspClasses.allowsLocal()) {
            boolean drawDoorTracers = TeslaMapsConfig.get().doorTracers;
            boolean onlyNextDoor = TeslaMapsConfig.get().onlyShowNextDoor;

            List<AABB> allDoors = new ArrayList<>();
            List<Boolean> isWitherDoor = new ArrayList<>();
            allDoors.addAll(witherDoorBoxes);
            for (int i = 0; i < witherDoorBoxes.size(); i++) isWitherDoor.add(true);
            allDoors.addAll(bloodDoorBoxes);
            for (int i = 0; i < bloodDoorBoxes.size(); i++) isWitherDoor.add(false);

            int nearestIdx = -1;
            if (onlyNextDoor && !allDoors.isEmpty()) {
                double nearestDist = Double.MAX_VALUE;
                for (int i = 0; i < allDoors.size(); i++) {
                    double dist = allDoors.get(i).getCenter().distanceToSqr(cameraPos);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearestIdx = i;
                    }
                }
            }

            for (int i = 0; i < allDoors.size(); i++) {
                if (onlyNextDoor && nearestIdx != -1 && i != nearestIdx) continue;

                AABB box = allDoors.get(i);
                boolean isWither = isWitherDoor.get(i);

                int boxColor, tracerColor;
                if (TeslaMapsConfig.get().doorColorBasedOnKey) {
                    boolean keyPickedUp = isWither ? witherKeyPickedUp : bloodKeyPickedUp;
                    if (keyPickedUp) {
                        boxColor = 0xFF00FF00;
                        tracerColor = 0xFF55FF55;
                    } else {
                        boxColor = 0xFFFF0000;
                        tracerColor = 0xFFFF5555;
                    }
                } else {
                    boxColor = isWither ? 0xFF333333 : 0xFFCC0000;
                    tracerColor = isWither ? TRACER_DOOR : 0xFFFF0000;
                }

                ESPRenderer.drawESPBox(matrices, box, boxColor, cameraPos);
                if (drawDoorTracers) {
                    Vec3 doorCenter = box.getCenter();
                    ESPRenderer.drawTracerFromCamera(matrices, doorCenter, tracerColor, cameraPos);
                }
            }
        }

        if (TeslaMapsConfig.get().witherKeyESP) {
            boolean drawKeyTracers = TeslaMapsConfig.get().keyTracers;

            for (Entity keyEntity : witherKeyEntities) {
                if (!keyEntity.isAlive()) continue;
                AABB keyBox = keyEntity.getBoundingBox();
                ESPRenderer.drawESPBox(matrices, keyBox, 0xFF000000, cameraPos);  // Black box
                if (drawKeyTracers) {
                    Vec3 keyCenter = keyBox.getCenter();
                    ESPRenderer.drawTracerFromCamera(matrices, keyCenter, TRACER_WITHER_KEY, cameraPos);
                }
            }

            for (Entity keyEntity : bloodKeyEntities) {
                if (!keyEntity.isAlive()) continue;
                AABB keyBox = keyEntity.getBoundingBox();
                ESPRenderer.drawESPBox(matrices, keyBox, 0xFFCC0000, cameraPos);  // Red box
                if (drawKeyTracers) {
                    Vec3 keyCenter = keyBox.getCenter();
                    ESPRenderer.drawTracerFromCamera(matrices, keyCenter, TRACER_BLOOD_KEY, cameraPos);
                }
            }
        }

        if (TeslaMapsConfig.get().filledESP) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            for (Map.Entry<Entity, Integer> entry : glowingEntities.entrySet()) {
                Entity entity = entry.getKey();
                if (!entity.isAlive()) continue;

                int color = entry.getValue();
                AABB entityBox = entity.getBoundingBox();
                ESPRenderer.drawFilledBox(matrices, entityBox, color, cameraPos,
                        (MultiBufferSource.BufferSource) provider);
            }

            for (Entity entity : mc.level.entitiesForRendering()) {
                if (SlayerHUD.shouldGlow(entity) && entity.isAlive()) {
                    int color = SlayerHUD.getGlowColor(entity);
                    AABB entityBox = entity.getBoundingBox();
                    ESPRenderer.drawFilledBox(matrices, entityBox, color, cameraPos,
                            (MultiBufferSource.BufferSource) provider);
                }
            }
        }

        if (TeslaMapsConfig.get().boxESP) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                for (Map.Entry<Entity, Integer> entry : glowingEntities.entrySet()) {
                    Entity entity = entry.getKey();
                    if (!entity.isAlive()) continue;
                    ESPRenderer.drawBoxOutline(matrices, entity.getBoundingBox(),
                            entry.getValue() | 0xFF000000, 2.0f, cameraPos, true);
                }

                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (SlayerHUD.shouldGlow(entity) && entity.isAlive()) {
                        ESPRenderer.drawBoxOutline(matrices, entity.getBoundingBox(),
                                SlayerHUD.getGlowColor(entity) | 0xFF000000, 2.0f, cameraPos, true);
                    }
                }
            }
        }

        if (TeslaMapsConfig.get().starredTracerWhenFew) {
            List<EntityHighlight> starred = new ArrayList<>();
            for (EntityHighlight eh : highlightedEntities) {
                if (eh.entity().isAlive() && isStarred(eh.entity())) starred.add(eh);
            }
            if (!starred.isEmpty() && starred.size() <= TeslaMapsConfig.get().starredTracerThreshold) {
                for (EntityHighlight eh : starred) {
                    ESPRenderer.drawTracerFromCamera(matrices, eh.entity().getBoundingBox().getCenter(),
                            eh.color() | 0xFF000000, cameraPos);
                }
            }
        }

        if (TeslaMapsConfig.get().invisibleArmorStandESP) {
            for (AABB armorStandBox : invisibleArmorStandBoxes) {
                ESPRenderer.drawBoxOutline(matrices, armorStandBox, INVISIBLE_ARMOR_STAND_COLOR, 2.0f, cameraPos);
            }
        }

    }

    public static void renderWitherKeyIndicators(net.minecraft.client.gui.GuiGraphicsExtractor context, net.minecraft.client.DeltaTracker tickCounter) {
        if (!TeslaMapsConfig.get().witherKeyESP) return;
        if (witherKeyPositions.isEmpty() && bloodKeyPositions.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        for (Vec3 keyPos : witherKeyPositions) {
            renderKeyIndicator(context, mc, keyPos, centerX, centerY, screenWidth, screenHeight,
                    0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF); // Black outer, white inner, white text
        }

        for (Vec3 keyPos : bloodKeyPositions) {
            renderKeyIndicator(context, mc, keyPos, centerX, centerY, screenWidth, screenHeight,
                    0xFFCC0000, 0xFFFF4444, 0xFFFF4444); // Dark red outer, red inner, red text
        }

        Entity correctLividEntity = LividSolver.getCorrectLivid();
        if (TeslaMapsConfig.get().lividFinder && correctLividEntity != null && correctLividEntity.isAlive() && !LividSolver.hasBlindness()) {
            Vec3 lividPos = new Vec3(correctLividEntity.getX(), correctLividEntity.getY(), correctLividEntity.getZ());
            renderKeyIndicator(context, mc, lividPos, centerX, centerY, screenWidth, screenHeight,
                    0xFF00AA00, 0xFF00FF00, 0xFF00FF00); // Green
        }
    }

    private static void renderKeyIndicator(net.minecraft.client.gui.GuiGraphicsExtractor context, Minecraft mc,
                                            Vec3 keyPos, int centerX, int centerY, int screenWidth, int screenHeight,
                                            int outerColor, int innerColor, int textColor) {
        double dx = keyPos.x - mc.player.getX();
        double dz = keyPos.z - mc.player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        double angleToKey = Math.atan2(dz, dx) * (180 / Math.PI) - 90;
        double playerYaw = mc.player.getYRot();
        double relativeAngle = Math.toRadians(angleToKey - playerYaw);

        int indicatorDist = 50;  // Fixed distance from crosshair
        int indicatorX = centerX + (int) (Math.sin(relativeAngle) * indicatorDist);
        int indicatorY = centerY - (int) (Math.cos(relativeAngle) * indicatorDist);

        int size = 8;  // Bigger indicator
        context.fill(indicatorX - size, indicatorY - size, indicatorX + size, indicatorY + size, outerColor);
        context.fill(indicatorX - size + 2, indicatorY - size + 2, indicatorX + size - 2, indicatorY + size - 2, innerColor);

        String distText = String.format("%.0fm", distance);
        int textWidth = mc.font.width(distText);
        context.text(mc.font, distText, indicatorX - textWidth / 2, indicatorY + size + 2, textColor, true);
    }

    public static boolean shouldGlow(Entity entity) {
        if (glowingEntities.containsKey(entity)) {
            return true;
        }
        if (LividSolver.shouldGlow(entity)) {
            return true;
        }
        return SlayerHUD.shouldGlow(entity);
    }

    public static int getGlowColor(Entity entity) {
        if (glowingEntities.containsKey(entity)) {
            return glowingEntities.get(entity);
        }
        if (LividSolver.shouldGlow(entity)) {
            return LividSolver.getGlowColor(entity);
        }
        if (SlayerHUD.shouldGlow(entity)) {
            return SlayerHUD.getGlowColor(entity);
        }
        return 0;
    }

    public static boolean shouldBeInvisible(Entity entity) {
        return LividSolver.isWrongLivid(entity);
    }

}
