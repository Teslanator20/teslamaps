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

        int rotation = deriveRotation(room); // 0=SOUTH 1=NORTH 2=WEST 3=EAST, -1=unknown
        if (rotation < 0) return;
        int clayX = room.getCornerX();
        int clayZ = room.getCornerZ();

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
        int clayX = room.getCornerX(), clayZ = room.getCornerZ();
        int rot = deriveRotation(room);
        String rotName = switch (rot) { case 0 -> "SOUTH"; case 1 -> "NORTH"; case 2 -> "WEST"; case 3 -> "EAST"; default -> "§cUNKNOWN(-1)"; };
        List<Waypoint> list = byRoom.get(name);

        msg.accept("Room: §e\"" + name + "\"§f | clayPos(corner): §e" + clayX + "," + clayZ + "§f | rotDeg: §e" + room.getRotation() + "§f | derived: §e" + rotName);
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

    // Rotations enum offsets (Odin): NORTH(15,15) SOUTH(-15,-15) WEST(15,-15) EAST(-15,15)
    private static final int[][] ROT_OFFSETS = {{-15, -15}, {15, 15}, {15, -15}, {-15, 15}}; // SOUTH,NORTH,WEST,EAST

    /** Determine Odin's rotation from which component-corner the terracotta (clayPos) sits at. */
    private static int deriveRotation(DungeonRoom room) {
        int clayX = room.getCornerX();
        int clayZ = room.getCornerZ();
        for (int[] comp : room.getComponents()) {
            int[] corner = ComponentGrid.gridToWorldCorner(comp[0], comp[1]);
            int cx = corner[0] + ComponentGrid.HALF_ROOM_SIZE; // component center
            int cz = corner[1] + ComponentGrid.HALF_ROOM_SIZE;
            for (int r = 0; r < ROT_OFFSETS.length; r++) {
                if (clayX == cx + ROT_OFFSETS[r][0] && clayZ == cz + ROT_OFFSETS[r][1]) return r;
            }
        }
        return -1;
    }
}
