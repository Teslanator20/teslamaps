package com.teslamaps.scanner;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.CheckmarkState;
import com.teslamaps.map.DungeonRoom;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapDecoration;
import net.minecraft.item.map.MapState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scans the dungeon map item in the player's inventory to detect checkmarks and room exploration.
 * Also extracts player marker positions from the map.
 * Based on IllegalMap's scanHotbarMap method and Skyblocker's DungeonMapUtils.
 */
public class MapScanner {
    private static int tickCounter = 0;

    // Map color values for checkmarks (from Skyblocker's DungeonMapUtils)
    // These are packed color IDs from MapColor
    private static final byte COLOR_GREEN_CHECKMARK = 30;  // MapColor.PLANT.getPackedId(MapColor.Brightness.HIGH)
    private static final byte COLOR_WHITE_CHECKMARK = 34;  // MapColor.SNOW.getPackedId(MapColor.Brightness.HIGH)
    private static final byte COLOR_FAILED = 18;           // MapColor.FIRE.getPackedId(MapColor.Brightness.HIGH)
    private static final byte COLOR_UNEXPLORED = 0;        // Black
    private static final byte COLOR_UNEXPLORED_ALT = 85;   // Unknown room color

    // Player name cache for map positions
    private static final Map<Integer, String> playerIndexToName = new HashMap<>();

    // Player positions extracted from the map (x, z in map coordinates 0-128)
    // Each entry: [mapX, mapZ, rotation, isLocalPlayer (1=local, 0=other)]
    private static final List<int[]> mapPlayerPositions = new ArrayList<>();

    // Door colors on the dungeon map (from Skyblocker)
    private static final byte COLOR_WITHER_DOOR = 119;  // Black door
    private static final byte COLOR_BLOOD_DOOR = 18;    // Red door (same as failed checkmark)

    // Door world positions for 3D rendering
    private static final List<double[]> witherDoorBoxes = new ArrayList<>();  // [minX, minY, minZ, maxX, maxY, maxZ]
    private static final List<double[]> bloodDoorBoxes = new ArrayList<>();

    // Track how many consecutive scans a room has been explored (to avoid false positives on first entry)
    private static final Map<String, Integer> roomExplorationScanCount = new HashMap<>();

    // Dungeon map parameters - dynamically detected
    public static int mapCornerX = -1;  // Will be detected from map
    public static int mapCornerY = -1;
    public static int mapRoomSize = 16;
    public static int mapGapSize = 4;
    private static boolean mapParamsDetected = false;

    public static void tick() {
        if (!DungeonManager.isInDungeon()) {
            return;
        }

        tickCounter++;

        // Scan every 10 ticks (2x per second)
        if (tickCounter % 10 != 0) {
            return;
        }

        scanDungeonMap();
    }

    private static void scanDungeonMap() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // Find the map item in the player's inventory
        MapState mapState = findDungeonMapState(mc);
        if (mapState == null) {
            if (debugLogCounter++ % 100 == 0) {
                TeslaMaps.LOGGER.info("[MapScanner] No map found in hotbar");
            }
            return;
        }

        // Extract player positions from map decorations
        extractPlayerPositions(mapState);

        // Scan for door positions on the map
        scanDoorsFromMap(mapState);

        byte[] colors = mapState.colors;
        if (colors == null || colors.length < 16384) {
            return;
        }

        // Dynamically detect map parameters if not yet done
        if (!mapParamsDetected) {
            detectMapParameters(colors);
        }

        // Skip if we couldn't detect the map parameters
        if (mapCornerX < 0 || mapCornerY < 0) {
            return;
        }

        // Log once per scan cycle for debugging
        boolean shouldLog = (debugLogCounter++ % 50 == 0);

        // Scan each room position on the map (using Skyblocker's algorithm)
        for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
            if (room.getComponents().isEmpty()) continue;

            // Check each component (segment) of the room for checkmarks AND exploration
            CheckmarkState bestState = CheckmarkState.UNEXPLORED;
            boolean mapShowsExplored = false;
            int exploredPixelCount = 0;

            // Count checkmark pixels per type
            int greenCount = 0, whiteCount = 0, failedCount = 0;
            java.util.Map<Integer, Integer> colorCounts = new java.util.HashMap<>();
            java.util.Map<Integer, Integer> allColorCounts = new java.util.HashMap<>();

