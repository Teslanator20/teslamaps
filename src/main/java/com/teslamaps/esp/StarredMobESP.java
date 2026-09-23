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
    private static final int FEL_COLOR = 0xFFFF00FF; // Magenta for fels
    private static final int SNIPER_COLOR = 0xFFFFAA00; // Orange-yellow for snipers
    private static final int SHADOW_ASSASSIN_COLOR = 0xFF5B2CB2; // Purple for shadow assassins
    private static final int DROPPED_ITEM_COLOR = 0xFFFFFF00; // Yellow for dropped items
    private static final int DUNGEON_BAT_COLOR = 0xFFFF8800; // Orange for dungeon bats
    private static final int LIVID_COLOR = 0xFF00FF00; // Green for the correct Livid

    private static int witherKeyColor() { return TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorESPWitherKey); }
    private static int bloodKeyColor() { return TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorESPBloodKey); }
    private static int witherDoorColor() { return TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorESPWitherDoor); }
    private static int bloodDoorColor() { return TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorESPBloodDoor); }

    private static final List<EntityHighlight> highlightedEntities = new ArrayList<>();
    // the subset that came from a custom entry, so a per-entry colour cannot be used to spot them
    private static final List<EntityHighlight> customHighlights = new ArrayList<>();

    private static final Map<Entity, Integer> glowingEntities = new WeakHashMap<>();
    private static final Map<Entity, Float> lineWidths = new WeakHashMap<>();

    private static final List<Vec3> witherKeyPositions = new ArrayList<>();
    private static final List<Vec3> bloodKeyPositions = new ArrayList<>();

    private static final List<Vec3> witherDoorPositions = new ArrayList<>();
    private static final List<Vec3> bloodDoorPositions = new ArrayList<>();

    private static final List<AABB> witherDoorBoxes = new ArrayList<>();
    private static final List<AABB> bloodDoorBoxes = new ArrayList<>();
    private static final List<AABB> fairyDoorBoxes = new ArrayList<>();

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
        TeslaMaps.debug("[KeyESP] Wither door opened, key state reset");
    }

    public static void onBloodDoorOpened() {
        bloodKeyPickedUp = false;
        TeslaMaps.debug("[KeyESP] Blood door opened, key state reset");
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
        TeslaMaps.debug("StarredMobESP initialized");
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

        if (TeslaMapsConfig.get().keyPickupTitle && TeslaMapsConfig.get().keySoundClasses.allowsLocal()) {
            mc.gui.setTitle(Component.literal("WITHER KEY").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
            mc.gui.setSubtitle(Component.literal("picked up!").withStyle(ChatFormatting.GRAY));
            mc.gui.setTimes(5, 30, 10); // fade in, stay, fade out
        }

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

        if (TeslaMapsConfig.get().keyPickupTitle && TeslaMapsConfig.get().keySoundClasses.allowsLocal()) {
            mc.gui.setTitle(Component.literal("BLOOD KEY").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            mc.gui.setSubtitle(Component.literal("picked up!").withStyle(ChatFormatting.RED));
            mc.gui.setTimes(5, 30, 10); // fade in, stay, fade out
        }

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

        if (TeslaMapsConfig.get().keyPickupTitle && TeslaMapsConfig.get().keySoundClasses.allowsLocal()) {
            mc.gui.setTitle(Component.literal("WITHER KEY").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
            mc.gui.setSubtitle(Component.literal("on ground!").withStyle(ChatFormatting.YELLOW));
            mc.gui.setTimes(5, 40, 10);
        }
    }

    private static void onBloodKeyOnGround() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (TeslaMapsConfig.get().keyOnGroundSoundEnabled && TeslaMapsConfig.get().keySoundClasses.allowsLocal()) {
            float volume = TeslaMapsConfig.get().keyOnGroundVolume;
            LoudSound.play(getOnGroundSound(), volume, 0.8f);
        }

        if (TeslaMapsConfig.get().keyPickupTitle && TeslaMapsConfig.get().keySoundClasses.allowsLocal()) {
            mc.gui.setTitle(Component.literal("BLOOD KEY").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            mc.gui.setSubtitle(Component.literal("on ground!").withStyle(ChatFormatting.YELLOW));
            mc.gui.setTimes(5, 40, 10);
        }
    }

    public static void tick() {
        tagText.clear();
        sniperTag.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || com.teslamaps.features.LegitMode.blocksCheats()) {
            highlightedEntities.clear();
            customHighlights.clear();
            glowingEntities.clear();
            return;
        }

        boolean inDungeon = DungeonManager.isInDungeon();
        boolean hasCustomMobs = TeslaMapsConfig.get().customESPMobs != null &&
                                !TeslaMapsConfig.get().customESPMobs.isEmpty();
        boolean droppedItemESP = TeslaMapsConfig.get().droppedItemESP;

        if (!inDungeon && !hasCustomMobs && !droppedItemESP) {
            highlightedEntities.clear();
            customHighlights.clear();
            glowingEntities.clear();
            return;
        }

        if (inDungeon && !TeslaMapsConfig.get().starredMobESP
                && !hasCustomMobs && !droppedItemESP
                && !TeslaMapsConfig.get().witherKeyESP && !TeslaMapsConfig.get().doorESP
                && !TeslaMapsConfig.get().lividFinder && !TeslaMapsConfig.get().dungeonBatESP) {
            highlightedEntities.clear();
            customHighlights.clear();
            glowingEntities.clear();
            return;
        }

        highlightedEntities.clear();
        customHighlights.clear();
        glowingEntities.clear();
        lineWidths.clear();
        witherKeyPositions.clear();
        bloodKeyPositions.clear();
        witherKeyEntities.clear();
        bloodKeyEntities.clear();
        witherDoorPositions.clear();
        bloodDoorPositions.clear();
        witherDoorBoxes.clear();
        bloodDoorBoxes.clear();
        fairyDoorBoxes.clear();
        dungeonBatPositions.clear();
        invisibleArmorStandBoxes.clear();

        if (inDungeon && TeslaMapsConfig.get().witherKeyESP) {
            for (Entity entity : com.teslamaps.utils.EntityCache.all()) {
                String entityName = entity.getName().getString();

                if (entityName.equals("Wither Key") || entityName.contains("Wither Key")) {
                    witherKeyPositions.add(new Vec3(entity.getX(), entity.getY(), entity.getZ()));
                    witherKeyEntities.add(entity);
                    glowingEntities.put(entity, witherKeyColor() & 0x00FFFFFF);
                }
                else if (entityName.equals("Blood Key") || entityName.contains("Blood Key")) {
                    bloodKeyPositions.add(new Vec3(entity.getX(), entity.getY(), entity.getZ()));
                    bloodKeyEntities.add(entity);
                    glowingEntities.put(entity, bloodKeyColor() & 0x00FFFFFF);
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
        if (inDungeon && TeslaMapsConfig.get().fairyDoorESP) {
            scanFairyDoors();
        } else {
            fairyDoorBoxes.clear();
        }

        collectCustomNameStands(mc);

        int checkedCount = 0;
        for (Entity entity : com.teslamaps.utils.EntityCache.all()) {
            checkedCount++;
            if (TeslaMapsConfig.get().lividFinder && isLivid(entity)) {
                continue;
            }

            String entityName = entity.getName().getString();

            if (entityName.equals("Bat") && inDungeon && TeslaMapsConfig.get().dungeonBatESP) {
                dungeonBatPositions.add(new Vec3(entity.getX(), entity.getY(), entity.getZ()));
            }

            if (shouldHighlight(entity, inDungeon)) {
                int color = getHighlightColor(entity);
                EntityHighlight eh = new EntityHighlight(entity, color);
                highlightedEntities.add(eh);
                glowingEntities.put(entity, color & 0x00FFFFFF);
                if (inDungeon) lineWidths.put(entity, lineWidthFor(entity, entityName));
                if (customEntryFor(entity) != null) customHighlights.add(eh);
            }
        }


        if (inDungeon && TeslaMapsConfig.get().invisibleArmorStandESP) {
            for (Entity entity : com.teslamaps.utils.EntityCache.armorStands()) {
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


        for (Entity entity : mc.level.getEntitiesOfClass(Entity.class, searchBox, e -> !(e instanceof Player) || e != mc.player)) {
            String className = entity.getClass().getSimpleName();
            String name = entity.getName().getString();
            String customName = entity.getCustomName() != null ? entity.getCustomName().getString() : "null";
            AABB box = entity.getBoundingBox();
            double width = box.maxX - box.minX;
            double height = box.maxY - box.minY;
            boolean invisible = entity.isInvisible();
            boolean isArmorStand = entity instanceof ArmorStand;

            TeslaMaps.debug("[EntityDebug] class={}, name='{}', customName='{}', size={}x{}, invisible={}, armorStand={}, pos={},{},{}",
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
                        checkDoorAt(mc, doorX, roomCenterZ, doorYLevels, true, gridX, gridZ); // X-aligned door
                    }
                }

                if (gridZ < 5) {
                    int roomCenterX = -200 + gridX * 32 + 16; // Center of room in X
                    int doorZ = -200 + (gridZ + 1) * 32 - 2; // Center of door gap

                    if (Math.abs(roomCenterX - px) <= scanRange && Math.abs(doorZ - pz) <= scanRange) {
                        checkDoorAt(mc, roomCenterX, doorZ, doorYLevels, false, gridX, gridZ); // Z-aligned door
                    }
                }
            }
        }

    }

    // the fairy-room doors stay closed until the wither door behind them is opened; the map already
    // shows a door pixel there, so: map says door + neighbor is fairy + gap still solid -> highlight
    private static void scanFairyDoors() {
        Minecraft mc = Minecraft.getInstance();
        fairyDoorBoxes.clear();
        if (mc.level == null) return;

        for (double[] p : com.teslamaps.scanner.MapScanner.getAllDoorPositions()) {
            int wx = (int) Math.floor(p[0]), wz = (int) Math.floor(p[1]);
            if (!isAdjacentToFairy(wx, wz)) continue;

            int solid = 0;
            for (int off = -1; off <= 1; off++) {
                if (!mc.level.getBlockState(new BlockPos(wx + off, 70, wz)).isAir()) solid++;
                if (!mc.level.getBlockState(new BlockPos(wx, 70, wz + off)).isAir()) solid++;
            }
            if (solid < 3) continue; // already open (or never closed)

            fairyDoorBoxes.add(new AABB(wx - 1.5, 69, wz - 1.5, wx + 2.5, 73, wz + 2.5));
        }
    }

    private static boolean isAdjacentToFairy(int wx, int wz) {
        int[][] probes = {{wx + 3, wz}, {wx - 3, wz}, {wx, wz + 3}, {wx, wz - 3}};
        for (int[] pr : probes) {
            int[] g = com.teslamaps.scanner.ComponentGrid.worldToGrid(pr[0], pr[1]);
            if (g == null) continue;
            com.teslamaps.map.DungeonRoom r = DungeonManager.getRoomAt(g[0], g[1]);
            if (r != null && r.getType() == com.teslamaps.map.RoomType.FAIRY) return true;
        }
        return false;
    }

    // a blood door can only sit next to the blood room - Chambers has the same red-terracotta
    // column inside the room and used to false-positive
    private static boolean couldBeBloodDoor(int gridX, int gridZ, boolean xAligned) {
        com.teslamaps.map.DungeonRoom a = DungeonManager.getRoomAt(gridX, gridZ);
        com.teslamaps.map.DungeonRoom b = xAligned
                ? DungeonManager.getRoomAt(gridX + 1, gridZ)
                : DungeonManager.getRoomAt(gridX, gridZ + 1);
        if (a == null || b == null) return true; // rooms not scanned yet -> keep old behavior
        return a.getType() == com.teslamaps.map.RoomType.BLOOD
                || b.getType() == com.teslamaps.map.RoomType.BLOOD;
    }

    private static void checkDoorAt(Minecraft mc, int x, int z, int[] yLevels, boolean xAligned, int gridX, int gridZ) {
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
                    if (!couldBeBloodDoor(gridX, gridZ, xAligned)) return;
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
        return shouldHighlight(entity, DungeonManager.isInDungeon());
    }

    public static boolean shouldHighlight(Entity entity, boolean inDungeon) {
        // a nametag stand overlaps our own box when standing on the mob, which briefly boxed the player
        if (entity == Minecraft.getInstance().player) return false;

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

        if (entityName.equals("Bat") && inDungeon && TeslaMapsConfig.get().dungeonBatESP) {
            return true;
        }

        // an armor stand is usually just a floating nametag, but a garden critter IS one: an
        // armour-wearing body with its label a block above it. So a stand is only skipped when
        // nothing but its own name matched - then it is the label, and the body gets the box.
        if (entity instanceof ArmorStand) {
            if (isCustomNameStand(entity)) return false;
            return matchedCustomStandEntry(entity) != null;
        }

        if (matchesCustomESP(entityName)) {
            return true;
        }

        if (entity.getCustomName() != null) {
            String customName = entity.getCustomName().getString();
            if (matchesCustomESP(customName)) {
                return true;
            }
        }

        // safari critters and most Hypixel props are an invisible or zero-size body under a
        // named stand, so this has to run before the invisibility bail
        if (matchesCustomESPArmorStand(entity)) {
            return true;
        }

        if (entity.isInvisible()) return false;

        if (!inDungeon || !TeslaMapsConfig.get().starredMobESP) {
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

    // the patterns were lowercased again for every entity on every tick
    private static List<String> mobSource;
    private static String[] mobLower = new String[0];
    private static List<String> tracerSource;
    private static String[] tracerLower = new String[0];

    // compare by content, not identity - the command mutates the same list in place
    private static String[] lowered(List<String> src, boolean tracers) {
        if (tracers) {
            if (!src.equals(tracerSource)) {
                tracerSource = new ArrayList<>(src);
                tracerLower = src.stream().map(String::toLowerCase).toArray(String[]::new);
            }
            return tracerLower;
        }
        if (!src.equals(mobSource)) {
            mobSource = new ArrayList<>(src);
            mobLower = src.stream().map(String::toLowerCase).toArray(String[]::new);
        }
        return mobLower;
    }

    private static boolean matchesCustomESP(String entityName) {
        return matchedCustomEntry(entityName) != null;
    }

    /** The config entry a name matched, in its original case, or null. */
    private static String matchedCustomEntry(String entityName) {
        List<String> customMobs = TeslaMapsConfig.get().customESPMobs;
        if (customMobs == null || customMobs.isEmpty()) return null;

        String lowerName = entityName.toLowerCase();
        String[] patterns = lowered(customMobs, false);
        for (int i = 0; i < patterns.length; i++) {
            // a blank entry (a fresh row in the list screen) would match every entity
            if (patterns[i].isEmpty()) continue;
            if (lowerName.contains(patterns[i])) {
                return i < customMobs.size() ? customMobs.get(i) : null;
            }
        }
        return null;
    }

    /** The entry that put this entity on the list, checking its own names and its nametag stand. */
    public static String customEntryFor(Entity entity) {
        String entry = matchedCustomEntry(entity.getName().getString());
        if (entry != null) return entry;
        if (entity.getCustomName() != null) {
            entry = matchedCustomEntry(entity.getCustomName().getString());
            if (entry != null) return entry;
        }
        return matchedCustomStandEntry(entity);
    }

    /** How many highlighted entities each custom entry currently accounts for. For the list screen. */
    public static Map<String, Integer> customEntryCounts() {
        Map<String, Integer> out = new HashMap<>();
        for (EntityHighlight eh : customHighlights) {
            if (!eh.entity().isAlive()) continue;
            String entry = customEntryFor(eh.entity());
            if (entry != null) out.merge(entry, 1, Integer::sum);
        }
        return out;
    }

    public static int customColor(String entry) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        String hex = entry == null ? null : config.customESPColors.get(entry.toLowerCase());
        return TeslaMapsConfig.parseColor(hex == null ? config.colorESPCustom : hex) | 0xFF000000;
    }

    private static boolean matchesCustomTracer(String entityName) {
        List<String> tracers = TeslaMapsConfig.get().customESPTracers;
        if (tracers == null || tracers.isEmpty()) return false;

        String lowerName = entityName.toLowerCase();
        for (String pattern : lowered(tracers, true)) {
            if (lowerName.contains(pattern)) return true;
        }
        return false;
    }

    // This used to run one getEntitiesOfClass query per entity per tick, which with
    // ~600 entities loaded cost up to 7.5ms in a single tick. The nametag stands are
    // collected once instead, then every entity is tested against that short list.
    private record NameStand(ArmorStand stand, String entry) {}

    private static final List<NameStand> customNameStands = new ArrayList<>();

    private static void collectCustomNameStands(Minecraft mc) {
        customNameStands.clear();
        List<String> customMobs = TeslaMapsConfig.get().customESPMobs;
        if (customMobs == null || customMobs.isEmpty() || mc.level == null) return;

        for (Entity e : com.teslamaps.utils.EntityCache.armorStands()) {
            if (!(e instanceof ArmorStand as)) continue;
            String entry = matchedCustomEntry(as.getName().getString());
            if (entry == null) {
                Component custom = as.getCustomName();
                if (custom != null) entry = matchedCustomEntry(custom.getString());
            }
            if (entry != null) customNameStands.add(new NameStand(as, entry));
        }
    }

    private static boolean matchesCustomESPArmorStand(Entity entity) {
        return matchedCustomStandEntry(entity) != null;
    }

    /** Whether this stand is one of the labels itself, rather than a body under one. */
    private static boolean isCustomNameStand(Entity entity) {
        for (NameStand ns : customNameStands) {
            if (ns.stand() == entity) return true;
        }
        return false;
    }

    private static String matchedCustomStandEntry(Entity entity) {
        if (customNameStands.isEmpty()) return null;

        AABB searchBox = entity.getBoundingBox().inflate(1, 4, 1);
        for (NameStand ns : customNameStands) {
            if (ns.stand() == entity) continue;   // a label must not put a box on itself
            if (searchBox.intersects(ns.stand().getBoundingBox())) return ns.entry();
        }
        return null;
    }

    private static boolean matchesCustomTracerArmorStand(Entity entity) {
        if (customNameStands.isEmpty()) return false;

        AABB searchBox = entity.getBoundingBox().inflate(1, 4, 1);
        for (NameStand ns : customNameStands) {
            ArmorStand stand = ns.stand();
            if (!searchBox.intersects(stand.getBoundingBox())) continue;
            if (matchesCustomTracer(stand.getName().getString())) return true;
            Component custom = stand.getCustomName();
            if (custom != null && matchesCustomTracer(custom.getString())) return true;
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
        Boolean cached = sniperTag.get(entity);
        if (cached != null) return cached;
        boolean r = scanSniperArmorStand(entity);
        sniperTag.put(entity, r);
        return r;
    }

    private static boolean scanSniperArmorStand(Entity entity) {
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


    public static boolean isStarred(Entity entity) {
        return tagText(entity).contains("✯");
    }

    // stand names in the nametag box, once per entity per tick
    private static final java.util.IdentityHashMap<Entity, String> tagText = new java.util.IdentityHashMap<>();
    private static final java.util.IdentityHashMap<Entity, Boolean> sniperTag = new java.util.IdentityHashMap<>();

    private static String tagText(Entity entity) {
        String t = tagText.get(entity);
        if (t != null) return t;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ArmorStand as : mc.level.getEntitiesOfClass(ArmorStand.class, entity.getBoundingBox().inflate(0, 2, 0), as -> true)) {
            sb.append(as.getName().getString()).append('\n');
        }
        t = sb.toString();
        tagText.put(entity, t);
        return t;
    }

    private static boolean isMiniboss(Entity entity, String name) {
        return (entity instanceof Player && (name.equals("Lost Adventurer") || name.equals("Diamond Guy")))
                || name.equals("Shadow Assassin");
    }

    private static float lineWidthFor(Entity entity, String name) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (isMiniboss(entity, name)) return c.espLineWidthMiniboss;
        if (name.equals("Dinnerbone")) return c.espLineWidthSpecial;
        if (c.espLineWidthSpecial != c.espLineWidthNormal && nametagContains(entity, "Withermancer")) return c.espLineWidthSpecial;
        return c.espLineWidthNormal;
    }

    private static boolean nametagContains(Entity entity, String text) {
        return tagText(entity).contains(text);
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
            if (config.espStarSpecialColors && isStarred(entity)) return TeslaMapsConfig.parseColor(config.colorESPStarredFel);
            return TeslaMapsConfig.parseColor(config.colorESPFel);
        }

        if (hasSniperArmorStand(entity)) {
            return TeslaMapsConfig.parseColor(config.colorESPSniper);
        }

        if (name.equals("Bat") && DungeonManager.isInDungeon()) {
            return TeslaMapsConfig.parseColor(config.colorESPBat);
        }

        if (name.equals("Shadow Assassin") && entity.isInvisible()) {
            return TeslaMapsConfig.parseColor(config.colorESPShadowAssassin);
        }

        String customEntry = customEntryFor(entity);
        if (customEntry != null) {
            return customColor(customEntry);
        }

        if (entity instanceof Player) {
            if (name.equals("Lost Adventurer")) return TeslaMapsConfig.parseColor(config.colorESPLostAdventurer);
            if (name.equals("Shadow Assassin")) return TeslaMapsConfig.parseColor(config.colorESPShadowAssassin);
            if (name.equals("Diamond Guy")) return TeslaMapsConfig.parseColor(config.colorESPDiamondGuy);
        }
        if (config.espStarSpecialColors && nametagContains(entity, "Withermancer")) {
            return TeslaMapsConfig.parseColor(config.colorESPStarredWithermancer);
        }
        return TeslaMapsConfig.parseColor(config.colorESPStarred);
    }

    public record EntityHighlight(Entity entity, int color) {}

    // display entities report a zero-size box, which draws as nothing
    private static AABB visibleBox(Entity entity) {
        AABB b = ESPRenderer.lerpBox(entity);
        if (b.getXsize() < 0.1 || b.getYsize() < 0.1) return b.inflate(0.4, 0.4, 0.4);
        return b;
    }

    public static List<Vec3> getWitherKeyPositions() {
        return witherKeyPositions;
    }

    public static List<Vec3> getBloodKeyPositions() {
        return bloodKeyPositions;
    }

    public static void renderWorldElements(PoseStack matrices, MultiBufferSource provider,
                                           Vec3 cameraPos, Vec3 playerEyePos) {
        boolean inDungeon = DungeonManager.isInDungeon();
        Minecraft mc = Minecraft.getInstance();

        if (TeslaMapsConfig.get().filledESP && mc.level != null) {
            for (Map.Entry<Entity, Integer> entry : glowingEntities.entrySet()) {
                Entity entity = entry.getKey();
                if (!entity.isAlive()) continue;
                ESPRenderer.drawFilledBox(matrices, visibleBox(entity), entry.getValue(), cameraPos,
                        (MultiBufferSource.BufferSource) provider);
            }
        }

        if (TeslaMapsConfig.get().boxESP && mc.level != null) {
            for (Map.Entry<Entity, Integer> entry : glowingEntities.entrySet()) {
                Entity entity = entry.getKey();
                if (!entity.isAlive()) continue;
                ESPRenderer.drawBoxOutline(matrices, visibleBox(entity),
                        entry.getValue() | 0xFF000000, lineWidths.getOrDefault(entity, 2.0f), cameraPos, true);
            }
        }

        // Custom entries have to show something with Box/Filled ESP off too - glow never
        // renders on the invisible or zero-size bodies most named mobs have.
        if (!TeslaMapsConfig.get().boxESP && !TeslaMapsConfig.get().filledESP) {
            for (EntityHighlight eh : customHighlights) {
                Entity entity = eh.entity();
                if (!entity.isAlive()) continue;
                ESPRenderer.drawBoxOutline(matrices, visibleBox(entity),
                        eh.color() | 0xFF000000, 2.0f, cameraPos, true);
            }
        }

        // Per-entry tracers from `/tmap add <name> on`. Not dungeon-gated: custom
        // entries are used outside dungeons too.
        for (EntityHighlight eh : customHighlights) {
            Entity entity = eh.entity();
            if (!entity.isAlive()) continue;
            if (!matchesCustomTracer(entity.getName().getString())
                    && (entity.getCustomName() == null
                        || !matchesCustomTracer(entity.getCustomName().getString()))
                    && !matchesCustomTracerArmorStand(entity)) continue;
            ESPRenderer.drawTracerFromCamera(matrices, ESPRenderer.lerpBox(entity).getCenter(),
                    eh.color() | 0xFF000000, cameraPos);
        }

        if (!inDungeon) return;

        if (TeslaMapsConfig.get().starredTracerWhenFew) {
            List<EntityHighlight> starred = new ArrayList<>();
            for (EntityHighlight eh : highlightedEntities) {
                if (eh.entity().isAlive() && isStarred(eh.entity())) starred.add(eh);
            }
            if (!starred.isEmpty() && starred.size() <= TeslaMapsConfig.get().starredTracerThreshold) {
                for (EntityHighlight eh : starred) {
                    ESPRenderer.drawTracerFromCamera(matrices, ESPRenderer.lerpBox(eh.entity()).getCenter(),
                            eh.color() | 0xFF000000, cameraPos);
                }
            }
        }

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

            boolean route = DoorRoute.routeActive();
            int nearestIdx = -1;
            if (onlyNextDoor && !route && !allDoors.isEmpty()) {
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
                AABB box = allDoors.get(i);
                if (route) {
                    if (!DoorRoute.isAhead(box)) continue;
                } else if (onlyNextDoor && nearestIdx != -1 && i != nearestIdx) continue;

                boolean isWither = isWitherDoor.get(i);

                int boxColor, tracerColor;
                if (TeslaMapsConfig.get().doorColorBasedOnKey) {
                    boolean keyPickedUp = isWither ? witherKeyPickedUp : bloodKeyPickedUp;
                    if (keyPickedUp && TeslaMapsConfig.get().doorEspKeyAtCurrentRoom) keyPickedUp = DoorRoute.atCurrentRoom(box);
                    if (keyPickedUp) {
                        boxColor = 0xFF00FF00;
                        tracerColor = 0xFF55FF55;
                    } else {
                        boxColor = 0xFFFF0000;
                        tracerColor = 0xFFFF5555;
                    }
                } else {
                    boxColor = isWither ? witherDoorColor() : bloodDoorColor();
                    tracerColor = isWither ? TRACER_DOOR : 0xFFFF0000;
                }

                ESPRenderer.drawESPBox(matrices, box, boxColor, cameraPos);
                if (drawDoorTracers) {
                    Vec3 doorCenter = box.getCenter();
                    ESPRenderer.drawTracerFromCamera(matrices, doorCenter, tracerColor, cameraPos);
                }
            }
        }

        if (TeslaMapsConfig.get().fairyDoorESP) {
            for (AABB box : fairyDoorBoxes) {
                ESPRenderer.drawESPBox(matrices, box, 0xFFFF69B4, cameraPos); // fairy pink
            }
        }

        if (TeslaMapsConfig.get().witherKeyESP) {
            boolean drawKeyTracers = TeslaMapsConfig.get().keyTracers;

            for (Entity keyEntity : witherKeyEntities) {
                if (!keyEntity.isAlive()) continue;
                AABB keyBox = ESPRenderer.lerpBox(keyEntity);
                ESPRenderer.drawESPBox(matrices, keyBox, witherKeyColor(), cameraPos);
                if (drawKeyTracers) {
                    Vec3 keyCenter = keyBox.getCenter();
                    ESPRenderer.drawTracerFromCamera(matrices, keyCenter, TRACER_WITHER_KEY, cameraPos);
                }
            }

            for (Entity keyEntity : bloodKeyEntities) {
                if (!keyEntity.isAlive()) continue;
                AABB keyBox = ESPRenderer.lerpBox(keyEntity);
                ESPRenderer.drawESPBox(matrices, keyBox, bloodKeyColor(), cameraPos);
                if (drawKeyTracers) {
                    Vec3 keyCenter = keyBox.getCenter();
                    ESPRenderer.drawTracerFromCamera(matrices, keyCenter, TRACER_BLOOD_KEY, cameraPos);
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
                    witherKeyColor(), 0xFFFFFFFF, 0xFFFFFFFF); // white inner, white text
        }

        for (Vec3 keyPos : bloodKeyPositions) {
            renderKeyIndicator(context, mc, keyPos, centerX, centerY, screenWidth, screenHeight,
                    bloodKeyColor(), 0xFFFF4444, 0xFFFF4444); // red inner, red text
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

    // mixin asks per entity per frame; every source only changes in its tick
    private static final java.util.IdentityHashMap<Entity, Integer> glowCache = new java.util.IdentityHashMap<>();

    public static void clearGlowCache() {
        glowCache.clear();
    }

    private static int glowOf(Entity entity) {
        Integer c = glowCache.get(entity);
        if (c == null) {
            c = computeGlow(entity);
            glowCache.put(entity, c);
        }
        return c;
    }

    private static int computeGlow(Entity entity) {
        Integer own = glowingEntities.get(entity);
        if (own != null) return own;
        if (LividSolver.shouldGlow(entity)) return LividSolver.getGlowColor(entity);
        if (SlayerHUD.shouldGlow(entity)) return SlayerHUD.getGlowColor(entity);
        return -1;
    }

    public static boolean shouldGlow(Entity entity) {
        return glowOf(entity) != -1;
    }

    public static int getGlowColor(Entity entity) {
        int c = glowOf(entity);
        return c == -1 ? 0 : c;
    }

    public static boolean shouldBeInvisible(Entity entity) {
        return LividSolver.isWrongLivid(entity);
    }

}
