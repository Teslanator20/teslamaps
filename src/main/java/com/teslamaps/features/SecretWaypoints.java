package com.teslamaps.features;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.LoudSound;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Secret Waypoints feature - renders 3D waypoints at known secret locations.
 * Renders 3D waypoints at known secret locations.
 */
public class SecretWaypoints {
    private static final Gson GSON = new Gson();

    // Dungeon coordinate constants
    private static final int CORNER_START_X = -200;
    private static final int CORNER_START_Z = -200;
    private static final int HALF_ROOM_SIZE = 15;  // dungeonRoomSize / 2 = 31 / 2 = 15
    private static final int HALF_COMBINED_SIZE = 16;  // (31 + 1) / 2 = 16

    // Room corner offsets for rotation detection
    private static final int[][] ROOM_OFFSETS = {
        {-HALF_ROOM_SIZE, -HALF_ROOM_SIZE},  // Index 0 = 0° rotation
        {HALF_ROOM_SIZE, -HALF_ROOM_SIZE},   // Index 1 = 90° rotation
        {HALF_ROOM_SIZE, HALF_ROOM_SIZE},    // Index 2 = 180° rotation
        {-HALF_ROOM_SIZE, HALF_ROOM_SIZE}    // Index 3 = 270° rotation
    };

    // Waypoint data indexed by roomID
    private static RoomWaypoints[] waypointsData;
    private static boolean loaded = false;

    // Per-room cached waypoints (already transformed to world coords)
    private static Map<Integer, Map<WaypointType, List<int[]>>> cachedWaypoints = new HashMap<>();
    private static int lastRoomId = -1;

    // Track found secrets
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

    // Track which waypoints we've already checked for collection
    private static final Set<BlockPos> checkedThisTick = new HashSet<>();
    private static long lastCheckTime = 0;
    private static final long CHECK_INTERVAL = 250; // Check every 250ms