            for (int[] comp : room.getComponents()) {
                int gridX = comp[0];
                int gridZ = comp[1];

                // Calculate the top-left corner of this room segment on the map
                int topLeftX = mapCornerX + (mapRoomSize + mapGapSize) * gridX;
                int topLeftY = mapCornerY + (mapRoomSize + mapGapSize) * gridZ;

                // Scan the room area for exploration status
                int halfRoom = mapRoomSize / 2;

                for (int dy = 0; dy < mapRoomSize; dy++) {
                    for (int dx = 0; dx < mapRoomSize; dx++) {
                        int checkX = topLeftX + dx;
                        int checkY = topLeftY + dy;
                        if (checkX < 0 || checkX >= 128 || checkY < 0 || checkY >= 128) continue;

                        int index = checkX + checkY * 128;
                        if (index < 0 || index >= colors.length) continue;

                        byte color = colors[index];
                        int c = color & 0xFF;

                        // Track all colors for debug
                        if (shouldLog && c != 0) {
                            allColorCounts.merge(c, 1, Integer::sum);
                        }

                        // Check CENTER area for exploration
                        if (dx >= 4 && dx <= 12 && dy >= 4 && dy <= 12) {
                            if (isExploredColor(color)) {
                                exploredPixelCount++;
                            }
                        }
                    }
                }

                // Scan ENTIRE room area for checkmark colors
                // The checkmark can be anywhere in the room
                for (int dy = 0; dy < mapRoomSize; dy++) {
                    for (int dx = 0; dx < mapRoomSize; dx++) {
                        int checkX = topLeftX + dx;
                        int checkY = topLeftY + dy;
                        if (checkX < 0 || checkX >= 128 || checkY < 0 || checkY >= 128) continue;

                        int idx = checkX + checkY * 128;
                        if (idx < 0 || idx >= colors.length) continue;

                        int c = colors[idx] & 0xFF;
                        if (c == 30) greenCount++;       // GREEN (MapColor.PLANT)
                        else if (c == 34) whiteCount++;  // WHITE (MapColor.SNOW)
                        else if (c == 18) failedCount++; // RED (MapColor.FIRE)
                    }
                }
            }

            // Entrance rooms are always GREEN (no secrets to find)
            if (room.getType() == com.teslamaps.map.RoomType.ENTRANCE) {
                bestState = CheckmarkState.GREEN;
            } else {
                // Scanning entire room - prioritize white over green
                // White/failed need 3+ pixels, green needs more to avoid Entrance bleed
                if (whiteCount >= 3) {
                    bestState = CheckmarkState.WHITE;
                    if (shouldLog) {
                        TeslaMaps.LOGGER.info("[MapScanner] Room '{}' has {} white checkmark pixels -> WHITE",
                                room.getName(), whiteCount);
                    }
                } else if (failedCount >= 3) {
                    bestState = CheckmarkState.FAILED;
                    if (shouldLog) {
                        TeslaMaps.LOGGER.info("[MapScanner] Room '{}' has {} failed checkmark pixels -> FAILED",
                                room.getName(), failedCount);
                    }
                } else if (greenCount >= 25 && !isAdjacentToEntrance(room)) {
                    // Green needs threshold AND must not be adjacent to Entrance (to avoid bleed)
                    bestState = CheckmarkState.GREEN;
                    if (shouldLog) {
                        TeslaMaps.LOGGER.info("[MapScanner] Room '{}' has {} green checkmark pixels -> GREEN",
                                room.getName(), greenCount);
                    }
                } else if (shouldLog) {
                    // Debug: log rooms without checkmarks (both explored and unexplored)
                    int[] comp = room.getPrimaryComponent();
                    int topX = mapCornerX + (mapRoomSize + mapGapSize) * comp[0];
                    int topY = mapCornerY + (mapRoomSize + mapGapSize) * comp[1];
                    String status = room.isExplored() ? "explored" : "unexplored(" + exploredPixelCount + "px)";
                    TeslaMaps.LOGGER.info("[MapScanner] Room '{}' [{}] {} g={} w={} f={} | all: {}",
                            room.getName(), comp[0] + "," + comp[1], status,
                            greenCount, whiteCount, failedCount, allColorCounts);
                }
            }

