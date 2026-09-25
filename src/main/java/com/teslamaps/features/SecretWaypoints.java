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
        if (TeslaMapsConfig.get().secretWaypointEvents && type != WaypointType.REDSTONE) return false;
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

        // Keep collected secrets for the whole run (positions are absolute world coords, unique per room),
        // so re-entering a room doesn't make already-found secrets reappear. Cleared on dungeon exit (reset()).
        lastRoomId = roomId;

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
                if ((type == WaypointType.ESSENCE || type == WaypointType.REDSTONE) && TeslaMapsConfig.get().secretWaypointAdaptiveBox) {
                    box = fitBox(mc.level, worldPos, box);
                }
                // redstone-key skulls are easy to miss: thicker outline through walls so they pop
                float width = type == WaypointType.REDSTONE ? 3.0f : 2.0f;
                drawStyled(matrices, box, worldPos, color, width, cameraPos);

                if (TeslaMapsConfig.get().secretWaypointTracers) {
                    Vec3 target = new Vec3(worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5);
                    ESPRenderer.drawTracerFromCamera(matrices, target, color, cameraPos);
                }
            }
        }
    }

    public static final String[] STYLES = {"Outline", "Highlight", "Outline + Highlight", "Beacon"};
    // Skyblocker SecretWaypoint.Category (route helpers; secrets themselves come from our own types)
    public static final String[] ROUTE_CATEGORIES = {"entrance", "superboom", "stonk", "aotv", "pearl", "lever", "fairysoul", "prince", "default"};
    private static final String[] ROUTE_NAMES = {"Entrance", "Superboom", "Stonk", "AOTV", "Pearl", "Lever", "Fairy Soul", "Prince", "Other"};
    private static final String[] ROUTE_COLORS = {"00FF00", "FF0000", "9234EB", "FC6203", "39757D", "FAD902", "FF55FF", "85150D", "BEFFFC"};

    public static String routeName(String id) {
        for (int i = 0; i < ROUTE_CATEGORIES.length; i++) if (ROUTE_CATEGORIES[i].equals(id)) return ROUTE_NAMES[i];
        return id;
    }

    public static String routeDefaultColor(String id) {
        for (int i = 0; i < ROUTE_CATEGORIES.length; i++) if (ROUTE_CATEGORIES[i].equals(id)) return ROUTE_COLORS[i];
        return "FFFFFF";
    }

    private static final Map<Integer, int[]> roomTransform = new HashMap<>();
    private static Map<String, List<RouteRow>> routeData = null;
    private static final Map<Integer, List<RoutePoint>> routeCache = new HashMap<>();

    private record RouteRow(int index, String category, int x, int y, int z, String name) {}
    private record RoutePoint(int index, String category, BlockPos pos, String name) {}
    private record Label(Vec3 pos, String text, int color) {}
    private record RouteBox(BlockPos pos, int color) {}

    private static volatile List<Label> labels = List.of();
    private static volatile List<RouteBox> routeBoxes = List.of();

    private static void loadRoutes() {
        routeData = new HashMap<>();
        try (InputStream is = SecretWaypoints.class.getResourceAsStream("/assets/teslamaps/data/secret_routes.json")) {
            if (is == null) return;
            Type t = new TypeToken<Map<String, List<List<Object>>>>(){}.getType();
            Map<String, List<List<Object>>> raw = GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), t);
            for (var e : raw.entrySet()) {
                List<RouteRow> rows = new ArrayList<>();
                for (List<Object> r : e.getValue()) {
                    rows.add(new RouteRow(((Number) r.get(0)).intValue(), (String) r.get(1), ((Number) r.get(2)).intValue(),
                            ((Number) r.get(3)).intValue(), ((Number) r.get(4)).intValue(), (String) r.get(5)));
                }
                routeData.put(e.getKey().toLowerCase(Locale.ROOT), rows);
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load secret routes", e);
        }
    }

    private static List<RoutePoint> routePoints(DungeonRoom room, int roomId) {
        List<RoutePoint> cached = routeCache.get(roomId);
        if (cached != null) return cached;
        int[] tr = roomTransform.get(roomId);
        if (tr == null) return null;
        if (routeData == null) loadRoutes();
        List<RouteRow> rows = routeData.get(room.getRoomData().getName().toLowerCase(Locale.ROOT));
        List<RoutePoint> out = new ArrayList<>();
        if (rows != null) {
            for (RouteRow r : rows) {
                int[] xz = fromComp(r.x(), r.z(), tr[0], tr[1], tr[2]);
                out.add(new RoutePoint(r.index(), r.category(), new BlockPos(xz[0], r.y(), xz[1]), r.name()));
            }
        }
        routeCache.put(roomId, out);
        return out;
    }

    private static boolean secretCategory(String c) {
        return c.equals("chest") || c.equals("item") || c.equals("bat") || c.equals("wither") || c.equals("key");
    }

    private static String typeName(WaypointType t) {
        return switch (t) {
            case CHEST -> "Chest";
            case ITEM -> "Item";
            case BAT -> "Bat";
            case ESSENCE -> "Wither Essence";
            case REDSTONE -> "Redstone Key";
        };
    }

    // labels + route boxes are built here so the render pass only draws
    private static void tickRoutes() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Minecraft mc = Minecraft.getInstance();
        DungeonRoom room = DungeonManager.isInDungeon() && loaded ? DungeonManager.getCurrentRoom() : null;
        if (room == null || room.getRoomData() == null || mc.player == null) {
            labels = List.of();
            routeBoxes = List.of();
            return;
        }
        int roomId = room.getRoomData().getRoomID();
        Map<WaypointType, List<int[]>> world = cachedWaypoints.get(roomId);
        List<RoutePoint> points = world != null ? routePoints(room, roomId) : null;
        if (world == null || points == null) {
            labels = List.of();
            routeBoxes = List.of();
            return;
        }
        Set<Integer> done = new HashSet<>();
        for (RoutePoint p : points) {
            if (p.index() > 0 && secretCategory(p.category()) && foundSecrets.contains(p.pos())) done.add(p.index());
        }
        Vec3 eye = mc.player.position();
        List<Label> ls = new ArrayList<>();
        if (c.secretWaypointLabels) {
            for (Map.Entry<WaypointType, List<int[]>> e : world.entrySet()) {
                if (!isTypeEnabled(e.getKey())) continue;
                int color = getColorForType(e.getKey());
                for (int[] p : e.getValue()) {
                    BlockPos bp = new BlockPos(p[0], p[1], p[2]);
                    if (foundSecrets.contains(bp)) continue;
                    String name = typeName(e.getKey());
                    for (RoutePoint rp : points) {
                        if (rp.pos().equals(bp) && secretCategory(rp.category()) && !rp.name().isEmpty()) { name = rp.name(); break; }
                    }
                    ls.add(label(bp, name, color, eye, c));
                }
            }
        }
        List<RouteBox> boxes = new ArrayList<>();
        if (c.secretWaypointRoutes) {
            for (RoutePoint rp : points) {
                if (secretCategory(rp.category()) || !c.secretRouteCategories.contains(rp.category())) continue;
                if (rp.index() > 0 && done.contains(rp.index())) continue;
                if (rp.category().equals("prince") && com.teslamaps.dungeon.DungeonScore.isPrinceKilled()) continue;
                int color = TeslaMapsConfig.parseColor(c.secretRouteColors.getOrDefault(rp.category(), routeDefaultColor(rp.category())));
                boxes.add(new RouteBox(rp.pos(), color));
                if (c.secretWaypointLabels) ls.add(label(rp.pos(), rp.name().isEmpty() ? routeName(rp.category()) : rp.name(), color, eye, c));
            }
        }
        labels = ls;
        routeBoxes = boxes;
    }

    private static Label label(BlockPos bp, String name, int color, Vec3 eye, TeslaMapsConfig c) {
        Vec3 at = new Vec3(bp.getX() + 0.5, bp.getY() + 1.5, bp.getZ() + 0.5);
        String text = c.secretWaypointDistance ? name + " §7(" + (int) Math.round(at.distanceTo(eye)) + "m)" : name;
        return new Label(at, text, color | 0xFF000000);
    }

    private static void renderRoutes(PoseStack matrices, Vec3 cameraPos) {
        for (RouteBox rb : routeBoxes) drawStyled(matrices, new AABB(rb.pos()), rb.pos(), rb.color(), 2.0f, cameraPos);
        for (Label l : labels) ESPRenderer.drawText(matrices, l.text(), l.pos(), 1.0f, cameraPos, true, true, l.color());
    }

    private static void drawStyled(PoseStack matrices, AABB box, BlockPos pos, int color, float width, Vec3 cameraPos) {
        switch (TeslaMapsConfig.get().secretWaypointStyle) {
            case "Highlight" -> ESPRenderer.drawFilledBox(matrices, box, (color & 0x00FFFFFF) | 0x60000000, cameraPos, true);
            case "Outline + Highlight" -> {
                ESPRenderer.drawFilledBox(matrices, box, (color & 0x00FFFFFF) | 0x60000000, cameraPos, true);
                ESPRenderer.drawBoxOutline(matrices, box, color, width, cameraPos, true);
            }
            case "Beacon" -> {
                ESPRenderer.drawBoxOutline(matrices, box, color, width, cameraPos, true);
                ESPRenderer.drawBeaconBeam(matrices, pos, color, cameraPos);
            }
            default -> ESPRenderer.drawBoxOutline(matrices, box, color, width, cameraPos, true);
        }
    }

    // Skyblocker adaptiveBoxSize: skull shape instead of a full block
    private static AABB fitBox(ClientLevel level, BlockPos pos, AABB fallback) {
        var shape = level.getBlockState(pos).getShape(level, pos);
        if (shape.isEmpty()) return fallback;
        return shape.bounds().move(pos);
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
        roomTransform.put(roomId, rotationAndCorner);

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

        TeslaMaps.debug("SecretWaypoints: Room '{}' rotation={} corner=[{},{}]",
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

    // Skyblocker markAsFound / markAsMissing: 1..n in the current room, EnumMap order
    private static List<BlockPos> currentRoomList(List<String> types) {
        Minecraft mc = Minecraft.getInstance();
        DungeonRoom room = DungeonManager.isInDungeon() ? DungeonManager.getCurrentRoom() : null;
        if (mc.level == null || room == null || room.getRoomData() == null) return null;
        if (!loaded) load();
        int roomId = room.getRoomData().getRoomID();
        Map<WaypointType, List<int[]>> wps = cachedWaypoints.get(roomId);
        if (wps == null) {
            wps = addSecretsForRoom(room, mc.level);
            if (wps == null) return null;
            cachedWaypoints.put(roomId, wps);
        }
        List<BlockPos> out = new ArrayList<>();
        for (Map.Entry<WaypointType, List<int[]>> e : wps.entrySet()) {
            for (int[] p : e.getValue()) {
                out.add(new BlockPos(p[0], p[1], p[2]));
                if (types != null) types.add(e.getKey().key);
            }
        }
        return out;
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> command() {
        var root = net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("secret");
        root.executes(c -> listSecrets(c.getSource()));
        root.then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("list").executes(c -> listSecrets(c.getSource())));
        for (boolean found : new boolean[]{true, false}) {
            String name = found ? "found" : "missing";
            root.then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal(name)
                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("all")
                            .executes(c -> mark(c.getSource(), 0, found)))
                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("index",
                                    com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                            .suggests((c, b) -> {
                                List<BlockPos> list = currentRoomList(null);
                                if (list != null) for (int i = 1; i <= list.size(); i++) b.suggest(i);
                                return b.buildFuture();
                            })
                            .executes(c -> mark(c.getSource(),
                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "index"), found))));
        }
        return root;
    }

    private static int listSecrets(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource src) {
        List<String> types = new ArrayList<>();
        List<BlockPos> list = currentRoomList(types);
        if (list == null || list.isEmpty()) {
            src.sendFeedback(net.minecraft.network.chat.Component.literal("§c[TeslaMaps] No secret waypoints for this room"));
            return 0;
        }
        src.sendFeedback(net.minecraft.network.chat.Component.literal("§7[TeslaMaps] Secrets in §f" + DungeonManager.getCurrentRoom().getName() + "§7:"));
        for (int i = 0; i < list.size(); i++) {
            BlockPos p = list.get(i);
            boolean done = foundSecrets.contains(p);
            src.sendFeedback(net.minecraft.network.chat.Component.literal("§e" + (i + 1) + ". §f" + types.get(i)
                    + " §7(" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ") " + (done ? "§afound" : "§cmissing")));
        }
        return 1;
    }

    private static int mark(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource src, int index, boolean found) {
        List<BlockPos> list = currentRoomList(null);
        if (list == null || list.isEmpty()) {
            src.sendFeedback(net.minecraft.network.chat.Component.literal("§c[TeslaMaps] No secret waypoints for this room"));
            return 0;
        }
        if (index > list.size()) {
            src.sendFeedback(net.minecraft.network.chat.Component.literal("§c[TeslaMaps] This room has " + list.size() + " secrets"));
            return 0;
        }
        List<BlockPos> targets = index == 0 ? list : List.of(list.get(index - 1));
        for (BlockPos p : targets) {
            if (found) foundSecrets.add(p);
            else foundSecrets.remove(p);
        }
        src.sendFeedback(net.minecraft.network.chat.Component.literal("§7[TeslaMaps] Marked " + (index == 0 ? "all" : "#" + index)
                + " as " + (found ? "§afound" : "§cmissing")));
        return 1;
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
                if (foundSecrets.add(waypointPos)) {
                    lastChest = waypointPos;
                    lastChestTime = System.currentTimeMillis();
                }
                TeslaMaps.LOGGER.debug("Chest secret collected at {}", waypointPos);
            }
        }
    }

    private static BlockPos lastChest = null;
    private static long lastChestTime = 0;

    public static void register() {
        com.teslamaps.registry.Feature.of("SecretWaypointsChat").section("Secrets & Waypoints")
                .when(() -> TeslaMapsConfig.get().secretWaypoints && TeslaMapsConfig.get().secretWaypointHideCollected)
                .serverChat(SecretWaypoints::onChat);
        com.teslamaps.registry.Feature.of("SecretWaypointsRoutes").section("Secrets & Waypoints")
                .when(() -> TeslaMapsConfig.get().secretWaypoints
                        && (TeslaMapsConfig.get().secretWaypointLabels || TeslaMapsConfig.get().secretWaypointRoutes))
                .tick(SecretWaypoints::tickRoutes)
                .world(SecretWaypoints::renderRoutes);
    }

    // Devonian DungeonWaypoints: a locked chest was not looted, bring its waypoint back
    private static void onChat(String message) {
        if (lastChest == null || !"That chest is locked!".equals(com.teslamaps.utils.StringUtil.stripTrim(message))) return;
        if (System.currentTimeMillis() - lastChestTime < 1500) foundSecrets.remove(lastChest);
        lastChest = null;
    }

    private static boolean eventsActive() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.secretWaypoints && c.secretWaypointHideCollected && c.secretWaypointEvents
                && DungeonManager.isInDungeon() && !DungeonManager.isInBossArea();
    }

    // Odin SecretWaypoints.clickSecret: exact block for clicks, nearest within distSq for bats/items
    private static void clickSecret(BlockPos pos, int distSq, WaypointType... types) {
        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getRoomData() == null) return;
        Map<WaypointType, List<int[]>> worldWaypoints = cachedWaypoints.get(room.getRoomData().getRoomID());
        if (worldWaypoints == null) return;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (WaypointType type : types) {
            List<int[]> list = worldWaypoints.get(type);
            if (list == null) continue;
            for (int[] p : list) {
                BlockPos wp = new BlockPos(p[0], p[1], p[2]);
                if (foundSecrets.contains(wp)) continue;
                double d = wp.distSqr(pos);
                if (d <= distSq && d < bestDist) { best = wp; bestDist = d; }
            }
        }
        if (best != null) {
            foundSecrets.add(best);
            TeslaMaps.LOGGER.debug("Secret event at {} -> {}", pos, best);
        }
    }

    public static void onSkullClick(BlockPos pos) {
        if (eventsActive()) clickSecret(pos, 1, WaypointType.ESSENCE, WaypointType.REDSTONE);
    }

    public static void onSecretItem(ItemEntity item) {
        if (eventsActive()) clickSecret(item.blockPosition(), 3, WaypointType.ITEM);
    }

    public static void onSound(net.minecraft.network.protocol.game.ClientboundSoundPacket packet) {
        if (!eventsActive()) return;
        var sound = packet.getSound().value();
        if ((sound == SoundEvents.BAT_HURT || sound == SoundEvents.BAT_DEATH) && packet.getVolume() == 0.1f) {
            clickSecret(BlockPos.containing(packet.getX(), packet.getY(), packet.getZ()), 5, WaypointType.BAT);
        }
    }

    public static void reset() {
        foundSecrets.clear();
        cachedWaypoints.clear();
        roomTransform.clear();
        routeCache.clear();
        labels = List.of();
        routeBoxes = List.of();
        visitedWaypoints.clear();
        lastChest = null;
        lastRoomId = -1;
        lastCheckTime = 0;
    }

    public static void onSecretInteract(String type) {
        onSecretInteract(type, null);
    }

    public static void onSecretInteract(String type, BlockPos pos) {
        if (!TeslaMapsConfig.get().secretWaypoints) return;
        if (!TeslaMapsConfig.get().secretSound) return;
        if (!DungeonManager.isInDungeon()) return;
        if (!SecretSounds.counts(type, pos)) return;
        if (SecretSounds.lever(type)) return;

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