    public static void tick() {
        if (!TeslaMapsConfig.get().secretWaypoints) return;
        if (!TeslaMapsConfig.get().secretWaypointHideCollected) return;
        if (!DungeonManager.isInDungeon()) return;
        if (!loaded) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        // Only check periodically to reduce lag
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < CHECK_INTERVAL) return;
        lastCheckTime = now;

        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getRoomData() == null) return;

        int roomId = room.getRoomData().getRoomID();
        Map<WaypointType, List<int[]>> worldWaypoints = cachedWaypoints.get(roomId);
        if (worldWaypoints == null) return;

        // Check each waypoint type for collection
        for (Map.Entry<WaypointType, List<int[]>> entry : worldWaypoints.entrySet()) {
            WaypointType type = entry.getKey();
            for (int[] pos : entry.getValue()) {
                BlockPos worldPos = new BlockPos(pos[0], pos[1], pos[2]);
                if (foundSecrets.contains(worldPos)) continue;

                if (isSecretCollected(mc.world, worldPos, type)) {
                    foundSecrets.add(worldPos);
                    TeslaMaps.LOGGER.debug("Secret collected: {} at {}", type.key, worldPos);
                }
            }
        }
    }

    /**
     * Check if a secret at the given position has been collected.
     */
    private static boolean isSecretCollected(ClientWorld world, BlockPos pos, WaypointType type) {
        switch (type) {
            case CHEST -> {
                // Track if player has opened a chest at this location
                // We mark chest as collected when player interacts with it (see onChestOpened)
                return false; // Handled by onChestOpened() callback
            }
            case BAT -> {
                // Check if there are any bats within 1.5 blocks of the waypoint
                Box searchBox = new Box(pos).expand(1.5);
                var bats = world.getEntitiesByClass(BatEntity.class, searchBox, bat -> !bat.isRemoved());
                // If no bats found near waypoint, it was killed
                return bats.isEmpty();
            }
            case ESSENCE -> {
                // Essence becomes air when picked up
                Block block = world.getBlockState(pos).getBlock();
                if (block == Blocks.AIR || block == Blocks.CAVE_AIR) {
                    // Only mark as collected if player has been near (to avoid false positives on load)
                    return hasBeenNearWaypoint(pos);
                }
                return false;
            }
            case ITEM -> {
                // Item secrets drop items - check if item exists nearby
                Box searchBox = new Box(pos).expand(1.0);
                var items = world.getEntitiesByClass(ItemEntity.class, searchBox, item -> !item.isRemoved());
                // Similar logic - only mark collected if we've been near and items are gone
                return items.isEmpty() && hasBeenNearWaypoint(pos);
            }
            case REDSTONE -> {
                // Redstone key secrets - check if lever/button was activated
                // These are usually skulls or trapped chests, similar to chest logic
                Block block = world.getBlockState(pos).getBlock();
                if (block == Blocks.AIR || block == Blocks.CAVE_AIR) {
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    // Track positions player has been near (to avoid false positives on spawn)
    private static final Set<BlockPos> visitedWaypoints = new HashSet<>();

    private static boolean hasBeenNearWaypoint(BlockPos pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;

        // Check if player is within 10 blocks
        if (mc.player.getBlockPos().isWithinDistance(pos, 10)) {
            visitedWaypoints.add(pos);
        }
        return visitedWaypoints.contains(pos);
    }

    public static void render(MatrixStack matrices, Vec3d cameraPos) {
        if (!TeslaMapsConfig.get().secretWaypoints) return;
        if (!DungeonManager.isInDungeon()) return;
        if (!loaded) {
            load();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getRoomData() == null) return;

        int roomId = room.getRoomData().getRoomID();

        // Check for room change
        if (roomId != lastRoomId) {
            lastRoomId = roomId;
            foundSecrets.clear();
            visitedWaypoints.clear();
        }

        // Get or compute cached waypoints for this room
        Map<WaypointType, List<int[]>> worldWaypoints = cachedWaypoints.get(roomId);
        if (worldWaypoints == null) {
            worldWaypoints = addSecretsForRoom(room, mc.world);
            if (worldWaypoints != null) {
                cachedWaypoints.put(roomId, worldWaypoints);
            }
        }

        if (worldWaypoints == null) return;

        // Render waypoints
        for (Map.Entry<WaypointType, List<int[]>> entry : worldWaypoints.entrySet()) {
            WaypointType type = entry.getKey();
            if (!isTypeEnabled(type)) continue;

            int color = getColorForType(type);

            for (int[] pos : entry.getValue()) {
                BlockPos worldPos = new BlockPos(pos[0], pos[1], pos[2]);
                if (foundSecrets.contains(worldPos)) continue;

                Box box = new Box(worldPos);
                ESPRenderer.drawBoxOutline(matrices, box, color, 2.0f, cameraPos);

                if (TeslaMapsConfig.get().secretWaypointTracers) {
                    Vec3d target = new Vec3d(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5);
                    ESPRenderer.drawTracerFromCamera(matrices, target, color, cameraPos);
                }
            }
        }
    }

    /**
     * Add secrets for a room
     * Returns map of waypoint type -> list of [x, y, z] world coordinates.
     */
    private static Map<WaypointType, List<int[]>> addSecretsForRoom(DungeonRoom room, ClientWorld world) {
        Integer roomId = room.getRoomData() != null ? room.getRoomData().getRoomID() : null;
        if (roomId == null) return null;

        RoomWaypoints waypointData = getWaypointsData(roomId);
        if (waypointData == null) return null;

        // Find rotation 
        int[] rotationAndCorner = findRotation(room, world);
        if (rotationAndCorner == null) return null;

        int rotation = rotationAndCorner[0];
        int cornerX = rotationAndCorner[1];
        int cornerZ = rotationAndCorner[2];

        // Transform waypoints to world coordinates
        Map<WaypointType, List<int[]>> result = new EnumMap<>(WaypointType.class);
        for (Map.Entry<WaypointType, List<int[]>> entry : waypointData.waypoints.entrySet()) {
            WaypointType type = entry.getKey();
            List<int[]> worldPositions = new ArrayList<>();

            for (int[] pos : entry.getValue()) {
                // fromComp: convert component coords to world coords
                int[] worldXZ = fromComp(pos[0], pos[2], rotation, cornerX, cornerZ);
                worldPositions.add(new int[]{worldXZ[0], pos[1], worldXZ[1]});
            }

            result.put(type, worldPositions);
        }

        TeslaMaps.LOGGER.info("SecretWaypoints: Room '{}' rotation={} corner=[{},{}]",
                room.getName(), rotation, cornerX, cornerZ);

        return result;
    }

    /**
     * Find rotation by scanning for blue terracotta
     * Returns [rotation, cornerX, cornerZ] or null if not found.
     */
    private static int[] findRotation(DungeonRoom room, ClientWorld world) {
        String shape = room.getShape();
        List<int[]> components = room.getComponents();

        if (components.isEmpty()) return null;

        // Sort components as needed: by cx then cz
        List<int[]> sortedComps = new ArrayList<>(components);
        sortedComps.sort((a, b) -> {
            int cxA = a[0] * 2;  // Convert grid to component index
            int cxB = b[0] * 2;
            int czA = a[1] * 2;
            int czB = b[1] * 2;
            if (cxA != cxB) return Integer.compare(cxA, cxB);
            return Integer.compare(czA, czB);
        });

        // Build possible corners list (for rotation detection)
        List<int[]> possibleCorners = new ArrayList<>();  // [idx, compIndex, worldX, worldZ]
        for (int compIdx = 0; compIdx < sortedComps.size(); compIdx++) {
            int[] comp = sortedComps.get(compIdx);
            // Convert grid (0-5) to component (0,2,4,6,8,10)
            int cx = comp[0] * 2;
            int cz = comp[1] * 2;
            // Get component world center for world coordinates
            int wx = CORNER_START_X + HALF_ROOM_SIZE + HALF_COMBINED_SIZE * cx;
            int wz = CORNER_START_Z + HALF_ROOM_SIZE + HALF_COMBINED_SIZE * cz;

            for (int i = 0; i < 4; i++) {
                possibleCorners.add(new int[]{i, compIdx, wx + ROOM_OFFSETS[i][0], wz + ROOM_OFFSETS[i][1]});
            }
        }

        // Get roof height
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

        // For 1x4 rooms, filter corners as needed
        if ("1x4".equals(shape) && sortedComps.size() >= 2) {
            boolean isHorz = sortedComps.get(0)[1] == sortedComps.get(1)[1];  // Same Z = horizontal
            int lastIdx = sortedComps.size() - 1;

            possibleCorners.removeIf(corner -> {
                int idx = corner[0];
                int compIdx = corner[1];

                // Only check first and last components
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

        // Find blue terracotta
        for (int[] corner : possibleCorners) {
            int idx = corner[0];
            int x = corner[2];
            int z = corner[3];

            if (world.getBlockState(new BlockPos(x, height, z)).getBlock() == Blocks.BLUE_TERRACOTTA) {
                return new int[]{idx * 90, x, z};
            }
        }

        // Fallback: use first component's corner 0
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

    /**
     * Get highest Y at position
     */
    private static int getHighestY(ClientWorld world, int x, int z) {
        for (int y = 256; y >= 0; y--) {
            var state = world.getBlockState(new BlockPos(x, y, z));
            if (state.isAir() || state.getBlock() == Blocks.GOLD_BLOCK) continue;
            return y;
        }
        return -1;
    }

    /**
     * Convert component coords to world coords
     */
    private static int[] fromComp(int x, int z, int rotation, int cornerX, int cornerZ) {
        int[] rotated = rotatePos(x, z, (360 - rotation) % 360);
        return new int[]{rotated[0] + cornerX, rotated[1] + cornerZ};
    }

    /**
     * Rotate position
     */
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

    /**
     * Called when player opens a chest. Marks nearby chest waypoints as found.
     */
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

        // Find chest waypoints within 2 blocks of opened chest
        for (int[] pos : chestWaypoints) {
            BlockPos waypointPos = new BlockPos(pos[0], pos[1], pos[2]);
            if (waypointPos.isWithinDistance(chestPos, 2.0)) {
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

    /**
     * Called when player interacts with a secret block (chest, lever, button, skull).
     * Plays a sound notification.
     */
    public static void onSecretInteract(String type) {
        if (!TeslaMapsConfig.get().secretWaypoints) return;
        if (!TeslaMapsConfig.get().secretSound) return;
        if (!DungeonManager.isInDungeon()) return;

        // Play sound based on config
        float volume = TeslaMapsConfig.get().secretSoundVolume;
        net.minecraft.sound.SoundEvent sound = getSoundForType(TeslaMapsConfig.get().secretSoundType);
        LoudSound.play(sound, volume, 1.2f);

        TeslaMaps.LOGGER.debug("[SecretWaypoints] Secret interaction: {}", type);
    }

    private static net.minecraft.sound.SoundEvent getSoundForType(String soundType) {
        return switch (soundType) {
            case "LEVEL_UP" -> SoundEvents.ENTITY_PLAYER_LEVELUP;
            case "NOTE_PLING" -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            case "AMETHYST_CHIME" -> SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
            default -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
        };
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