            // Need significant number of explored pixels in center to confirm exploration
            // Center area is ~8x8 = 64 pixels per component, require at least 20 to be "explored" color
            int componentsCount = room.getComponents().size();
            int threshold = componentsCount * 20;
            mapShowsExplored = exploredPixelCount >= threshold;

            // Track if room was already explored before this scan
            boolean wasAlreadyExplored = room.isExplored();
            String roomKey = room.getName() + "_" + room.getPrimaryComponent()[0] + "_" + room.getPrimaryComponent()[1];

            // Initialize scan count for rooms we haven't seen yet
            if (!roomExplorationScanCount.containsKey(roomKey)) {
                // If room is already explored (e.g., entrance or rooms explored before we started tracking),
                // start with high count so checkmarks can be applied immediately
                roomExplorationScanCount.put(roomKey, wasAlreadyExplored ? 10 : 0);
            }

            // Update exploration state based on map (failsafe - only mark explored if map confirms)
            if (mapShowsExplored && !room.isExplored()) {
                room.setExplored(true);
                // When first explored, set to NONE (visited but not cleared) - NOT to any detected checkmark
                // This prevents false positives from adjacent room bleed on first exploration
                if (room.getCheckmarkState() == CheckmarkState.UNEXPLORED) {
                    room.setCheckmarkState(CheckmarkState.NONE);
                }
                // Initialize found secrets to 0 so it shows "0/X" for explored rooms
                if (room.getFoundSecrets() < 0 && room.getSecrets() > 0) {
                    room.setFoundSecrets(0);
                }
                // Reset counting scans since exploration (room just became explored)
                roomExplorationScanCount.put(roomKey, 0);
                TeslaMaps.LOGGER.info("[MapScanner] Room '{}' [{}] marked as explored by map (pixels={})",
                        room.getName(), room.getPrimaryComponent()[0] + "," + room.getPrimaryComponent()[1], exploredPixelCount);
            }

            // Increment scan count for explored rooms
            if (room.isExplored()) {
                int scanCount = roomExplorationScanCount.getOrDefault(roomKey, 0);
                roomExplorationScanCount.put(roomKey, scanCount + 1);
            }

            // If map doesn't show explored, reset exploration state (failsafe)
            if (!mapShowsExplored && room.isExplored() && room.getCheckmarkState() == CheckmarkState.UNEXPLORED) {
                room.setExplored(false);
                roomExplorationScanCount.put(roomKey, 0);
            }

