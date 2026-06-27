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
package com.teslamaps.features;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.LoudSound;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class SecretWaypoints {
    private static final Gson GSON = new Gson();

    private static final int CORNER_START_X = -200;
    private static final int CORNER_START_Z = -200;
    private static final int HALF_ROOM_SIZE = 15;  // dungeonRoomSize / 2 = 31 / 2 = 15
    private static final int HALF_COMBINED_SIZE = 16;  // (31 + 1) / 2 = 16

    private static final int[][] ROOM_OFFSETS = {
        {-HALF_ROOM_SIZE, -HALF_ROOM_SIZE},  // Index 0 = 0° rotation
        {HALF_ROOM_SIZE, -HALF_ROOM_SIZE},   // Index 1 = 90° rotation
        {HALF_ROOM_SIZE, HALF_ROOM_SIZE},    // Index 2 = 180° rotation
        {-HALF_ROOM_SIZE, HALF_ROOM_SIZE}    // Index 3 = 270° rotation
    };

    private static RoomWaypoints[] waypointsData;
    private static boolean loaded = false;

    private static Map<Integer, Map<WaypointType, List<int[]>>> cachedWaypoints = new HashMap<>();
    private static int lastRoomId = -1;

    private static final Set<BlockPos> foundSecrets = new HashSet<>();

    public enum WaypointType {
        CHEST("chest"),
        ITEM("item"),
        BAT("bat"),
        ESSENCE("essence"),
        REDSTONE("redstone");

        public final String key;
        WaypointType(String key) { this.key = key; }

        public static WaypointType fromKey(String key) {
            for (WaypointType type : values()) {
                if (type.key.equals(key)) return type;
            }
            return null;
        }
    }

    private static class WaypointsJson {
        String name;
        Map<String, List<List<Integer>>> waypoints;
        int roomID;
    }

    public static class RoomWaypoints {
        public final String name;
        public final int roomID;
        public final Map<WaypointType, List<int[]>> waypoints = new EnumMap<>(WaypointType.class);

        public RoomWaypoints(WaypointsJson json) {
            this.name = json.name;
            this.roomID = json.roomID;
            if (json.waypoints != null) {
                for (Map.Entry<String, List<List<Integer>>> entry : json.waypoints.entrySet()) {
                    WaypointType type = WaypointType.fromKey(entry.getKey());
                    if (type != null && entry.getValue() != null) {
                        List<int[]> coords = new ArrayList<>();
                        for (List<Integer> pos : entry.getValue()) {
                            if (pos != null && pos.size() >= 3) {
                                coords.add(new int[]{pos.get(0), pos.get(1), pos.get(2)});
                            }
                        }
                        if (!coords.isEmpty()) {
                            waypoints.put(type, coords);
                        }
                    }
                }
            }
        }
    }

    public static void load() {
        if (loaded) return;
        try {
            InputStream is = SecretWaypoints.class.getResourceAsStream("/assets/teslamaps/data/secrets.json");
            if (is == null) {
                TeslaMaps.LOGGER.error("Secret waypoints data not found");
                return;
            }
            Type listType = new TypeToken<List<WaypointsJson>>(){}.getType();
            List<WaypointsJson> jsonList = GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), listType);
            if (jsonList == null || jsonList.isEmpty()) {
                TeslaMaps.LOGGER.error("Failed to parse secrets.json");
                return;
            }
            int maxId = jsonList.stream().mapToInt(j -> j.roomID).max().orElse(0);
            waypointsData = new RoomWaypoints[maxId + 1];
            for (WaypointsJson json : jsonList) {
                RoomWaypoints waypoints = new RoomWaypoints(json);
                if (waypoints.roomID >= 0 && waypoints.roomID < waypointsData.length) {
                    waypointsData[waypoints.roomID] = waypoints;
                }
            }
            loaded = true;
            TeslaMaps.LOGGER.info("Loaded secret waypoints for {} rooms", jsonList.size());
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load secret waypoints", e);
        }
    }

    public static RoomWaypoints getWaypointsData(int roomId) {
        if (!loaded || waypointsData == null) return null;
        if (roomId < 0 || roomId >= waypointsData.length) return null;
        return waypointsData[roomId];
    }

    private static final Set<BlockPos> checkedThisTick = new HashSet<>();
    private static long lastCheckTime = 0;
    private static final long CHECK_INTERVAL = 250; // Check every 250ms

    public static void tick() {
        if (!TeslaMapsConfig.get().secretWaypoints) return;
        if (!TeslaMapsConfig.get().secretWaypointHideCollected) return;
        if (!DungeonManager.isInDungeon()) return;
        if (!loaded) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        long now = System.currentTimeMillis();
        if (now - lastCheckTime < CHECK_INTERVAL) return;
        lastCheckTime = now;

        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getRoomData() == null) return;

        int roomId = room.getRoomData().getRoomID();
        Map<WaypointType, List<int[]>> worldWaypoints = cachedWaypoints.get(roomId);
        if (worldWaypoints == null) return;

        for (Map.Entry<WaypointType, List<int[]>> entry : worldWaypoints.entrySet()) {
            WaypointType type = entry.getKey();
            for (int[] pos : entry.getValue()) {
                BlockPos worldPos = new BlockPos(pos[0], pos[1], pos[2]);
                if (foundSecrets.contains(worldPos)) continue;

                if (isSecretCollected(mc.level, worldPos, type)) {
                    foundSecrets.add(worldPos);
                    TeslaMaps.LOGGER.debug("Secret collected: {} at {}", type.key, worldPos);
                }
            }
        }
    }

    private static boolean isSecretCollected(ClientLevel world, BlockPos pos, WaypointType type) {
        switch (type) {
            case CHEST -> {
                return false; // Handled by onChestOpened() callback
            }
            case BAT -> {
                AABB searchBox = new AABB(pos).inflate(1.5);
                var bats = world.getEntitiesOfClass(Bat.class, searchBox, bat -> !bat.isRemoved());
                return bats.isEmpty();
            }
            case ESSENCE -> {
                Block block = world.getBlockState(pos).getBlock();
                if (block == Blocks.AIR || block == Blocks.CAVE_AIR) {
                    return hasBeenNearWaypoint(pos);
                }
                return false;
            }
            case ITEM -> {
                AABB searchBox = new AABB(pos).inflate(1.0);
                var items = world.getEntitiesOfClass(ItemEntity.class, searchBox, item -> !item.isRemoved());
                return items.isEmpty() && hasBeenNearWaypoint(pos);
            }
            case REDSTONE -> {
                Block block = world.getBlockState(pos).getBlock();
                if (block == Blocks.AIR || block == Blocks.CAVE_AIR) {
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    private static final Set<BlockPos> visitedWaypoints = new HashSet<>();

    private static boolean hasBeenNearWaypoint(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        if (mc.player.blockPosition().closerThan(pos, 10)) {
            visitedWaypoints.add(pos);
        }
        return visitedWaypoints.contains(pos);
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().secretWaypoints) return;
        if (!DungeonManager.isInDungeon()) return;
        if (!loaded) {
            load();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getRoomData() == null) return;

        int roomId = room.getRoomData().getRoomID();

        if (roomId != lastRoomId) {
            lastRoomId = roomId;
            foundSecrets.clear();
            visitedWaypoints.clear();
        }

        Map<WaypointType, List<int[]>> worldWaypoints = cachedWaypoints.get(roomId);
        if (worldWaypoints == null) {
            worldWaypoints = addSecretsForRoom(room, mc.level);
            if (worldWaypoints != null) {
                cachedWaypoints.put(roomId, worldWaypoints);
            }
        }

        if (worldWaypoints == null) return;

        for (Map.Entry<WaypointType, List<int[]>> entry : worldWaypoints.entrySet()) {
            WaypointType type = entry.getKey();
            if (!isTypeEnabled(type)) continue;

            int color = getColorForType(type);

            for (int[] pos : entry.getValue()) {
                BlockPos worldPos = new BlockPos(pos[0], pos[1], pos[2]);
                if (foundSecrets.contains(worldPos)) continue;

                AABB box = new AABB(worldPos);
                // redstone-key skulls are easy to miss: fill them through walls so they pop
                if (type == WaypointType.REDSTONE && TeslaMapsConfig.get().redstoneKeyFilled) {
                    ESPRenderer.drawFilledBox(matrices, box, color, cameraPos, true);
                } else {
                    ESPRenderer.drawBoxOutline(matrices, box, color, 2.0f, cameraPos);
                }

                if (TeslaMapsConfig.get().secretWaypointTracers) {
                    Vec3 target = new Vec3(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5);
                    ESPRenderer.drawTracerFromCamera(matrices, target, color, cameraPos);
                }
            }
        }
    }

    private static Map<WaypointType, List<int[]>> addSecretsForRoom(DungeonRoom room, ClientLevel world) {
        Integer roomId = room.getRoomData() != null ? room.getRoomData().getRoomID() : null;
        if (roomId == null) return null;

        RoomWaypoints waypointData = getWaypointsData(roomId);
        if (waypointData == null) return null;

        int[] rotationAndCorner = findRotation(room, world);
        if (rotationAndCorner == null) return null;

        int rotation = rotationAndCorner[0];
        int cornerX = rotationAndCorner[1];
        int cornerZ = rotationAndCorner[2];

        Map<WaypointType, List<int[]>> result = new EnumMap<>(WaypointType.class);
        for (Map.Entry<WaypointType, List<int[]>> entry : waypointData.waypoints.entrySet()) {
            WaypointType type = entry.getKey();
            List<int[]> worldPositions = new ArrayList<>();

            for (int[] pos : entry.getValue()) {
                int[] worldXZ = fromComp(pos[0], pos[2], rotation, cornerX, cornerZ);
                worldPositions.add(new int[]{worldXZ[0], pos[1], worldXZ[1]});
            }

            result.put(type, worldPositions);
        }

        TeslaMaps.LOGGER.info("SecretWaypoints: Room '{}' rotation={} corner=[{},{}]",
                room.getName(), rotation, cornerX, cornerZ);

        return result;
    }

    private static int[] findRotation(DungeonRoom room, ClientLevel world) {
        String shape = room.getShape();
        List<int[]> components = room.getComponents();

        if (components.isEmpty()) return null;

        List<int[]> sortedComps = new ArrayList<>(components);
        sortedComps.sort((a, b) -> {
            int cxA = a[0] * 2;  // Convert grid to component index
            int cxB = b[0] * 2;
            int czA = a[1] * 2;
            int czB = b[1] * 2;
            if (cxA != cxB) return Integer.compare(cxA, cxB);
            return Integer.compare(czA, czB);
        });

        List<int[]> possibleCorners = new ArrayList<>();  // [idx, compIndex, worldX, worldZ]
        for (int compIdx = 0; compIdx < sortedComps.size(); compIdx++) {
            int[] comp = sortedComps.get(compIdx);
            int cx = comp[0] * 2;
            int cz = comp[1] * 2;
            int wx = CORNER_START_X + HALF_ROOM_SIZE + HALF_COMBINED_SIZE * cx;
            int wz = CORNER_START_Z + HALF_ROOM_SIZE + HALF_COMBINED_SIZE * cz;

            for (int i = 0; i < 4; i++) {
                possibleCorners.add(new int[]{i, compIdx, wx + ROOM_OFFSETS[i][0], wz + ROOM_OFFSETS[i][1]});
            }
        }

        int height = 0;
        for (int[] comp : sortedComps) {
            int cx = comp[0] * 2;
            int cz = comp[1] * 2;
            int wx = CORNER_START_X + HALF_ROOM_SIZE + HALF_COMBINED_SIZE * cx;
            int wz = CORNER_START_Z + HALF_ROOM_SIZE + HALF_COMBINED_SIZE * cz;
            height = getHighestY(world, wx, wz);
            if (height > 0) break;
        }
        if (height <= 0) return null;

        if ("1x4".equals(shape) && sortedComps.size() >= 2) {
            boolean isHorz = sortedComps.get(0)[1] == sortedComps.get(1)[1];  // Same Z = horizontal
            int lastIdx = sortedComps.size() - 1;

            possibleCorners.removeIf(corner -> {
                int idx = corner[0];
                int compIdx = corner[1];

                if (compIdx != 0 && compIdx != lastIdx) return true;

                if (compIdx == 0) {
                    if (isHorz) {
                        if (idx != 0 && idx != 3) return true;
                    } else {
                        if (idx != 0 && idx != 1) return true;
                    }
                } else {
                    if (isHorz) {
                        if (idx != 1 && idx != 2) return true;
                    } else {
                        if (idx != 2 && idx != 3) return true;
                    }
                }
                return false;
            });
        }

        for (int[] corner : possibleCorners) {
            int idx = corner[0];
            int x = corner[2];
            int z = corner[3];

            if (world.getBlockState(new BlockPos(x, height, z)).getBlock() == Blocks.BLUE_TERRACOTTA) {
                return new int[]{idx * 90, x, z};
            }
        }

        if (!sortedComps.isEmpty()) {
            int[] comp = sortedComps.get(0);
            int cx = comp[0] * 2;
            int cz = comp[1] * 2;
            int wx = CORNER_START_X + HALF_ROOM_SIZE + HALF_COMBINED_SIZE * cx;
            int wz = CORNER_START_Z + HALF_ROOM_SIZE + HALF_COMBINED_SIZE * cz;
            TeslaMaps.LOGGER.warn("SecretWaypoints: No blue terracotta for '{}', using fallback", room.getName());
            return new int[]{0, wx + ROOM_OFFSETS[0][0], wz + ROOM_OFFSETS[0][1]};
        }

        return null;
    }

    private static int getHighestY(ClientLevel world, int x, int z) {
        for (int y = 256; y >= 0; y--) {
            var state = world.getBlockState(new BlockPos(x, y, z));
            if (state.isAir() || state.getBlock() == Blocks.GOLD_BLOCK) continue;
            return y;
        }
        return -1;
    }

    private static int[] fromComp(int x, int z, int rotation, int cornerX, int cornerZ) {
        int[] rotated = rotatePos(x, z, (360 - rotation) % 360);
        return new int[]{rotated[0] + cornerX, rotated[1] + cornerZ};
    }

    private static int[] rotatePos(int x, int z, int degree) {
        return switch (degree) {
            case 0 -> new int[]{x, z};
            case 90 -> new int[]{z, -x};
            case 180 -> new int[]{-x, -z};
            case 270 -> new int[]{-z, x};
            default -> new int[]{x, z};
        };
    }

    public static void markFound(BlockPos pos) {
        foundSecrets.add(pos);
    }

    public static void onChestOpened(BlockPos chestPos) {
        if (!TeslaMapsConfig.get().secretWaypoints) return;
        if (!TeslaMapsConfig.get().secretWaypointHideCollected) return;
        if (!DungeonManager.isInDungeon()) return;

        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getRoomData() == null) return;

        int roomId = room.getRoomData().getRoomID();
        Map<WaypointType, List<int[]>> worldWaypoints = cachedWaypoints.get(roomId);
        if (worldWaypoints == null) return;

        List<int[]> chestWaypoints = worldWaypoints.get(WaypointType.CHEST);
        if (chestWaypoints == null) return;

        for (int[] pos : chestWaypoints) {
            BlockPos waypointPos = new BlockPos(pos[0], pos[1], pos[2]);
            if (waypointPos.closerThan(chestPos, 2.0)) {
                foundSecrets.add(waypointPos);
                TeslaMaps.LOGGER.debug("Chest secret collected at {}", waypointPos);
            }
        }
    }

    public static void reset() {
        foundSecrets.clear();
        cachedWaypoints.clear();
        visitedWaypoints.clear();
        lastRoomId = -1;
        lastCheckTime = 0;
    }

    public static void onSecretInteract(String type) {
        if (!TeslaMapsConfig.get().secretWaypoints) return;
        if (!TeslaMapsConfig.get().secretSound) return;
        if (!DungeonManager.isInDungeon()) return;

        LoudSound.play(com.teslamaps.utils.SoundOptions.resolve(TeslaMapsConfig.get().secretSoundType),
                TeslaMapsConfig.get().secretSoundVolume, TeslaMapsConfig.get().secretSoundPitch);

        TeslaMaps.LOGGER.debug("[SecretWaypoints] Secret interaction: {}", type);
    }

    private static boolean isTypeEnabled(WaypointType type) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        return switch (type) {
            case CHEST -> config.secretWaypointChests;
            case ITEM -> config.secretWaypointItems;
            case BAT -> config.secretWaypointBats;
            case ESSENCE -> config.secretWaypointEssence;
            case REDSTONE -> config.secretWaypointRedstoneKey;
        };
    }

    private static int getColorForType(WaypointType type) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        return switch (type) {
            case CHEST -> TeslaMapsConfig.parseColor(config.colorSecretChest);
            case ITEM -> TeslaMapsConfig.parseColor(config.colorSecretItem);
            case BAT -> TeslaMapsConfig.parseColor(config.colorSecretBat);
            case ESSENCE -> TeslaMapsConfig.parseColor(config.colorSecretEssence);
            case REDSTONE -> TeslaMapsConfig.parseColor(config.colorSecretRedstone);
        };
    }
}
