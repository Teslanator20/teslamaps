package com.teslamaps.esp;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.features.LividSolver;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.scanner.MapScanner;
import com.teslamaps.slayer.SlayerHUD;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * ESP for starred mobs in dungeons.
 * Draws hitboxes through walls for mobs with a star (✯) in their name tag.
 *
 * Note: Due to Fabric API changes in 1.21, this uses the debug renderer approach
 * which draws boxes during entity rendering.
 */
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

    // Livid tracking is now handled by LividSolver

    // Known pest/critter names in Hypixel Skyblock Garden
    private static final String[] PEST_NAMES = {
            "Cricket", "Mosquito", "Moth", "Fly", "Locust",
            "Rat", "Slug", "Earthworm", "Beetle", "Mite",
            "Firefly", "Butterfly"  // Garden critters
    };
    private static final List<EntityHighlight> highlightedEntities = new ArrayList<>();

    // WeakHashMap to track entities that should glow (automatically cleans up dead entities)
    private static final Map<Entity, Integer> glowingEntities = new WeakHashMap<>();

    // Track wither key and blood key positions for tracers
    private static final List<Vec3d> witherKeyPositions = new ArrayList<>();
    private static final List<Vec3d> bloodKeyPositions = new ArrayList<>();

    // Track door positions for Door ESP
    private static final List<Vec3d> witherDoorPositions = new ArrayList<>();
    private static final List<Vec3d> bloodDoorPositions = new ArrayList<>();

    // Store door boxes for 3D rendering
    private static final List<Box> witherDoorBoxes = new ArrayList<>();
    private static final List<Box> bloodDoorBoxes = new ArrayList<>();

    // Track pest positions for tracers (from armor stand names for longer range)
    private static final List<Vec3d> pestPositions = new ArrayList<>();

    // Track dungeon bat positions for tracers
    private static final List<Vec3d> dungeonBatPositions = new ArrayList<>();

    // Track invisible armor stand positions for ESP
    private static final List<Box> invisibleArmorStandBoxes = new ArrayList<>();
    private static final int INVISIBLE_ARMOR_STAND_COLOR = 0xFF00FFFF; // Cyan

    // Track key pickup state for door coloring
    private static boolean witherKeyPickedUp = false;
    private static boolean bloodKeyPickedUp = false;

    /**
     * Reset key pickup tracking when leaving dungeon.
     */
    public static void reset() {
        witherKeyPickedUp = false;
        bloodKeyPickedUp = false;
    }

    /**
     * Called when a wither door is opened - reset wither key state.
     */
    public static void onWitherDoorOpened() {
        witherKeyPickedUp = false;
        TeslaMaps.LOGGER.info("[KeyESP] Wither door opened, key state reset");
    }

    /**
     * Called when a blood door is opened - reset blood key state.
     */
    public static void onBloodDoorOpened() {
        bloodKeyPickedUp = false;
        TeslaMaps.LOGGER.info("[KeyESP] Blood door opened, key state reset");
    }

    // Store key entities for 3D box rendering
    private static final List<Entity> witherKeyEntities = new ArrayList<>();
    private static final List<Entity> bloodKeyEntities = new ArrayList<>();

    // Tracer colors
    private static final int TRACER_WITHER_KEY = 0xFF00FFFF; // Cyan
    private static final int TRACER_BLOOD_KEY = 0xFFFF4444; // Red
    private static final int TRACER_LIVID = 0xFF00FF00; // Green
    private static final int TRACER_DOOR = 0xFFFFAA00; // Orange

    // Track if we've already notified about keys on ground (to avoid spam)
    private static boolean witherKeyOnGroundNotified = false;
    private static boolean bloodKeyOnGroundNotified = false;
    private static long witherKeyPickupTime = 0;  // Cooldown after pickup
    private static long bloodKeyPickupTime = 0;   // Cooldown after pickup
    private static final long PICKUP_COOLDOWN_MS = 3000; // 3 second cooldown after pickup

    public static void init() {
        TeslaMaps.LOGGER.info("StarredMobESP initialized");
    }

    /**
     * Get the SoundEvent for key pickup based on config.
     */
    private static net.minecraft.sound.SoundEvent getPickupSound() {
        String sound = TeslaMapsConfig.get().keyPickupSound;
        return switch (sound) {
            case "BLAZE_DEATH" -> SoundEvents.ENTITY_BLAZE_DEATH;
            case "GHAST_SHOOT" -> SoundEvents.ENTITY_GHAST_SHOOT;
            case "WITHER_SPAWN" -> SoundEvents.ENTITY_WITHER_SPAWN;
            case "ENDER_DRAGON_GROWL" -> SoundEvents.ENTITY_ENDER_DRAGON_GROWL;
            case "NOTE_PLING" -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            default -> SoundEvents.ENTITY_PLAYER_LEVELUP; // LEVEL_UP
        };
    }

    /**
     * Get the SoundEvent for key on ground based on config.
     */
    private static net.minecraft.sound.SoundEvent getOnGroundSound() {
        String sound = TeslaMapsConfig.get().keyOnGroundSound;
        return switch (sound) {
            case "NOTE_PLING" -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            case "EXPERIENCE_ORB" -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            case "ANVIL_LAND" -> SoundEvents.BLOCK_ANVIL_LAND;
            default -> SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(); // NOTE_CHIME
        };
    }

    /**
     * Called when a wither key is picked up - plays sound and shows title.
     */
    public static void onWitherKeyPickup() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // Play configurable sound with configurable volume (can exceed 1.0!)
        if (TeslaMapsConfig.get().keyPickupSoundEnabled) {
            float volume = TeslaMapsConfig.get().keyPickupVolume;
            LoudSound.play(getPickupSound(), volume, 1.5f);
        }

        // Show title
        mc.inGameHud.setTitle(Text.literal("WITHER KEY").formatted(Formatting.DARK_GRAY, Formatting.BOLD));
        mc.inGameHud.setSubtitle(Text.literal("picked up!").formatted(Formatting.GRAY));
        mc.inGameHud.setTitleTicks(5, 30, 10); // fade in, stay, fade out

        // Reset ground notification and set cooldown
        witherKeyOnGroundNotified = false;
        witherKeyPickupTime = System.currentTimeMillis();
        witherKeyPickedUp = true;
    }

    /**
     * Called when a blood key is picked up - plays sound and shows title.
     */
    public static void onBloodKeyPickup() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // Play configurable sound with configurable volume (can exceed 1.0!)
        if (TeslaMapsConfig.get().keyPickupSoundEnabled) {
            float volume = TeslaMapsConfig.get().keyPickupVolume;
            LoudSound.play(getPickupSound(), volume, 1.2f);
        }

        // Show title
        mc.inGameHud.setTitle(Text.literal("BLOOD KEY").formatted(Formatting.DARK_RED, Formatting.BOLD));
        mc.inGameHud.setSubtitle(Text.literal("picked up!").formatted(Formatting.RED));
        mc.inGameHud.setTitleTicks(5, 30, 10); // fade in, stay, fade out

        // Reset ground notification and set cooldown
        bloodKeyOnGroundNotified = false;
        bloodKeyPickupTime = System.currentTimeMillis();
        bloodKeyPickedUp = true;
    }

    /**
     * Called when a wither key is found on ground (detected by ESP).
     */
    private static void onWitherKeyOnGround() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // Play configurable sound with configurable volume (can exceed 1.0!)
        if (TeslaMapsConfig.get().keyOnGroundSoundEnabled) {
            float volume = TeslaMapsConfig.get().keyOnGroundVolume;
            LoudSound.play(getOnGroundSound(), volume, 1.0f);
        }

        // Show title
        mc.inGameHud.setTitle(Text.literal("WITHER KEY").formatted(Formatting.DARK_GRAY, Formatting.BOLD));
        mc.inGameHud.setSubtitle(Text.literal("on ground!").formatted(Formatting.YELLOW));
        mc.inGameHud.setTitleTicks(5, 40, 10);
    }

    /**
     * Called when a blood key is found on ground (detected by ESP).
     */
    private static void onBloodKeyOnGround() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // Play configurable sound with configurable volume (can exceed 1.0!)
        if (TeslaMapsConfig.get().keyOnGroundSoundEnabled) {
            float volume = TeslaMapsConfig.get().keyOnGroundVolume;
            LoudSound.play(getOnGroundSound(), volume, 0.8f);
        }

        // Show title
        mc.inGameHud.setTitle(Text.literal("BLOOD KEY").formatted(Formatting.DARK_RED, Formatting.BOLD));
        mc.inGameHud.setSubtitle(Text.literal("on ground!").formatted(Formatting.YELLOW));
        mc.inGameHud.setTitleTicks(5, 40, 10);
    }

    /**
     * Called each tick to update the list of highlighted entities.
     */
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) {
            highlightedEntities.clear();
            glowingEntities.clear();
            return;
        }

        boolean inDungeon = DungeonManager.isInDungeon();
        boolean hasCustomMobs = TeslaMapsConfig.get().customESPMobs != null &&
                                !TeslaMapsConfig.get().customESPMobs.isEmpty();
        boolean droppedItemESP = TeslaMapsConfig.get().droppedItemESP;
        boolean pestESP = TeslaMapsConfig.get().pestESP && SkyblockUtils.isInGarden();

        // If not in dungeon and no custom mobs and no dropped item ESP and no pest ESP, nothing to do
        if (!inDungeon && !hasCustomMobs && !droppedItemESP && !pestESP) {
            highlightedEntities.clear();
            glowingEntities.clear();
            pestPositions.clear();
            return;
        }

        // If in dungeon but ESP disabled, nothing to do (custom ESP still works outside)
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

        // Scan for wither keys and blood keys by entity name 
        if (inDungeon && TeslaMapsConfig.get().witherKeyESP) {
            for (Entity entity : mc.world.getEntities()) {
                String entityName = entity.getName().getString();

                // Check for Wither Key by name
                if (entityName.equals("Wither Key") || entityName.contains("Wither Key")) {
                    witherKeyPositions.add(new Vec3d(entity.getX(), entity.getY(), entity.getZ()));
                    witherKeyEntities.add(entity);
                    glowingEntities.put(entity, WITHER_KEY_COLOR & 0x00FFFFFF);
                    if (System.currentTimeMillis() % 3000 < 50) {
                        TeslaMaps.LOGGER.info("[KeyESP] Found Wither Key: {} at {},{},{}",
                            entity.getClass().getSimpleName(), entity.getX(), entity.getY(), entity.getZ());
                    }
                }
                // Check for Blood Key by name
                else if (entityName.equals("Blood Key") || entityName.contains("Blood Key")) {
                    bloodKeyPositions.add(new Vec3d(entity.getX(), entity.getY(), entity.getZ()));
                    bloodKeyEntities.add(entity);
                    glowingEntities.put(entity, BLOOD_KEY_COLOR & 0x00FFFFFF);
                    if (System.currentTimeMillis() % 3000 < 50) {
                        TeslaMaps.LOGGER.info("[KeyESP] Found Blood Key: {} at {},{},{}",
                            entity.getClass().getSimpleName(), entity.getX(), entity.getY(), entity.getZ());
                    }
                }
            }

            // Check for new keys on ground and notify (with cooldown after pickup)
            long now = System.currentTimeMillis();
            if (!witherKeyPositions.isEmpty() && !witherKeyOnGroundNotified) {
                // Only notify if enough time has passed since last pickup
                if (now - witherKeyPickupTime > PICKUP_COOLDOWN_MS) {
                    witherKeyOnGroundNotified = true;
                    onWitherKeyOnGround();
                }
            } else if (witherKeyPositions.isEmpty()) {
                witherKeyOnGroundNotified = false;
            }

            if (!bloodKeyPositions.isEmpty() && !bloodKeyOnGroundNotified) {
                // Only notify if enough time has passed since last pickup
                if (now - bloodKeyPickupTime > PICKUP_COOLDOWN_MS) {
                    bloodKeyOnGroundNotified = true;
                    onBloodKeyOnGround();
                }
            } else if (bloodKeyPositions.isEmpty()) {
                bloodKeyOnGroundNotified = false;
            }
        }

        // Livid finder - now handled by LividSolver
        // Add correct Livid to glowing entities for consistency
        if (inDungeon && TeslaMapsConfig.get().lividFinder) {
            Entity correctLividEntity = LividSolver.getCorrectLivid();
            if (correctLividEntity != null && !LividSolver.hasBlindness()) {
                glowingEntities.put(correctLividEntity, LIVID_COLOR & 0x00FFFFFF);
            }
        }

        // Door ESP - scan for wither and blood doors
        if (inDungeon && TeslaMapsConfig.get().doorESP) {
            scanDoorPositions();
        }

        // Pest ESP debug logging
        if (System.currentTimeMillis() % 5000 < 50 && TeslaMapsConfig.get().pestESP && SkyblockUtils.isInGarden()) {
            TeslaMaps.LOGGER.info("[PestESP] pestESP enabled, in Garden, pestPositions will be tracked from invisible entities");
        }

        // Collect mobs to highlight
        int checkedCount = 0;
        for (Entity entity : mc.world.getEntities()) {
            checkedCount++;
            // Skip all Livids when Livid finder is active - LividSolver handles them
            if (TeslaMapsConfig.get().lividFinder && isLivid(entity)) {
                continue;
            }

            String entityName = entity.getName().getString();

            // Track dungeon bat positions for tracers (in dungeon)
            // Note: Dungeon bats are NOT invisible, they're just small
            if (entityName.equals("Bat") && inDungeon && TeslaMapsConfig.get().dungeonBatESP) {
                dungeonBatPositions.add(new Vec3d(entity.getX(), entity.getY(), entity.getZ()));
            }

            // Track pest positions from the actual invisible entity (not armor stand) for better range
            // Pests are invisible Silverfish or Bat entities in the Garden
            if (entity.isInvisible() && !inDungeon && TeslaMapsConfig.get().pestESP && SkyblockUtils.isInGarden()) {
                if (entityName.equals("Silverfish") || entityName.equals("Bat")) {
                    pestPositions.add(new Vec3d(entity.getX(), entity.getY(), entity.getZ()));
                }
            }

            if (shouldHighlight(entity, inDungeon)) {
                int color = getHighlightColor(entity);
                highlightedEntities.add(new EntityHighlight(entity, color));
                // Add to glowing map for mixin to use (convert ARGB to RGB for team color)
                glowingEntities.put(entity, color & 0x00FFFFFF);
            }
        }

        // Debug log every 5 seconds
        if (checkedCount > 0 && System.currentTimeMillis() % 5000 < 50) {
            TeslaMaps.LOGGER.info("[StarredMobESP] Checked {} entities, highlighted {}, inDungeon={}, customMobs={}",
                    checkedCount, highlightedEntities.size(), inDungeon, hasCustomMobs);
        }

        // Scan for invisible armor stands (skull decorations) in dungeon
        if (inDungeon && TeslaMapsConfig.get().invisibleArmorStandESP) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof ArmorStandEntity && entity.isInvisible()) {
                    ArmorStandEntity armorStand = (ArmorStandEntity) entity;
                    // Only highlight armor stands without passengers (decorations, not mob name tags)
                    if (!armorStand.hasPassengers()) {
                        invisibleArmorStandBoxes.add(armorStand.getBoundingBox());
                    }
                }
            }
        }

        // Debug: log all nearby entities (disabled - was spamming logs)
        // debugLogNearbyEntities(mc);
    }

    private static long lastFelDebugTime = 0;

    /**
     * Debug method to log nearby entities to help identify fels and pests.
     * Logs every 3 seconds.
     */
    private static void debugLogNearbyEntities(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        // Only log every 3 seconds
        long now = System.currentTimeMillis();
        if (now - lastFelDebugTime < 3000) return;
        lastFelDebugTime = now;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        Box searchBox = new Box(px - 15, py - 5, pz - 15,
                                px + 15, py + 5, pz + 15);

        TeslaMaps.LOGGER.info("[EntityDebug] === ALL Entities within 15 blocks ===");

        for (Entity entity : mc.world.getEntitiesByClass(Entity.class, searchBox, e -> !(e instanceof PlayerEntity) || e != mc.player)) {
            String className = entity.getClass().getSimpleName();
            String name = entity.getName().getString();
            String customName = entity.getCustomName() != null ? entity.getCustomName().getString() : "null";
            Box box = entity.getBoundingBox();
            double width = box.maxX - box.minX;
            double height = box.maxY - box.minY;
            boolean invisible = entity.isInvisible();
            boolean isArmorStand = entity instanceof ArmorStandEntity;

            // Log ALL entities with full details
            TeslaMaps.LOGGER.info("[EntityDebug] class={}, name='{}', customName='{}', size={}x{}, invisible={}, armorStand={}, pos={},{},{}",
                    className, name, customName, String.format("%.2f", width), String.format("%.2f", height), invisible, isArmorStand,
                    String.format("%.1f", entity.getX()), String.format("%.1f", entity.getY()), String.format("%.1f", entity.getZ()));
        }
    }

    /**
     * Check if an entity is a Livid.
     * Used to skip Livids in the regular ESP loop (LividSolver handles them separately).
     */
    private static boolean isLivid(Entity entity) {
        if (entity instanceof ArmorStandEntity) return false;
        String name = entity.getName().getString();
        return name.endsWith(" Livid") || name.equals("Livid");
    }

    /**
     * Scan for wither and blood doors by checking blocks directly in the world.
     * Wither doors = coal blocks, Blood doors = red terracotta.
     * Doors are located at specific positions between rooms (every 32 blocks).
     *
     * Door center positions are at the midpoint between rooms:
     * Room centers are at: -184, -152, -120, -88, -56, -24 (offset by 16 from grid lines)
     * Door positions between rooms: -168, -136, -104, -72, -40 (midpoints)
     */
    private static void scanDoorPositions() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        // Door Y levels to check (doors span multiple Y levels)
        int[] doorYLevels = {68, 69, 70, 71};

        // Scan range around player
        double px = mc.player.getX();
        double pz = mc.player.getZ();
        double scanRange = 80;

        // Room grid: rooms are 31 blocks, doors are 1 block gap
        // Grid starts at -200, cells are 32 blocks each
        // Door positions are at cell boundaries: -200, -168, -136, -104, -72, -40, -8
        // But actual door centers are in the 4-block gap between rooms

        // Scan for doors at grid boundaries
        for (int gridX = 0; gridX < 6; gridX++) {
            for (int gridZ = 0; gridZ < 6; gridZ++) {
                // Check door to the right (between gridX and gridX+1)
                if (gridX < 5) {
                    int doorX = -200 + (gridX + 1) * 32 - 2; // Center of door gap
                    int roomCenterZ = -200 + gridZ * 32 + 16; // Center of room in Z

                    if (Math.abs(doorX - px) <= scanRange && Math.abs(roomCenterZ - pz) <= scanRange) {
                        checkDoorAt(mc, doorX, roomCenterZ, doorYLevels, true); // X-aligned door
                    }
                }

                // Check door below (between gridZ and gridZ+1)
                if (gridZ < 5) {
                    int roomCenterX = -200 + gridX * 32 + 16; // Center of room in X
                    int doorZ = -200 + (gridZ + 1) * 32 - 2; // Center of door gap

                    if (Math.abs(roomCenterX - px) <= scanRange && Math.abs(doorZ - pz) <= scanRange) {
                        checkDoorAt(mc, roomCenterX, doorZ, doorYLevels, false); // Z-aligned door
                    }
                }
            }
        }

        // Debug log
        if (System.currentTimeMillis() % 5000 < 50) {
            if (!witherDoorBoxes.isEmpty() || !bloodDoorBoxes.isEmpty()) {
                TeslaMaps.LOGGER.info("[DoorESP] Found {} wither doors, {} blood doors",
                        witherDoorBoxes.size(), bloodDoorBoxes.size());
            }
        }
    }

    /**
     * Check for a door at a specific position.
     * @param xAligned true if door opens along X axis (wall runs along Z)
     */
    private static void checkDoorAt(MinecraftClient mc, int x, int z, int[] yLevels, boolean xAligned) {
        // Check multiple positions around the door center
        for (int dy : yLevels) {
            // Check a few positions around the expected door location
            for (int offset = -2; offset <= 2; offset++) {
                int checkX = xAligned ? x : x + offset;
                int checkZ = xAligned ? z + offset : z;

                BlockPos pos = new BlockPos(checkX, dy, checkZ);
                Block block = mc.world.getBlockState(pos).getBlock();

                if (block == Blocks.COAL_BLOCK) {
                    // Validate it's actually a door (has air nearby indicating passage)
                    if (isValidDoor(mc, pos, xAligned)) {
                        Box doorBox = findDoorBox(mc, pos, true);
                        if (doorBox != null && !containsBox(witherDoorBoxes, doorBox)) {
                            witherDoorBoxes.add(doorBox);
                            witherDoorPositions.add(doorBox.getCenter());
                        }
                        return; // Found door at this location
                    }
                } else if (block == Blocks.RED_TERRACOTTA) {
                    // Validate it's actually a door (has air nearby indicating passage)
                    if (isValidDoor(mc, pos, xAligned)) {
                        Box doorBox = findDoorBox(mc, pos, false);
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

    /**
     * Validate that a block is actually part of a door (not wall decoration).
     * Doors have air/passage on both sides perpendicular to the door.
     */
    private static boolean isValidDoor(MinecraftClient mc, BlockPos pos, boolean xAligned) {
        // Only check doors in dungeon area (negative coordinates)
        // Chambers and boss rooms are in positive coords and have decorative red terracotta
        if (pos.getX() > 0 || pos.getZ() > 0) {
            return false;
        }

        // Check for air blocks on both sides of the door (the passage)
        Block airCheck1, airCheck2;
        if (xAligned) {
            // Door runs along Z, check X direction for passage
            airCheck1 = mc.world.getBlockState(pos.add(4, 0, 0)).getBlock();
            airCheck2 = mc.world.getBlockState(pos.add(-4, 0, 0)).getBlock();
        } else {
            // Door runs along X, check Z direction for passage
            airCheck1 = mc.world.getBlockState(pos.add(0, 0, 4)).getBlock();
            airCheck2 = mc.world.getBlockState(pos.add(0, 0, -4)).getBlock();
        }

        // BOTH sides should have air (passage) - doors connect two rooms
        boolean hasPassage = airCheck1 == Blocks.AIR && airCheck2 == Blocks.AIR;
        if (!hasPassage) return false;

        // Check that there are multiple door blocks vertically (doors are at least 3 tall)
        int verticalCount = 0;
        Block targetBlock = mc.world.getBlockState(pos).getBlock();
        for (int dy = -2; dy <= 3; dy++) {
            Block check = mc.world.getBlockState(pos.add(0, dy, 0)).getBlock();
            if (check == targetBlock) {
                verticalCount++;
            }
        }

        // Valid door: has passage on both sides AND has at least 3 blocks vertically
        return verticalCount >= 3;
    }

    /**
     * Find the full bounding box of a door starting from one block.
     */
    private static Box findDoorBox(MinecraftClient mc, BlockPos start, boolean isWitherDoor) {
        Block targetBlock = isWitherDoor ? Blocks.COAL_BLOCK : Blocks.RED_TERRACOTTA;

        // Expand to find full door bounds
        int minX = start.getX(), maxX = start.getX();
        int minY = start.getY(), maxY = start.getY();
        int minZ = start.getZ(), maxZ = start.getZ();

        // Expand in each direction (limit to reasonable door size)
        for (int i = 0; i < 5; i++) {
            if (mc.world.getBlockState(new BlockPos(minX - 1, start.getY(), start.getZ())).getBlock() == targetBlock) minX--;
            if (mc.world.getBlockState(new BlockPos(maxX + 1, start.getY(), start.getZ())).getBlock() == targetBlock) maxX++;
            if (mc.world.getBlockState(new BlockPos(start.getX(), minY - 1, start.getZ())).getBlock() == targetBlock) minY--;
            if (mc.world.getBlockState(new BlockPos(start.getX(), maxY + 1, start.getZ())).getBlock() == targetBlock) maxY++;
            if (mc.world.getBlockState(new BlockPos(start.getX(), start.getY(), minZ - 1)).getBlock() == targetBlock) minZ--;
            if (mc.world.getBlockState(new BlockPos(start.getX(), start.getY(), maxZ + 1)).getBlock() == targetBlock) maxZ++;
        }

        // Make box slightly larger for visibility
        return new Box(minX - 0.5, minY, minZ - 0.5, maxX + 1.5, maxY + 3, maxZ + 1.5);
    }

    private static boolean containsBox(List<Box> boxes, Box newBox) {
        for (Box existing : boxes) {
            // Check if centers are close (within 2 blocks)
            if (existing.getCenter().squaredDistanceTo(newBox.getCenter()) < 4) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the list of entities that should be highlighted.
     */
    public static List<EntityHighlight> getHighlightedEntities() {
        return highlightedEntities;
    }

    /**
     * Check if an entity should be highlighted (legacy method for external calls).
     */
    public static boolean shouldHighlight(Entity entity) {
        return shouldHighlight(entity, DungeonManager.isInDungeon());
    }

    /**
     * Check if an entity should be highlighted.
     * @param entity The entity to check
     * @param inDungeon Whether we're currently in a dungeon
     */
    public static boolean shouldHighlight(Entity entity, boolean inDungeon) {
        // Dropped item ESP - works everywhere
        if (entity instanceof ItemEntity && TeslaMapsConfig.get().droppedItemESP) {
            return true;
        }

        String entityName = entity.getName().getString();

        // Special case: Fels (Dinnerbone entities) are invisible until you get close
        // Check them BEFORE the invisible filter, with range limit
        if (entityName.equals("Dinnerbone") && inDungeon && TeslaMapsConfig.get().felESP) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                double dist = entity.squaredDistanceTo(mc.player);
                int maxRange = TeslaMapsConfig.get().felESPRange;
                if (dist <= maxRange * maxRange) {
                    return true;
                }
            }
        }

        // Special case: Snipers - detected via armor stand name tags
        if (inDungeon && TeslaMapsConfig.get().sniperESP && hasSniperArmorStand(entity)) {
            return true;
        }

        // Special case: Shadow Assassins are invisible until they teleport
        // The entity itself is named "Shadow Assassin" and is invisible
        if (entityName.equals("Shadow Assassin") && entity.isInvisible() && inDungeon && TeslaMapsConfig.get().shadowAssassinESP) {
            return true;
        }

        // Special case: Pests/critters are invisible Silverfish or Bats (only in Garden)
        // Check them BEFORE the invisible filter - no name tag check needed for better range
        if (entity.isInvisible() && TeslaMapsConfig.get().pestESP && SkyblockUtils.isInGarden()) {
            // Silverfish = pests (Cricket, etc.), Bat = critters (Firefly, etc.)
            if (entityName.equals("Silverfish") || entityName.equals("Bat")) {
                return true;
            }
        }

        // Special case: Dungeon bats indicate secrets (they're NOT invisible, just small)
        if (entityName.equals("Bat") && inDungeon && TeslaMapsConfig.get().dungeonBatESP) {
            return true;
        }

        if (entity.isInvisible()) return false;
        if (entity instanceof ArmorStandEntity) return false;

        // Custom ESP works everywhere (not just in dungeons)
        // Check custom ESP list - direct name match
        if (matchesCustomESP(entityName)) {
            return true;
        }

        // Also check custom name (some mobs have custom display names)
        if (entity.getCustomName() != null) {
            String customName = entity.getCustomName().getString();
            if (matchesCustomESP(customName)) {
                return true;
            }
        }

        // Check custom ESP via armor stand name tags (Hypixel displays names this way)
        if (matchesCustomESPArmorStand(entity)) {
            return true;
        }

        // The following checks only apply inside dungeons
        if (!inDungeon) {
            return false;
        }

        // Fels are already checked at the top (before invisible filter)

        // Check for miniboss player entities (hardcoded names)
        if (entity instanceof PlayerEntity) {
            if (entityName.equals("Lost Adventurer") ||
                entityName.equals("Shadow Assassin") ||
                entityName.equals("Diamond Guy")) {
                return true;
            }
            // Also check if player-based mob is starred (e.g. Livid)
            return isStarred(entity);
        }

        // Check for starred mobs
        if (entity instanceof MobEntity) {
            return isStarred(entity);
        }

        return false;
    }

    /**
     * Check if entity name matches any custom ESP entry (case-insensitive, partial match).
     */
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

    /**
     * Check if any nearby armor stand (name tag) matches custom ESP list.
     * Hypixel displays mob names on armor stands above the mob.
     */
    private static boolean matchesCustomESPArmorStand(Entity entity) {
        List<String> customMobs = TeslaMapsConfig.get().customESPMobs;
        if (customMobs == null || customMobs.isEmpty()) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;

        // Look for armor stands near the entity (name tags) - expand search area
        Box searchBox = entity.getBoundingBox().expand(1, 4, 1);
        List<ArmorStandEntity> armorStands = mc.world.getEntitiesByClass(
                ArmorStandEntity.class, searchBox, as -> true);

        for (ArmorStandEntity armorStand : armorStands) {
            // Check both getName and getCustomName
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

    /**
     * Check if an entity has a Shadow Assassin armor stand nearby.
     */
    private static boolean hasShadowAssassinArmorStand(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;

        // Look for armor stands near the entity (name tags)
        Box searchBox = entity.getBoundingBox().expand(1, 4, 1);
        List<ArmorStandEntity> armorStands = mc.world.getEntitiesByClass(
                ArmorStandEntity.class, searchBox, as -> true);

        for (ArmorStandEntity armorStand : armorStands) {
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

    /**
     * Check if an entity has a Sniper armor stand nearby.
     * Snipers have name tags like "🦴 Sniper 100k❤"
     */
    private static boolean hasSniperArmorStand(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;

        // Look for armor stands near the entity (name tags)
        Box searchBox = entity.getBoundingBox().expand(1, 4, 1);
        List<ArmorStandEntity> armorStands = mc.world.getEntitiesByClass(
                ArmorStandEntity.class, searchBox, as -> true);

        for (ArmorStandEntity armorStand : armorStands) {
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

    /**
     * Check if an entity is a pest by looking for nearby armor stands with pest names.
     */
    private static boolean isPest(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;

        // Look for armor stands near the entity (name tags)
        Box searchBox = entity.getBoundingBox().expand(1, 3, 1);
        List<ArmorStandEntity> armorStands = mc.world.getEntitiesByClass(
                ArmorStandEntity.class, searchBox, as -> true);

        for (ArmorStandEntity armorStand : armorStands) {
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

    /**
     * Check if an entity is starred by looking for nearby armor stands with ✯.
     */
    public static boolean isStarred(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return false;

        // Look for armor stands near the entity
        Box searchBox = entity.getBoundingBox().expand(0, 2, 0);
        List<ArmorStandEntity> armorStands = mc.world.getEntitiesByClass(
                ArmorStandEntity.class, searchBox, as -> true);

        for (ArmorStandEntity armorStand : armorStands) {
            String name = armorStand.getName().getString();
            if (name.contains("✯")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the highlight color for an entity (uses config colors).
     */
    public static int getHighlightColor(Entity entity) {
        TeslaMapsConfig config = TeslaMapsConfig.get();

        // Dropped items get yellow
        if (entity instanceof ItemEntity) {
            return DROPPED_ITEM_COLOR;
        }

        String name = entity.getName().getString();

        // Fels get configured color
        if (name.equals("Dinnerbone")) {
            return TeslaMapsConfig.parseColor(config.colorESPFel);
        }

        // Snipers get configured color (light blue by default)
        if (hasSniperArmorStand(entity)) {
            return TeslaMapsConfig.parseColor(config.colorESPSniper);
        }

        // Dungeon bats get configured color (in dungeon)
        if (name.equals("Bat") && DungeonManager.isInDungeon()) {
            return TeslaMapsConfig.parseColor(config.colorESPBat);
        }

        // Pests/critters get green color (invisible Silverfish or Bat outside dungeon)
        if (entity.isInvisible() && (name.equals("Silverfish") || name.equals("Bat"))) {
            return PEST_COLOR;
        }

        // Shadow Assassins get configured color
        if (name.equals("Shadow Assassin") && entity.isInvisible()) {
            return TeslaMapsConfig.parseColor(config.colorESPShadowAssassin);
        }

        // Custom ESP mobs get cyan color
        if (matchesCustomESP(name)) {
            return CUSTOM_ESP_COLOR;
        }

        if (entity instanceof PlayerEntity) {
            if (name.equals("Lost Adventurer")) return 0xFFFEE15C; // Yellow
            if (name.equals("Shadow Assassin")) return TeslaMapsConfig.parseColor(config.colorESPShadowAssassin);
            if (name.equals("Diamond Guy")) return 0xFF57C2F7; // Light blue
        }
        return TeslaMapsConfig.parseColor(config.colorESPStarred);
    }

    /**
     * Record to hold entity and its highlight color.
     */
    public record EntityHighlight(Entity entity, int color) {}

    /**
     * Get wither key positions for tracer rendering.
     */
    public static List<Vec3d> getWitherKeyPositions() {
        return witherKeyPositions;
    }

    /**
     * Get blood key positions for tracer rendering.
     */
    public static List<Vec3d> getBloodKeyPositions() {
        return bloodKeyPositions;
    }

    /**
     * Render world elements like door boxes, key boxes, and tracers.
     * Called from WorldRenderEvents.
     * Uses ESPRenderer for immediate mode rendering through walls.
     */
    public static void renderWorldElements(MatrixStack matrices, VertexConsumerProvider provider,
                                           Vec3d cameraPos, Vec3d playerEyePos) {
        boolean inDungeon = DungeonManager.isInDungeon();
        boolean inGarden = SkyblockUtils.isInGarden();

        // Render pest tracers in garden
        if (inGarden && TeslaMapsConfig.get().pestESP && TeslaMapsConfig.get().pestTracers) {
            for (Vec3d pestPos : pestPositions) {
                ESPRenderer.drawTracerFromCamera(matrices, pestPos, PEST_COLOR, cameraPos);
            }
        }

        if (!inDungeon) return;

        // Render dungeon bat tracers
        if (TeslaMapsConfig.get().dungeonBatESP && TeslaMapsConfig.get().dungeonBatTracers) {
            for (Vec3d batPos : dungeonBatPositions) {
                ESPRenderer.drawTracerFromCamera(matrices, batPos, DUNGEON_BAT_COLOR, cameraPos);
            }
        }

        // Door ESP - boxes and tracers using ESPRenderer
        if (TeslaMapsConfig.get().doorESP) {
            boolean drawDoorTracers = TeslaMapsConfig.get().doorTracers;
            boolean onlyNextDoor = TeslaMapsConfig.get().onlyShowNextDoor;

            // Combine all doors and find nearest if needed
            List<Box> allDoors = new ArrayList<>();
            List<Boolean> isWitherDoor = new ArrayList<>();
            allDoors.addAll(witherDoorBoxes);
            for (int i = 0; i < witherDoorBoxes.size(); i++) isWitherDoor.add(true);
            allDoors.addAll(bloodDoorBoxes);
            for (int i = 0; i < bloodDoorBoxes.size(); i++) isWitherDoor.add(false);

            // Find nearest door if onlyNextDoor is enabled
            int nearestIdx = -1;
            if (onlyNextDoor && !allDoors.isEmpty()) {
                double nearestDist = Double.MAX_VALUE;
                for (int i = 0; i < allDoors.size(); i++) {
                    double dist = allDoors.get(i).getCenter().squaredDistanceTo(cameraPos);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearestIdx = i;
                    }
                }
            }

            // Render doors
            for (int i = 0; i < allDoors.size(); i++) {
                // Skip if not nearest door and onlyNextDoor is enabled
                if (onlyNextDoor && nearestIdx != -1 && i != nearestIdx) continue;

                Box box = allDoors.get(i);
                boolean isWither = isWitherDoor.get(i);

                // Door color based on key state (if enabled)
                int boxColor, tracerColor;
                if (TeslaMapsConfig.get().doorColorBasedOnKey) {
                    boolean keyPickedUp = isWither ? witherKeyPickedUp : bloodKeyPickedUp;
                    if (keyPickedUp) {
                        // Green if key picked up
                        boxColor = 0xFF00FF00;
                        tracerColor = 0xFF55FF55;
                    } else {
                        // Red if key not picked up
                        boxColor = 0xFFFF0000;
                        tracerColor = 0xFFFF5555;
                    }
                } else {
                    // Default colors
                    boxColor = isWither ? 0xFF333333 : 0xFFCC0000;
                    tracerColor = isWither ? TRACER_DOOR : 0xFFFF0000;
                }

                ESPRenderer.drawESPBox(matrices, box, boxColor, cameraPos);
                if (drawDoorTracers) {
                    Vec3d doorCenter = box.getCenter();
                    ESPRenderer.drawTracerFromCamera(matrices, doorCenter, tracerColor, cameraPos);
                }
            }
        }

        // Key ESP - boxes and tracers using ESPRenderer
        if (TeslaMapsConfig.get().witherKeyESP) {
            boolean drawKeyTracers = TeslaMapsConfig.get().keyTracers;

            // Wither keys - 3D box + tracer
            for (Entity keyEntity : witherKeyEntities) {
                if (!keyEntity.isAlive()) continue;
                // Use entity's bounding box directly
                Box keyBox = keyEntity.getBoundingBox();
                ESPRenderer.drawESPBox(matrices, keyBox, 0xFF000000, cameraPos);  // Black box
                if (drawKeyTracers) {
                    Vec3d keyCenter = keyBox.getCenter();
                    ESPRenderer.drawTracerFromCamera(matrices, keyCenter, TRACER_WITHER_KEY, cameraPos);
                }
            }

            // Blood keys - 3D box + tracer
            for (Entity keyEntity : bloodKeyEntities) {
                if (!keyEntity.isAlive()) continue;
                Box keyBox = keyEntity.getBoundingBox();
                ESPRenderer.drawESPBox(matrices, keyBox, 0xFFCC0000, cameraPos);  // Red box
                if (drawKeyTracers) {
                    Vec3d keyCenter = keyBox.getCenter();
                    ESPRenderer.drawTracerFromCamera(matrices, keyCenter, TRACER_BLOOD_KEY, cameraPos);
                }
            }
        }

        // Render boxes for all highlighted entities (if filled ESP is enabled)
        if (TeslaMapsConfig.get().filledESP) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;

            // Render boxes for all glowing entities
            for (Map.Entry<Entity, Integer> entry : glowingEntities.entrySet()) {
                Entity entity = entry.getKey();
                if (!entity.isAlive()) continue;

                int color = entry.getValue();
                Box entityBox = entity.getBoundingBox();
                ESPRenderer.drawFilledBox(matrices, entityBox, color, cameraPos,
                        (VertexConsumerProvider.Immediate) provider);
            }

            // Render boxes for slayer entities
            for (Entity entity : mc.world.getEntities()) {
                if (SlayerHUD.shouldGlow(entity) && entity.isAlive()) {
                    int color = SlayerHUD.getGlowColor(entity);
                    Box entityBox = entity.getBoundingBox();
                    ESPRenderer.drawFilledBox(matrices, entityBox, color, cameraPos,
                            (VertexConsumerProvider.Immediate) provider);
                }
            }
        }

        // Invisible armor stand ESP - render cyan boxes
        if (TeslaMapsConfig.get().invisibleArmorStandESP) {
            for (Box armorStandBox : invisibleArmorStandBoxes) {
                ESPRenderer.drawBoxOutline(matrices, armorStandBox, INVISIBLE_ARMOR_STAND_COLOR, 2.0f, cameraPos);
            }
        }

        // Livid ESP is now handled by LividSolver.renderWorld()
    }

    /**
     * Render 2D HUD indicators pointing to wither and blood keys.
     * Called from HudRenderCallback.
     */
    public static void renderWitherKeyIndicators(net.minecraft.client.gui.DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        if (!TeslaMapsConfig.get().witherKeyESP) return;
        if (witherKeyPositions.isEmpty() && bloodKeyPositions.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        // Render wither key indicators (black/white)
        for (Vec3d keyPos : witherKeyPositions) {
            renderKeyIndicator(context, mc, keyPos, centerX, centerY, screenWidth, screenHeight,
                    0xFF000000, 0xFFFFFFFF, 0xFFFFFFFF); // Black outer, white inner, white text
        }

        // Render blood key indicators (dark red)
        for (Vec3d keyPos : bloodKeyPositions) {
            renderKeyIndicator(context, mc, keyPos, centerX, centerY, screenWidth, screenHeight,
                    0xFFCC0000, 0xFFFF4444, 0xFFFF4444); // Dark red outer, red inner, red text
        }

        // Render livid indicator (green) if livid finder is enabled
        Entity correctLividEntity = LividSolver.getCorrectLivid();
        if (TeslaMapsConfig.get().lividFinder && correctLividEntity != null && correctLividEntity.isAlive() && !LividSolver.hasBlindness()) {
            Vec3d lividPos = new Vec3d(correctLividEntity.getX(), correctLividEntity.getY(), correctLividEntity.getZ());
            renderKeyIndicator(context, mc, lividPos, centerX, centerY, screenWidth, screenHeight,
                    0xFF00AA00, 0xFF00FF00, 0xFF00FF00); // Green
        }
    }

    /**
     * Helper to render a single key indicator.
     */
    private static void renderKeyIndicator(net.minecraft.client.gui.DrawContext context, MinecraftClient mc,
                                            Vec3d keyPos, int centerX, int centerY, int screenWidth, int screenHeight,
                                            int outerColor, int innerColor, int textColor) {
        // Calculate direction from player to key
        double dx = keyPos.x - mc.player.getX();
        double dz = keyPos.z - mc.player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        // Get angle to key relative to player's yaw
        double angleToKey = Math.atan2(dz, dx) * (180 / Math.PI) - 90;
        double playerYaw = mc.player.getYaw();
        double relativeAngle = Math.toRadians(angleToKey - playerYaw);

        // Calculate indicator position - close to crosshair
        int indicatorDist = 50;  // Fixed distance from crosshair
        int indicatorX = centerX + (int) (Math.sin(relativeAngle) * indicatorDist);
        int indicatorY = centerY - (int) (Math.cos(relativeAngle) * indicatorDist);

        // Draw indicator square with colors - BIGGER size
        int size = 8;  // Bigger indicator
        context.fill(indicatorX - size, indicatorY - size, indicatorX + size, indicatorY + size, outerColor);
        context.fill(indicatorX - size + 2, indicatorY - size + 2, indicatorX + size - 2, indicatorY + size - 2, innerColor);

        // Draw distance text - bigger
        String distText = String.format("%.0fm", distance);
        int textWidth = mc.textRenderer.getWidth(distText);
        context.drawText(mc.textRenderer, distText, indicatorX - textWidth / 2, indicatorY + size + 2, textColor, true);
    }

    /**
     * Check if an entity should have the glow effect (through walls).
     * Called from WorldRendererMixin.
     */
    public static boolean shouldGlow(Entity entity) {
        // Check regular ESP
        if (glowingEntities.containsKey(entity)) {
            return true;
        }
        // Check Livid solver
        if (LividSolver.shouldGlow(entity)) {
            return true;
        }
        // Check slayer ESP
        return SlayerHUD.shouldGlow(entity);
    }

    /**
     * Get the glow color for an entity.
     * Called from EntityMixin.
     * @return RGB color, or 0 if entity shouldn't glow
     */
    public static int getGlowColor(Entity entity) {
        // Check regular ESP first
        if (glowingEntities.containsKey(entity)) {
            return glowingEntities.get(entity);
        }
        // Check Livid solver
        if (LividSolver.shouldGlow(entity)) {
            return LividSolver.getGlowColor(entity);
        }
        // Check slayer ESP
        if (SlayerHUD.shouldGlow(entity)) {
            return SlayerHUD.getGlowColor(entity);
        }
        return 0;
    }

    /**
     * Check if an entity should be made invisible (wrong Livid).
     * Called from mixin.
     */
    public static boolean shouldBeInvisible(Entity entity) {
        return LividSolver.isWrongLivid(entity);
    }

}