            // Update checkmark if found - BUT only if room has been explored for at least 2 scan cycles (1 second)
            // This prevents false positives from map reveal animations or entrance bleed
            int scansSinceExplored = roomExplorationScanCount.getOrDefault(roomKey, 0);
            if (bestState != CheckmarkState.UNEXPLORED && room.getCheckmarkState() != bestState) {
                // Only apply checkmark if room has been explored for multiple scans
                if (wasAlreadyExplored && scansSinceExplored >= 2) {
                    CheckmarkState oldState = room.getCheckmarkState();
                    room.setCheckmarkState(bestState);
                    TeslaMaps.LOGGER.info("[MapScanner] Room '{}' [{}] checkmark updated: {} -> {} (after {} scans)",
                            room.getName(), room.getPrimaryComponent()[0] + "," + room.getPrimaryComponent()[1],
                            oldState, bestState, scansSinceExplored);

                    // If room turns GREEN, notify SecretTracker
                    if (bestState == CheckmarkState.GREEN) {
                        SecretTracker.onRoomCompleted(room);
                    }
                } else if (shouldLog && bestState != CheckmarkState.UNEXPLORED) {
                    TeslaMaps.LOGGER.info("[MapScanner] Room '{}' detected state {} but waiting (explored={}, scans={}/2)",
                            room.getName(), bestState, wasAlreadyExplored, scansSinceExplored);
                }
            }
        }
    }

    /**
     * Check if a color indicates the room is explored on the map.
     * Map colors have format: baseColor * 4 + brightness (0=dark, 1=normal, 2=light, 3=lightest)
     * Unexplored rooms use darker shades, explored rooms use brighter shades.
     */
    private static boolean isExploredColor(byte color) {
        int c = color & 0xFF;
        // 0 = transparent/black (unexplored)
        if (c == 0) return false;

        // Map color brightness is in the lower 2 bits
        // 0 = darkest (unexplored), 1 = dark, 2 = normal (explored), 3 = bright (explored)
        int brightness = c % 4;

        // Only count as explored if brightness >= 2 (normal or bright)
        // This filters out the dark outlines of unexplored rooms
        return brightness >= 2;
    }

    private static int debugLogCounter = 0;

    /**
     * Check if a color matches any checkmark color.
     * Using int comparison to avoid signed byte issues.
     */
    private static boolean matchesCheckmarkColor(byte color) {
        int c = color & 0xFF;  // Convert to unsigned
        return c == 30 || c == 34 || c == 18;  // GREEN, WHITE, FAILED
    }

    /**
     * Convert a map color to checkmark state.
     * Using int comparison to avoid signed byte issues.
     */
    private static CheckmarkState getCheckmarkStateFromColor(byte color) {
        int c = color & 0xFF;  // Convert to unsigned
        if (c == 30) return CheckmarkState.GREEN;   // GREEN
        if (c == 34) return CheckmarkState.WHITE;   // WHITE
        if (c == 18) return CheckmarkState.FAILED;  // FAILED/RED
        return CheckmarkState.NONE;
    }

    private static MapState findDungeonMapState(MinecraftClient mc) {
        // Check hotbar slots for a map
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof FilledMapItem) {
                MapIdComponent mapId = stack.get(DataComponentTypes.MAP_ID);
                if (mapId != null) {
                    MapState state = FilledMapItem.getMapState(mapId, mc.world);
                    if (state != null) {
                        return state;
                    }
                }
            }
        }

        // Also check off-hand
        ItemStack offhand = mc.player.getOffHandStack();
        if (offhand.getItem() instanceof FilledMapItem) {
            MapIdComponent mapId = offhand.get(DataComponentTypes.MAP_ID);
            if (mapId != null) {
                MapState state = FilledMapItem.getMapState(mapId, mc.world);
                if (state != null) {
                    return state;
                }
            }
        }

        return null;
    }

    /**
     * Extract player positions from map decorations.
     * Player icons on the dungeon map show where all party members are.
     */
    private static void extractPlayerPositions(MapState mapState) {
        mapPlayerPositions.clear();

        // MapState has decorations which include player markers
        try {
            // Get all decorations from the map
            Iterable<MapDecoration> decorations = mapState.getDecorations();
            if (decorations == null) {
                if (debugLogCounter % 100 == 0) {
                    TeslaMaps.LOGGER.info("[MapScanner] No decorations on map");
                }
                return;
            }

            int decorationCount = 0;
            for (MapDecoration decoration : decorations) {
                decorationCount++;
                // Get the decoration type's registry ID
                String typeId = decoration.type().getIdAsString();

                // Log all decorations for debugging
                if (debugLogCounter % 100 == 0) {
                    TeslaMaps.LOGGER.info("[MapScanner] Decoration: type={} x={} z={} rot={}",
                            typeId, decoration.x(), decoration.z(), decoration.rotation());
                }

                // Hypixel uses these decoration types for dungeon map:
                // - minecraft:player (green) = local player
                // - minecraft:blue_marker = other party members
                // - minecraft:frame = also other party members
                if (typeId.contains("player") || typeId.contains("blue_marker") || typeId.contains("frame")) {
                    // x and z are bytes in range -128 to 127, representing map coordinates
                    // They need to be converted to 0-128 range for our map
                    int mapX = (decoration.x() + 128) / 2;  // Convert from -128..128 to 0..128
                    int mapZ = (decoration.z() + 128) / 2;

                    // "player" type (green marker) = local player, blue_marker/frame = others
                    int isLocal = typeId.equals("minecraft:player") ? 1 : 0;

                    mapPlayerPositions.add(new int[]{mapX, mapZ, (int)decoration.rotation(), isLocal});

                    TeslaMaps.LOGGER.info("[MapScanner] Player at map[{},{}] type={} isLocal={}", mapX, mapZ, typeId, isLocal);
                }
            }

            if (debugLogCounter % 100 == 0) {
                TeslaMaps.LOGGER.info("[MapScanner] Total decorations: {}, player positions: {}",
                        decorationCount, mapPlayerPositions.size());
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[MapScanner] Error extracting player positions from map", e);
        }
    }

    /**
     * Scan the dungeon map for wither and blood doors.
     * Based on Skyblocker's door detection method.
     * Color 119 = wither door (black), Color 18 = blood door (red)
     */
    private static void scanDoorsFromMap(MapState mapState) {
        witherDoorBoxes.clear();
        bloodDoorBoxes.clear();

        if (!mapParamsDetected || mapCornerX < 0 || mapCornerY < 0) {
            return;
        }

        byte[] colors = mapState.colors;
        if (colors == null || colors.length < 16384) {
            return;
        }

        int cellSize = mapRoomSize + mapGapSize;  // Usually 16 + 4 = 20

        // Debug: scan entire map for door colors to find them
        boolean shouldDebug = debugLogCounter % 100 == 0;
        if (shouldDebug) {
            Map<Integer, Integer> doorColorCounts = new HashMap<>();
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    int c = colors[x + y * 128] & 0xFF;
                    // Look for potential door colors (dark colors)
                    if (c == 119 || c == 18 || c == 116 || c == 117 || c == 118 || c == 44 || c == 45 || c == 46 || c == 47) {
                        doorColorCounts.merge(c, 1, Integer::sum);
                    }
                }
            }
            if (!doorColorCounts.isEmpty()) {
                TeslaMaps.LOGGER.info("[MapScanner] Door color scan - potential door colors on map: {}", doorColorCounts);
            }
        }

        // Scan for vertical doors (between rooms horizontally adjacent)
        // Door positions are in the gap between rooms
        for (int mapX = mapCornerX + mapRoomSize; mapX < 128; mapX += cellSize) {
            for (int mapY = mapCornerY + mapRoomSize / 2; mapY < 128; mapY += cellSize) {
                if (mapX < 0 || mapX >= 128 || mapY < 0 || mapY >= 128) continue;
                int idx = mapX + mapY * 128;
                if (idx < 0 || idx >= colors.length) continue;

                int color = colors[idx] & 0xFF;
                if (shouldDebug && color != 0) {
                    TeslaMaps.LOGGER.info("[MapScanner] Vertical gap at ({},{}) color={}", mapX, mapY, color);
                }
                if (color == COLOR_WITHER_DOOR || color == COLOR_BLOOD_DOOR) {
                    // Convert map position to world position
                    double[] worldPos = mapToWorldPosition(mapX, mapY, true);
                    if (worldPos != null) {
                        // Create bounding box for vertical door (wider in X direction)
                        double[] box = new double[]{
                            worldPos[0] - 1.5, 69, worldPos[1] - 1.5,
                            worldPos[0] + 1.5, 73, worldPos[1] + 1.5
                        };
                        if (color == COLOR_WITHER_DOOR) {
                            witherDoorBoxes.add(box);
                        } else {
                            bloodDoorBoxes.add(box);
                        }
                        TeslaMaps.LOGGER.info("[MapScanner] Found {} door at map({},{}) -> world({},{})",
                            color == COLOR_WITHER_DOOR ? "WITHER" : "BLOOD",
                            mapX, mapY, worldPos[0], worldPos[1]);
                    }
                }
            }
        }

        // Scan for horizontal doors (between rooms vertically adjacent)
        for (int mapX = mapCornerX + mapRoomSize / 2; mapX < 128; mapX += cellSize) {
            for (int mapY = mapCornerY + mapRoomSize; mapY < 128; mapY += cellSize) {
                if (mapX < 0 || mapX >= 128 || mapY < 0 || mapY >= 128) continue;
                int idx = mapX + mapY * 128;
                if (idx < 0 || idx >= colors.length) continue;

                int color = colors[idx] & 0xFF;
                if (shouldDebug && color != 0) {
                    TeslaMaps.LOGGER.info("[MapScanner] Horizontal gap at ({},{}) color={}", mapX, mapY, color);
                }
                if (color == COLOR_WITHER_DOOR || color == COLOR_BLOOD_DOOR) {
                    // Convert map position to world position
                    double[] worldPos = mapToWorldPosition(mapX, mapY, false);
                    if (worldPos != null) {
                        // Create bounding box for horizontal door (wider in Z direction)
                        double[] box = new double[]{
                            worldPos[0] - 1.5, 69, worldPos[1] - 1.5,
                            worldPos[0] + 1.5, 73, worldPos[1] + 1.5
                        };
                        if (color == COLOR_WITHER_DOOR) {
                            witherDoorBoxes.add(box);
                        } else {
                            bloodDoorBoxes.add(box);
                        }
                        TeslaMaps.LOGGER.info("[MapScanner] Found {} door at map({},{}) -> world({},{})",
                            color == COLOR_WITHER_DOOR ? "WITHER" : "BLOOD",
                            mapX, mapY, worldPos[0], worldPos[1]);
                    }
                }
            }
        }
    }

    /**
     * Convert map coordinates to world coordinates.
     * Dungeon starts at approximately (-200, -200) and uses 32-block rooms.
     */
    private static double[] mapToWorldPosition(int mapX, int mapY, boolean isVerticalDoor) {
        // The dungeon map represents a 128x128 pixel area
        // Dungeon world coordinates: roughly -200 to -10 (190 blocks)
        // Each map pixel represents approximately 190/128 ≈ 1.48 blocks

        // Find the entrance room position on the grid
        DungeonRoom entranceRoom = null;
        for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
            if (room.getType() == com.teslamaps.map.RoomType.ENTRANCE) {
                entranceRoom = room;
                break;
            }
        }

        if (entranceRoom == null) {
            return null;
        }

        // Get entrance world position
        int[] entranceGrid = entranceRoom.getPrimaryComponent();
        int[] entranceWorld = ComponentGrid.gridToWorld(entranceGrid[0], entranceGrid[1]);

        // Calculate offset from entrance on map
        int entranceMapX = mapCornerX + entranceGrid[0] * (mapRoomSize + mapGapSize) + mapRoomSize / 2;
        int entranceMapY = mapCornerY + entranceGrid[1] * (mapRoomSize + mapGapSize) + mapRoomSize / 2;

        int mapOffsetX = mapX - entranceMapX;
        int mapOffsetY = mapY - entranceMapY;

        // Convert map offset to world offset (each map cell = 32 blocks in world)
        double blocksPerMapPixel = 32.0 / mapRoomSize;
        double worldX = entranceWorld[0] + mapOffsetX * blocksPerMapPixel;
        double worldZ = entranceWorld[1] + mapOffsetY * blocksPerMapPixel;

        return new double[]{worldX, worldZ};
    }

    /**
     * Get wither door bounding boxes for rendering.
     * @return List of [minX, minY, minZ, maxX, maxY, maxZ]
     */
    public static List<double[]> getWitherDoorBoxes() {
        return witherDoorBoxes;
    }

    /**
     * Get blood door bounding boxes for rendering.
     * @return List of [minX, minY, minZ, maxX, maxY, maxZ]
     */
    public static List<double[]> getBloodDoorBoxes() {
        return bloodDoorBoxes;
    }

    /**
     * Get player positions from the map.
     * @return List of [mapX, mapZ, rotation] positions (0-128 range, rotation 0-15)
     */
    public static List<int[]> getMapPlayerPositions() {
        return mapPlayerPositions; // Return directly - no copy needed, list is rebuilt on scan
    }

    /**
     * Get the number of player positions detected.
     */
    public static int getPlayerCount() {
        return mapPlayerPositions.size();
    }

    /**
     * Convert map coordinates (0-128) to grid coordinates (0-5).
     */
    public static int[] mapToGrid(int mapX, int mapZ) {
        // Map layout: corner at (5,5), room size 16, gap 4 (total cell = 20)
        int gridX = (mapX - mapCornerX) / (mapRoomSize + mapGapSize);
        int gridZ = (mapZ - mapCornerY) / (mapRoomSize + mapGapSize);

        // Clamp to grid bounds
        gridX = Math.max(0, Math.min(5, gridX));
        gridZ = Math.max(0, Math.min(5, gridZ));

        return new int[]{gridX, gridZ};
    }

    /**
     * Convert map coordinates to pixel position on the rendered map.
     * @return [pixelX, pixelY] relative to map origin
     */
    public static double[] mapToRenderPosition(int mapX, int mapZ, int roomSize, int doorSize) {
        // Calculate position within the map
        double cellSize = roomSize + doorSize;
        double x = (mapX - mapCornerX) / (double)(mapRoomSize + mapGapSize) * cellSize;
        double z = (mapZ - mapCornerY) / (double)(mapRoomSize + mapGapSize) * cellSize;

        return new double[]{x, z};
    }

    /**
     * Dynamically detect map parameters by finding the Entrance room on the map.
     * The Entrance room is green (color 30) on the map.
     */
    private static void detectMapParameters(byte[] colors) {
        // Find a green pixel (Entrance room color = 30)
        int entranceX = -1, entranceY = -1;

        // Scan the map for green entrance color
        for (int y = 5; y < 123; y++) {
            for (int x = 5; x < 123; x++) {
                int idx = x + y * 128;
                if ((colors[idx] & 0xFF) == 30) {
                    entranceX = x;
                    entranceY = y;
                    break;
                }
            }
            if (entranceX >= 0) break;
        }

        if (entranceX < 0) {
            // No entrance found yet
            return;
        }

        // Find top-left corner of entrance room by going up-left until we hit non-green
        while (entranceX > 0 && (colors[(entranceX - 1) + entranceY * 128] & 0xFF) == 30) {
            entranceX--;
        }
        while (entranceY > 0 && (colors[entranceX + (entranceY - 1) * 128] & 0xFF) == 30) {
            entranceY--;
        }

        // Count room size by going right
        int roomSize = 0;
        while (entranceX + roomSize < 128 && (colors[(entranceX + roomSize) + entranceY * 128] & 0xFF) == 30) {
            roomSize++;
        }

        if (roomSize < 10 || roomSize > 20) {
            // Invalid room size, use defaults
            mapCornerX = 5;
            mapCornerY = 5;
            mapRoomSize = 16;
        } else {
            mapRoomSize = roomSize;

            // Calculate corner based on entrance position and grid position of entrance
            // We need to find where grid [0,0] would be on the map
            DungeonRoom entranceRoom = null;
            for (DungeonRoom room : DungeonManager.getGrid().getAllRooms()) {
                if (room.getType() == com.teslamaps.map.RoomType.ENTRANCE) {
                    entranceRoom = room;
                    break;
                }
            }

            if (entranceRoom != null) {
                int[] entranceGrid = entranceRoom.getPrimaryComponent();
                int cellSize = mapRoomSize + mapGapSize;
                mapCornerX = entranceX - entranceGrid[0] * cellSize;
                mapCornerY = entranceY - entranceGrid[1] * cellSize;
                TeslaMaps.LOGGER.info("[MapScanner] Detected map params: corner=({},{}), roomSize={}, entranceAt=({},{}), grid=[{},{}]",
                        mapCornerX, mapCornerY, mapRoomSize, entranceX, entranceY, entranceGrid[0], entranceGrid[1]);
            } else {
                // No entrance room detected in grid yet, use the found position as corner
                mapCornerX = entranceX;
                mapCornerY = entranceY;
                TeslaMaps.LOGGER.info("[MapScanner] Detected entrance at ({},{}) roomSize={}, waiting for grid",
                        entranceX, entranceY, mapRoomSize);
                return; // Don't mark as detected yet
            }
        }

        mapParamsDetected = true;
    }

    /**
     * Check if a room is adjacent to the Entrance room.
     * Used to avoid false green checkmarks from Entrance bleed.
     */
    private static boolean isAdjacentToEntrance(DungeonRoom room) {
        // Find the Entrance room
        DungeonRoom entrance = null;
        for (DungeonRoom r : DungeonManager.getGrid().getAllRooms()) {
            if (r.getType() == com.teslamaps.map.RoomType.ENTRANCE) {
                entrance = r;
                break;
            }
        }
        if (entrance == null) return false;

        // Check if any component of this room is adjacent to any component of Entrance
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] comp : room.getComponents()) {
            for (int[] entranceComp : entrance.getComponents()) {
                for (int[] offset : offsets) {
                    if (comp[0] + offset[0] == entranceComp[0] && comp[1] + offset[1] == entranceComp[1]) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void reset() {
        tickCounter = 0;
        mapPlayerPositions.clear();
        mapParamsDetected = false;
        mapCornerX = -1;
        mapCornerY = -1;
        roomExplorationScanCount.clear();
        witherDoorBoxes.clear();
        bloodDoorBoxes.clear();
    }
}
