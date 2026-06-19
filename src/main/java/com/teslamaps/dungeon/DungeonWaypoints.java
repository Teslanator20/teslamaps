package com.teslamaps.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.scanner.ComponentGrid;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads Odin-format dungeon waypoint files (Map&lt;RoomName, List&lt;Waypoint&gt;&gt;) and renders them.
 *
 * Odin stores waypoints relative to the room's blue-terracotta block (clayPos), rotated into a
 * canonical orientation. teslamaps already detects the same room name and the same terracotta
 * corner, so we just re-apply Odin's exact rotateAroundNorth transform:
 *   world = rotateAroundNorth(rotation, relativeXZ) + clayPos   (y stays absolute).
 */
public class DungeonWaypoints {

    public record Waypoint(double rx, double ry, double rz, int colorArgb, boolean filled, boolean depth,
                           double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    private static final Map<String, List<Waypoint>> byRoom = new HashMap<>();

    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("teslamaps").resolve("dungeon_waypoints.json");

    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        byRoom.clear();
        if (!Files.exists(FILE)) {
            TeslaMaps.LOGGER.info("[Waypoints] No dungeon_waypoints.json found at {}", FILE);
            return;
        }
        try (Reader r = Files.newBufferedReader(FILE)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            int count = 0;
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonArray arr = e.getValue().getAsJsonArray();
                List<Waypoint> list = new ArrayList<>();
                for (JsonElement el : arr) {
                    Waypoint wp = parse(el.getAsJsonObject());
                    if (wp != null) { list.add(wp); count++; }
                }
                if (!list.isEmpty()) byRoom.put(e.getKey(), list);
            }
            TeslaMaps.LOGGER.info("[Waypoints] Loaded {} waypoints across {} rooms", count, byRoom.size());
        } catch (Exception ex) {
            TeslaMaps.LOGGER.error("[Waypoints] Failed to load", ex);
        }
    }

    private static Waypoint parse(JsonObject o) {
        try {
            double x = o.has("x") ? o.get("x").getAsDouble() : o.getAsJsonObject("blockPos").get("x").getAsDouble();
            double y = o.has("y") ? o.get("y").getAsDouble() : o.getAsJsonObject("blockPos").get("y").getAsDouble();
            double z = o.has("z") ? o.get("z").getAsDouble() : o.getAsJsonObject("blockPos").get("z").getAsDouble();
            int color = parseColor(o.has("color") ? o.get("color").getAsString() : "#FFFFFFFF");
            boolean filled = o.has("filled") && o.get("filled").getAsBoolean();
            boolean depth = !o.has("depth") || o.get("depth").getAsBoolean();

            // AABB: current "minX..maxZ" or legacy obfuscated field names
            double[] b = readAabb(o.has("aabb") ? o.getAsJsonObject("aabb") : null);
            return new Waypoint(x, y, z, color, filled, depth, b[0], b[1], b[2], b[3], b[4], b[5]);
        } catch (Exception ex) {
            return null;
        }
    }

    private static double[] readAabb(JsonObject a) {
        if (a == null) return new double[]{0, 0, 0, 1, 1, 1};
        if (a.has("minX")) return new double[]{a.get("minX").getAsDouble(), a.get("minY").getAsDouble(), a.get("minZ").getAsDouble(),
                a.get("maxX").getAsDouble(), a.get("maxY").getAsDouble(), a.get("maxZ").getAsDouble()};
        if (a.has("field_72340_a")) return new double[]{a.get("field_72340_a").getAsDouble(), a.get("field_72338_b").getAsDouble(), a.get("field_72339_c").getAsDouble(),
                a.get("field_72336_d").getAsDouble(), a.get("field_72337_e").getAsDouble(), a.get("field_72334_f").getAsDouble()};
        if (a.has("field_1323")) return new double[]{a.get("field_1323").getAsDouble(), a.get("field_1322").getAsDouble(), a.get("field_1321").getAsDouble(),
                a.get("field_1320").getAsDouble(), a.get("field_1325").getAsDouble(), a.get("field_1324").getAsDouble()};
        return new double[]{0, 0, 0, 1, 1, 1};
    }

    /** Parse "#RRGGBBAA" (Odin format, alpha last) to ARGB int. */
    private static int parseColor(String s) {
        s = s.replace("#", "");
        try {
            long v = Long.parseLong(s, 16);
            if (s.length() == 8) {
                int r = (int) ((v >> 24) & 0xFF), g = (int) ((v >> 16) & 0xFF), b = (int) ((v >> 8) & 0xFF), a = (int) (v & 0xFF);
                return (a << 24) | (r << 16) | (g << 8) | b;
            }
            return 0xFF000000 | (int) (v & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return 0xFFFFFFFF;
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (!TeslaMapsConfig.get().dungeonWaypoints || byRoom.isEmpty()) return;
        if (!DungeonManager.isInDungeon()) return;

        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null) return;
        List<Waypoint> list = byRoom.get(room.getName());
        if (list == null || list.isEmpty()) return;

        int[] clay = detectClay(room); // {clayX, clayZ, rotation}, or null if terracotta not found yet
        if (clay == null) return;
        int clayX = clay[0], clayZ = clay[1], rotation = clay[2];

        for (Waypoint wp : list) {
            double[] rot = rotateAroundNorth(rotation, wp.rx(), wp.rz());
            double wx = rot[0] + clayX;
            double wz = rot[1] + clayZ;
            double wy = wp.ry();
            AABB box = new AABB(wx + wp.minX(), wy + wp.minY(), wz + wp.minZ(),
                    wx + wp.maxX(), wy + wp.maxY(), wz + wp.maxZ());
            if (wp.filled()) {
                ESPRenderer.drawFilledBox(matrices, box, wp.colorArgb(), cameraPos);
            } else {
                ESPRenderer.drawBoxOutline(matrices, box, wp.colorArgb(), 2.0f, cameraPos);
            }
        }
    }

    /** Prints diagnostics for the current room so a failing room can be reported. */
    public static void debug() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        java.util.function.Consumer<String> msg = s ->
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b[WP] §f" + s));

        if (!DungeonManager.isInDungeon()) { msg.accept("§cNot in a dungeon."); return; }
        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null) { msg.accept("§cNo current room detected."); return; }

        String name = room.getName();
        int[] clay = detectClay(room); // scanned terracotta {x,z,rot}
        int clayX = clay != null ? clay[0] : room.getCornerX();
        int clayZ = clay != null ? clay[1] : room.getCornerZ();
        int rot = clay != null ? clay[2] : -1;
        String rotName = switch (rot) { case 0 -> "SOUTH"; case 1 -> "NORTH"; case 2 -> "WEST"; case 3 -> "EAST"; default -> "§cNO TERRACOTTA FOUND"; };
        List<Waypoint> list = byRoom.get(name);

        msg.accept("Room: §e\"" + name + "\"§f | scanned clayPos: §e" + clayX + "," + clayZ + "§f | rot: §e" + rotName + "§f | (teslamaps corner: §7" + room.getCornerX() + "," + room.getCornerZ() + "§f)");
        msg.accept("Waypoints in file for this name: " + (list == null ? "§cNONE (name not a key in your file)" : "§a" + list.size()));

        StringBuilder comps = new StringBuilder();
        for (int[] c : room.getComponents()) {
            int[] corner = ComponentGrid.gridToWorldCorner(c[0], c[1]);
            comps.append("[grid ").append(c[0]).append(",").append(c[1]).append(" center ")
                    .append(corner[0] + ComponentGrid.HALF_ROOM_SIZE).append(",").append(corner[1] + ComponentGrid.HALF_ROOM_SIZE).append("] ");
        }
        msg.accept("Components: §7" + comps);

        // Player position -> relative coords (rotateToNorth(world - clay)); compare to your file values
        net.minecraft.core.BlockPos p = mc.player.blockPosition();
        if (rot >= 0) {
            double[] rel = rotateToNorth(rot, p.getX() - clayX, p.getZ() - clayZ);
            msg.accept("You stand at world §e" + p.getX() + "," + p.getY() + "," + p.getZ()
                    + "§f -> relative §e" + (int) rel[0] + "," + p.getY() + "," + (int) rel[1]
                    + "§7 (this is what would be stored for a waypoint here)");
        }
    }

    /** Add a waypoint at the block you're looking at (or under your feet) in the current room. */
    public static String addAtTarget(int colorArgb, boolean filled) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return "§cNo player.";
        if (!DungeonManager.isInDungeon()) return "§cNot in a dungeon.";
        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getName() == null) return "§cNo current room detected.";
        int[] clay = detectClay(room);
        if (clay == null) return "§cCouldn't find the room's blue-terracotta marker yet — move into the room.";

        net.minecraft.core.BlockPos target = (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr)
                ? bhr.getBlockPos() : mc.player.blockPosition().below();

        double[] rel = rotateToNorth(clay[2], target.getX() - clay[0], target.getZ() - clay[1]);
        byRoom.computeIfAbsent(room.getName(), k -> new ArrayList<>())
                .add(new Waypoint(rel[0], target.getY(), rel[1], colorArgb, filled, true, 0, 0, 0, 1, 1, 1));
        save();
        int n = byRoom.get(room.getName()).size();
        return "§aAdded waypoint #" + n + " in §e\"" + room.getName() + "\"§a @ "
                + target.getX() + "," + target.getY() + "," + target.getZ();
    }

    /** Remove the waypoint nearest the player in the current room. */
    public static String removeNearest() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return "§cNo player.";
        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getName() == null) return "§cNo current room detected.";
        List<Waypoint> list = byRoom.get(room.getName());
        if (list == null || list.isEmpty()) return "§eNo waypoints in \"" + room.getName() + "\".";
        int[] clay = detectClay(room);
        if (clay == null) return "§cCouldn't find the room marker yet.";

        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            Waypoint wp = list.get(i);
            double[] rot = rotateAroundNorth(clay[2], wp.rx(), wp.rz());
            double dx = rot[0] + clay[0] + 0.5 - px, dy = wp.ry() + 0.5 - py, dz = rot[1] + clay[1] + 0.5 - pz;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) { bestD = d; best = i; }
        }
        list.remove(best);
        if (list.isEmpty()) byRoom.remove(room.getName());
        save();
        return "§aRemoved the nearest waypoint in \"" + room.getName() + "\" (" + list.size() + " left).";
    }

    /** Remove all waypoints for the current room. */
    public static String clearRoom() {
        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getName() == null) return "§cNo current room detected.";
        List<Waypoint> removed = byRoom.remove(room.getName());
        save();
        return removed == null ? "§eNo waypoints to clear in \"" + room.getName() + "\"."
                : "§aCleared " + removed.size() + " waypoint(s) in \"" + room.getName() + "\".";
    }

    /** Write byRoom back to the Odin-format JSON file (backs up the original once before first write). */
    private static void save() {
        try {
            Path bak = FILE.resolveSibling("dungeon_waypoints.json.bak");
            if (Files.exists(FILE) && !Files.exists(bak)) Files.copy(FILE, bak);

            JsonObject root = new JsonObject();
            for (Map.Entry<String, List<Waypoint>> e : byRoom.entrySet()) {
                JsonArray arr = new JsonArray();
                for (Waypoint wp : e.getValue()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("x", wp.rx());
                    o.addProperty("y", wp.ry());
                    o.addProperty("z", wp.rz());
                    o.addProperty("color", toHex(wp.colorArgb()));
                    o.addProperty("filled", wp.filled());
                    o.addProperty("depth", wp.depth());
                    JsonObject ab = new JsonObject();
                    ab.addProperty("minX", wp.minX()); ab.addProperty("minY", wp.minY()); ab.addProperty("minZ", wp.minZ());
                    ab.addProperty("maxX", wp.maxX()); ab.addProperty("maxY", wp.maxY()); ab.addProperty("maxZ", wp.maxZ());
                    o.add("aabb", ab);
                    arr.add(o);
                }
                root.add(e.getKey(), arr);
            }
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root));
        } catch (Exception ex) {
            TeslaMaps.LOGGER.error("[Waypoints] Failed to save", ex);
        }
    }

    /** ARGB int -> Odin "#RRGGBBAA" (alpha last), round-trips with parseColor. */
    private static String toHex(int argb) {
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return String.format("#%02X%02X%02X%02X", r, g, b, a);
    }

    /** Inverse of rotateAroundNorth (Odin rotateToNorth) on x/z. */
    private static double[] rotateToNorth(int rotation, double x, double z) {
        return switch (rotation) {
            case 1 -> new double[]{-x, -z};  // NORTH
            case 2 -> new double[]{z, -x};   // WEST
            case 3 -> new double[]{-z, x};   // EAST
            default -> new double[]{x, z};   // SOUTH
        };
    }

    /** Odin rotateAroundNorth on the relative x/z. */
    private static double[] rotateAroundNorth(int rotation, double x, double z) {
        return switch (rotation) {
            case 1 -> new double[]{-x, -z};  // NORTH
            case 2 -> new double[]{-z, x};   // WEST
            case 3 -> new double[]{z, -x};   // EAST
            default -> new double[]{x, z};   // SOUTH (identity)
        };
    }

    // Rotations enum offsets (Odin): SOUTH(-15,-15) NORTH(15,15) WEST(15,-15) EAST(-15,15), indexed by rotation code
    private static final int[][] ROT_OFFSETS = {{-15, -15}, {15, 15}, {15, -15}, {-15, 15}}; // 0=SOUTH 1=NORTH 2=WEST 3=EAST

    private static int[] cachedClay = null;     // {clayX, clayZ, rotation}
    private static String cacheKey = "";

    /** Public, uncached scan for a room's clayPos + rotation ({x, z, rot}), or null if not found yet. */
    public static int[] scanClayPos(DungeonRoom room) {
        return scanClay(room);
    }

    /**
     * Odin's getRealCoords: rotate clayPos-relative x/z into world coords using a scanned clay {x,z,rot}.
     * Mirrors {@code pos.rotateAroundNorth(rotation).offset(clayPos.x, 0, clayPos.z)}.
     */
    public static int[] relativeToWorld(int[] clay, int relX, int relZ) {
        double[] rot = rotateAroundNorth(clay[2], relX, relZ);
        return new int[]{(int) Math.round(rot[0]) + clay[0], (int) Math.round(rot[1]) + clay[1]};
    }

    /** clayPos + rotation, cached per room. Re-scans each frame until the terracotta is found. */
    static int[] detectClay(DungeonRoom room) {
        List<int[]> comps = room.getComponents();
        if (comps.isEmpty()) return null;
        String key = room.getName() + "@" + comps.get(0)[0] + "," + comps.get(0)[1] + ":" + comps.size();
        if (!key.equals(cacheKey)) { cacheKey = key; cachedClay = null; } // room changed -> invalidate
        if (cachedClay == null) cachedClay = scanClay(room);
        return cachedClay;
    }

    /**
     * Finds the room's blue-terracotta marker (Odin's clayPos) by checking each component's four
     * diagonal corners across a Y range. teslamaps' own corner is unreliable for multi-component
     * rooms (it falls back to a geometric corner), so we scan for the real block to match Odin.
     */
    private static int[] scanClay(DungeonRoom room) {
        net.minecraft.world.level.Level level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return null;
        for (int[] comp : room.getComponents()) {
            int[] corner = ComponentGrid.gridToWorldCorner(comp[0], comp[1]);
            int cx = corner[0] + ComponentGrid.HALF_ROOM_SIZE;
            int cz = corner[1] + ComponentGrid.HALF_ROOM_SIZE;
            for (int r = 0; r < ROT_OFFSETS.length; r++) {
                int bx = cx + ROT_OFFSETS[r][0];
                int bz = cz + ROT_OFFSETS[r][1];
                net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
                for (int y = 150; y >= 11; y--) {
                    if (level.getBlockState(m.set(bx, y, bz)).is(net.minecraft.world.level.block.Blocks.BLUE_TERRACOTTA)) {
                        return new int[]{bx, bz, r};
                    }
                }
            }
        }
        return null;
    }
}
