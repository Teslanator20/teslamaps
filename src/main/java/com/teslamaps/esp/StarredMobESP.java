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
import com.teslamaps.render.MultiBufferSource;
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

    private static boolean witherKeyPickedUp = false;
    private static boolean bloodKeyPickedUp = false;

    public static void reset() {
        fairyVisited = false;
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

    public static void onWitherKeyPickup(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (TeslaMapsConfig.get().keyPickupSoundEnabled && TeslaMapsConfig.get().keySoundClasses.allowsLocal()) {
            float volume = TeslaMapsConfig.get().keyPickupVolume;
            LoudSound.play(getPickupSound(), volume, 1.5f);
        }

        if (TeslaMapsConfig.get().keyPickupTitle && TeslaMapsConfig.get().keySoundClasses.allowsLocal()
                && !whoTitle(msg, "§8Wither Key")) {
            mc.gui.hud.setTitle(Component.literal("WITHER KEY").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
            mc.gui.hud.setSubtitle(Component.literal("picked up!").withStyle(ChatFormatting.GRAY));
            mc.gui.hud.setTimes(5, 30, 10); // fade in, stay, fade out
        }

        witherKeyOnGroundNotified = false;
        witherKeyPickupTime = System.currentTimeMillis();
        witherKeyPickedUp = true;
    }

    public static void onBloodKeyPickup(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (TeslaMapsConfig.get().keyPickupSoundEnabled && TeslaMapsConfig.get().keySoundClasses.allowsLocal()) {
            float volume = TeslaMapsConfig.get().keyPickupVolume;
            LoudSound.play(getPickupSound(), volume, 1.2f);
        }

        if (TeslaMapsConfig.get().keyPickupTitle && TeslaMapsConfig.get().keySoundClasses.allowsLocal()
                && !whoTitle(msg, "§cBlood Key")) {
            mc.gui.hud.setTitle(Component.literal("BLOOD KEY").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            mc.gui.hud.setSubtitle(Component.literal("picked up!").withStyle(ChatFormatting.RED));
            mc.gui.hud.setTimes(5, 30, 10); // fade in, stay, fade out
        }

        bloodKeyOnGroundNotified = false;
        bloodKeyPickupTime = System.currentTimeMillis();
        bloodKeyPickedUp = true;
    }

    private static final java.util.regex.Pattern KEY_OBTAINED =
            java.util.regex.Pattern.compile("^.*?(\\w+) has obtained (?:Wither|Blood) Key!$");

    // Devonian KeyPickup: "You" or the picker's class in class colour
    private static boolean whoTitle(String msg, String key) {
        if (!TeslaMapsConfig.get().keyPickupWho || msg == null) return false;
        java.util.regex.Matcher m = KEY_OBTAINED.matcher(com.teslamaps.utils.StringUtil.stripTrim(msg));
        if (!m.matches()) return false;
        Minecraft mc = Minecraft.getInstance();
        String name = m.group(1);
        String who;
        if (mc.player != null && name.equals(mc.player.getGameProfile().name())) who = "§bYou";
        else {
            String cls = com.teslamaps.player.PlayerTracker.getPlayer(name)
                    .map(com.teslamaps.player.PlayerTracker.DungeonPlayer::getDungeonClass).orElse(null);
            who = cls == null || cls.equals("EMPTY") || cls.equals("DEAD") ? "§f" + name : classColor(cls) + cls;
        }
        mc.gui.hud.setTitle(Component.literal(who + " §7Picked Up"));
        mc.gui.hud.setSubtitle(Component.literal(key));
        mc.gui.hud.setTimes(5, 30, 10);
        return true;
    }

    private static String classColor(String cls) {
        return switch (cls) {
            case "Healer" -> "§d";
            case "Mage" -> "§b";
            case "Berserk" -> "§c";
            case "Archer" -> "§6";
            case "Tank" -> "§a";
            default -> "§7";
        };
    }

    private static void onWitherKeyOnGround() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (TeslaMapsConfig.get().keyOnGroundSoundEnabled && TeslaMapsConfig.get().keySoundClasses.allowsLocal()) {
            float volume = TeslaMapsConfig.get().keyOnGroundVolume;
            LoudSound.play(getOnGroundSound(), volume, 1.0f);
        }

        if (TeslaMapsConfig.get().keyPickupTitle && TeslaMapsConfig.get().keySoundClasses.allowsLocal()) {
            mc.gui.hud.setTitle(Component.literal("WITHER KEY").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
            mc.gui.hud.setSubtitle(Component.literal("on ground!").withStyle(ChatFormatting.YELLOW));
            mc.gui.hud.setTimes(5, 40, 10);
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
            mc.gui.hud.setTitle(Component.literal("BLOOD KEY").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            mc.gui.hud.setSubtitle(Component.literal("on ground!").withStyle(ChatFormatting.YELLOW));
            mc.gui.hud.setTimes(5, 40, 10);
        }
    }

    public static void tick() {
        starLinkActive = false;
        tagText.clear();
        sniperTag.clear();
        itemLabels.clear();
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
            if (correctLividEntity != null && !LividSolver.hasBlindness() && TeslaMapsConfig.get().lividGlow) {
                glowingEntities.put(correctLividEntity, LividSolver.highlightColor() & 0x00FFFFFF);
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
        linkStarLabels(mc, inDungeon);

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
                if (entity instanceof ItemEntity ie && TeslaMapsConfig.get().droppedItemLabel) {
                    itemLabels.put(entity, "§ax" + ie.getItem().getCount() + " §f" + ie.getItem().getHoverName().getString());
                }
                int color = getHighlightColor(entity);
                EntityHighlight eh = new EntityHighlight(entity, color);
                highlightedEntities.add(eh);
                glowingEntities.put(entity, color & 0x00FFFFFF);
                if (inDungeon) lineWidths.put(entity, lineWidthFor(entity, entityName));
                if (customEntryFor(entity) != null) customHighlights.add(eh);
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

    private static boolean fairyVisited = false;

    // fairy door = wither door (coal) in front of an unvisited fairy room, as Devonian's DungeonDoor
    private static void scanFairyDoors() {
        Minecraft mc = Minecraft.getInstance();
        fairyDoorBoxes.clear();
        if (mc.level == null || mc.player == null || DungeonManager.isInBossArea()) return;

        com.teslamaps.map.DungeonRoom fairy = null;
        for (com.teslamaps.map.DungeonRoom r : DungeonManager.getGrid().getAllRooms())
            if (r.getType() == com.teslamaps.map.RoomType.FAIRY) { fairy = r; break; }
        if (fairy == null || fairy.getComponents().isEmpty()) return;
        int[] g = fairy.getComponents().get(0);

        if (fairy == DungeonManager.getCurrentRoom()) fairyVisited = true;
        if (fairyVisited) return;

        int[] c = com.teslamaps.scanner.ComponentGrid.gridToWorldCorner(g[0], g[1]);
        int mid = com.teslamaps.scanner.ComponentGrid.HALF_ROOM_SIZE;
        int size = com.teslamaps.scanner.ComponentGrid.ROOM_SIZE;
        // gap block in front of each side: east, west, south, north
        int[][] sides = {{c[0] + size, c[1] + mid, 1}, {c[0] - 1, c[1] + mid, 1},
                {c[0] + mid, c[1] + size, 0}, {c[0] + mid, c[1] - 1, 0}};
        for (int[] sd : sides) {
            int gx = sd[0], gz = sd[1];
            BlockPos door = new BlockPos(gx, 69, gz);
            if (!mc.level.isLoaded(door) || mc.level.getBlockState(door).getBlock() != Blocks.COAL_BLOCK) continue;
            boolean alongZ = sd[2] == 1;
            fairyDoorBoxes.add(alongZ
                    ? new AABB(gx, 69, gz - 1, gx + 1, 73, gz + 2)
                    : new AABB(gx - 1, 69, gz, gx + 2, 73, gz + 1));
        }
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
                } else if (block == Blocks.DYED_TERRACOTTA.red()) {
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
        Block targetBlock = isWitherDoor ? Blocks.COAL_BLOCK : Blocks.DYED_TERRACOTTA.red();

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

        if (entity instanceof ItemEntity ie && TeslaMapsConfig.get().droppedItemESP) {
            return !isShowcase(ie);
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
        if (starLinkActive) return linkedStar.containsKey(entity);
        return tagText(entity).contains("✯");
    }

    // ✯ label -> mob by entity id (label id - 1, Withermancer - 3), nearest unclaimed mob as fallback
    private static final java.util.IdentityHashMap<Entity, String> linkedStar = new java.util.IdentityHashMap<>();
    private static final List<AABB> orphanStarBoxes = new ArrayList<>();
    private static boolean starLinkActive;

    private static void linkStarLabels(Minecraft mc, boolean inDungeon) {
        linkedStar.clear();
        orphanStarBoxes.clear();
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!inDungeon || !c.starredMobESP || !c.starMobIdLink || mc.level == null) return;
        starLinkActive = true;
        for (Entity e : com.teslamaps.utils.EntityCache.armorStands()) {
            if (!(e instanceof ArmorStand as)) continue;
            String n = as.getName().getString();
            if (n.indexOf('✯') < 0) continue;
            Entity mob = mc.level.getEntity(as.getId() - (n.contains("Withermancer") ? 3 : 1));
            if (!validStarMob(mc, mob, as)) mob = nearestStarMob(mc, as);
            if (mob != null) linkedStar.put(mob, n);
            else orphanStarBoxes.add(new AABB(as.getX() - 0.4, as.getY() - 2.0, as.getZ() - 0.4,
                    as.getX() + 0.4, as.getY(), as.getZ() + 0.4));
        }
    }

    private static boolean validStarMob(Minecraft mc, Entity mob, ArmorStand as) {
        if (!(mob instanceof net.minecraft.world.entity.LivingEntity) || mob instanceof ArmorStand
                || mob == mc.player || !mob.isAlive() || linkedStar.containsKey(mob)) return false;
        double dy = as.getY() - mob.getY();
        return Math.abs(as.getX() - mob.getX()) <= 1.5 && Math.abs(as.getZ() - mob.getZ()) <= 1.5 && dy >= -1 && dy <= 5;
    }

    private static Entity nearestStarMob(Minecraft mc, ArmorStand as) {
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        for (net.minecraft.world.entity.LivingEntity le : mc.level.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class, as.getBoundingBox().inflate(1.5, 5, 1.5), le -> true)) {
            if (!validStarMob(mc, le, as)) continue;
            double dx = le.getX() - as.getX(), dz = le.getZ() - as.getZ();
            double d = dx * dx + dz * dz;
            if (d < bestD) { bestD = d; best = le; }
        }
        return best;
    }

    private static String starLabel(Entity entity) {
        if (starLinkActive) return linkedStar.get(entity);
        String t = tagText(entity);
        return t.contains("✯") ? t : null;
    }

    private static int starTypeColor(TeslaMapsConfig c, String label) {
        if (label.contains("Shadow Assassin")) return TeslaMapsConfig.parseColor(c.colorESPShadowAssassin);
        if (label.contains("Fels")) return TeslaMapsConfig.parseColor(c.colorESPStarredFel);
        if (label.contains("Skeleton Master")) return TeslaMapsConfig.parseColor(c.colorESPStarredSkeletonMaster);
        if (label.contains("Withermancer") || label.contains("Lord") || label.contains("Zombie Commander")
                || label.contains("Super Archer")) return TeslaMapsConfig.parseColor(c.colorESPStarredChunky);
        if (label.contains("Adventurer") || label.contains("Angry Archaeologist") || label.contains("King Midas"))
            return TeslaMapsConfig.parseColor(c.colorESPStarredMini);
        return TeslaMapsConfig.parseColor(c.colorESPStarred);
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
        String linked = starLinkActive ? linkedStar.get(entity) : null;
        if (linked != null) return linked.contains(text);
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
            if (config.droppedItemRarityColor) return itemColor(item.getItem());
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
        if (config.starMobTypeColors) {
            String label = starLabel(entity);
            if (label != null) return starTypeColor(config, label);
        }
        if (config.espStarSpecialColors && nametagContains(entity, "Withermancer")) {
            return TeslaMapsConfig.parseColor(config.colorESPStarredWithermancer);
        }
        return TeslaMapsConfig.parseColor(config.colorESPStarred);
    }

    public record EntityHighlight(Entity entity, int color) {}

    // shop / museum showcase items sit on (or under) an armor stand wearing a glass block
    private static List<ArmorStand> glassSource;
    private static final List<ArmorStand> glassStands = new ArrayList<>();

    private static boolean isShowcase(ItemEntity item) {
        if (item.isPassenger()) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        List<ArmorStand> stands = com.teslamaps.utils.EntityCache.armorStands();
        if (stands != glassSource) {
            glassSource = stands;
            glassStands.clear();
            for (ArmorStand as : stands) {
                if (as.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.GLASS)) glassStands.add(as);
            }
        }
        if (glassStands.isEmpty()) return false;
        AABB box = item.getBoundingBox().inflate(0.5, 2.5, 0.5);
        for (ArmorStand as : glassStands) if (as.getBoundingBox().intersects(box)) return true;
        return false;
    }

    private static final Map<net.minecraft.world.item.ItemStack, Integer> itemColors = new WeakHashMap<>();
    private static final Map<Entity, String> itemLabels = new WeakHashMap<>();

    private static int itemColor(net.minecraft.world.item.ItemStack stack) {
        Integer cached = itemColors.get(stack);
        if (cached != null) return cached;
        int rgb = com.teslamaps.features.RarityBackground.rarityColor(stack);
        if (rgb == -1) {
            Component name = stack.getHoverName();
            net.minecraft.network.chat.TextColor tc = name.getStyle().getColor();
            if (tc == null && !name.getSiblings().isEmpty()) tc = name.getSiblings().get(0).getStyle().getColor();
            rgb = tc != null ? tc.getValue() : 0xFFFFFF;
        }
        int c = 0xFF000000 | (rgb & 0xFFFFFF);
        itemColors.put(stack, c);
        return c;
    }

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

        if (!itemLabels.isEmpty()) {
            for (Map.Entry<Entity, String> entry : itemLabels.entrySet()) {
                Entity entity = entry.getKey();
                if (!entity.isAlive()) continue;
                AABB b = ESPRenderer.lerpBox(entity);
                ESPRenderer.drawText(matrices, entry.getValue(), new Vec3(b.getCenter().x, b.maxY + 0.5, b.getCenter().z),
                        0.8f, cameraPos);
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

        if (starLinkActive && !orphanStarBoxes.isEmpty()) {
            int oc = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorESPStarred) | 0xFF000000;
            for (AABB box : orphanStarBoxes) ESPRenderer.drawBoxOutline(matrices, box, oc, 2.0f, cameraPos, true);
        }

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
