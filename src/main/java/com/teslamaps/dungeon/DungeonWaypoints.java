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

public class DungeonWaypoints {

    public record Waypoint(double rx, double ry, double rz, int colorArgb, boolean filled, boolean depth,
                           double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    private static final Map<String, List<Waypoint>> byRoom = new HashMap<>();

    private static final String ABSOLUTE_KEY = "__absolute__";

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

        List<Waypoint> abs = byRoom.get(ABSOLUTE_KEY);
        if (abs != null) {
            for (Waypoint wp : abs) drawWaypoint(matrices, cameraPos, wp, wp.rx(), wp.ry(), wp.rz());
        }

        if (!DungeonManager.isInDungeon()) return;
        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null) return;
        List<Waypoint> list = byRoom.get(room.getName());
        if (list == null || list.isEmpty()) return;
        int[] clay = detectClay(room); // {clayX, clayZ, rotation}, or null if terracotta not found yet
        if (clay == null) return;

        for (Waypoint wp : list) {
            double[] rot = rotateAroundNorth(clay[2], wp.rx(), wp.rz());
            drawWaypoint(matrices, cameraPos, wp, rot[0] + clay[0], wp.ry(), rot[1] + clay[1]);
        }
    }

    private static void drawWaypoint(PoseStack matrices, Vec3 cameraPos, Waypoint wp, double wx, double wy, double wz) {
        AABB box = new AABB(wx + wp.minX(), wy + wp.minY(), wz + wp.minZ(),
                wx + wp.maxX(), wy + wp.maxY(), wz + wp.maxZ());
        if (wp.filled()) {
            ESPRenderer.drawFilledBox(matrices, box, wp.colorArgb(), cameraPos, wp.depth());
        } else {
            ESPRenderer.drawBoxOutline(matrices, box, wp.colorArgb(), 2.0f, cameraPos, wp.depth());
        }
    }

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

        net.minecraft.core.BlockPos p = mc.player.blockPosition();
        if (rot >= 0) {
            double[] rel = rotateToNorth(rot, p.getX() - clayX, p.getZ() - clayZ);
            msg.accept("You stand at world §e" + p.getX() + "," + p.getY() + "," + p.getZ()
                    + "§f -> relative §e" + (int) rel[0] + "," + p.getY() + "," + (int) rel[1]
                    + "§7 (this is what would be stored for a waypoint here)");
        }
    }

    public static String addAtTarget(int colorArgb, boolean filled, boolean depth) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return "§cNo player.";

        net.minecraft.core.BlockPos target = (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr)
                ? bhr.getBlockPos() : mc.player.blockPosition().below();

        DungeonRoom room = DungeonManager.isInDungeon() ? DungeonManager.getCurrentRoom() : null;
        int[] clay = (room != null && room.getName() != null) ? detectClay(room) : null;

        String coords = target.getX() + "," + target.getY() + "," + target.getZ();

        if (room != null && room.getName() != null && clay != null) {
            double[] rel = rotateToNorth(clay[2], target.getX() - clay[0], target.getZ() - clay[1]);
            List<Waypoint> list = byRoom.computeIfAbsent(room.getName(), k -> new ArrayList<>());
            if (removeAt(list, room.getName(), rel[0], target.getY(), rel[1]))
                return "§eRemoved waypoint in §e\"" + room.getName() + "\"§e @ " + coords;
            list.add(new Waypoint(rel[0], target.getY(), rel[1], colorArgb, filled, depth, 0, 0, 0, 1, 1, 1));
            save();
            return "§aAdded waypoint in §e\"" + room.getName() + "\"§a @ " + coords;
        }

        List<Waypoint> list = byRoom.computeIfAbsent(ABSOLUTE_KEY, k -> new ArrayList<>());
        if (removeAt(list, ABSOLUTE_KEY, target.getX(), target.getY(), target.getZ()))
            return "§eRemoved §babsolute§e waypoint @ " + coords;
        list.add(new Waypoint(target.getX(), target.getY(), target.getZ(), colorArgb, filled, depth, 0, 0, 0, 1, 1, 1));
        save();
        return "§aAdded §babsolute§a waypoint @ " + coords;
    }

    // removes a waypoint at the exact (rx,ry,rz); returns true if one was found and removed
    private static boolean removeAt(List<Waypoint> list, String key, double rx, double ry, double rz) {
        for (int i = 0; i < list.size(); i++) {
            Waypoint wp = list.get(i);
            if (Math.abs(wp.rx() - rx) < 0.001 && Math.abs(wp.ry() - ry) < 0.001 && Math.abs(wp.rz() - rz) < 0.001) {
                list.remove(i);
                if (list.isEmpty()) byRoom.remove(key);
                save();
                return true;
            }
        }
        return false;
    }

    public static String removeNearest() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return "§cNo player.";
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();

        DungeonRoom room = DungeonManager.isInDungeon() ? DungeonManager.getCurrentRoom() : null;
        int[] clay = (room != null && room.getName() != null) ? detectClay(room) : null;

        String key;
        List<Waypoint> list;
        int[] xf; // clay transform, or null for absolute
        if (room != null && room.getName() != null && clay != null) {
            key = room.getName(); list = byRoom.get(key); xf = clay;
        } else {
            key = ABSOLUTE_KEY; list = byRoom.get(key); xf = null;
        }
        if (list == null || list.isEmpty()) return "§eNo waypoints here.";

        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            Waypoint wp = list.get(i);
            double wx, wz;
            if (xf != null) { double[] rot = rotateAroundNorth(xf[2], wp.rx(), wp.rz()); wx = rot[0] + xf[0]; wz = rot[1] + xf[1]; }
            else { wx = wp.rx(); wz = wp.rz(); }
            double dx = wx + 0.5 - px, dy = wp.ry() + 0.5 - py, dz = wz + 0.5 - pz;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) { bestD = d; best = i; }
        }
        list.remove(best);
        if (list.isEmpty()) byRoom.remove(key);
        save();
        return "§aRemoved the nearest waypoint (" + list.size() + " left).";
    }

    public static String clearRoom() {
        DungeonRoom room = DungeonManager.isInDungeon() ? DungeonManager.getCurrentRoom() : null;
        String key = (room != null && room.getName() != null) ? room.getName() : ABSOLUTE_KEY;
        List<Waypoint> removed = byRoom.remove(key);
        save();
        String where = key.equals(ABSOLUTE_KEY) ? "absolute" : "\"" + key + "\"";
        return removed == null ? "§eNo waypoints to clear (" + where + ")."
                : "§aCleared " + removed.size() + " " + where + " waypoint(s).";
    }

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

    private static String toHex(int argb) {
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return String.format("#%02X%02X%02X%02X", r, g, b, a);
    }

    private static double[] rotateToNorth(int rotation, double x, double z) {
        return switch (rotation) {
            case 1 -> new double[]{-x, -z};  // NORTH
            case 2 -> new double[]{z, -x};   // WEST
            case 3 -> new double[]{-z, x};   // EAST
            default -> new double[]{x, z};   // SOUTH
        };
    }

    private static double[] rotateAroundNorth(int rotation, double x, double z) {
        return switch (rotation) {
            case 1 -> new double[]{-x, -z};  // NORTH
            case 2 -> new double[]{-z, x};   // WEST
            case 3 -> new double[]{z, -x};   // EAST
            default -> new double[]{x, z};   // SOUTH (identity)
        };
    }

    private static final int[][] ROT_OFFSETS = {{-15, -15}, {15, 15}, {15, -15}, {-15, 15}}; // 0=SOUTH 1=NORTH 2=WEST 3=EAST

    private static int[] cachedClay = null;     // {clayX, clayZ, rotation}
    private static String cacheKey = "";

    public static int[] scanClayPos(DungeonRoom room) {
        return scanClay(room);
    }

    public static int[] relativeToWorld(int[] clay, int relX, int relZ) {
        double[] rot = rotateAroundNorth(clay[2], relX, relZ);
        return new int[]{(int) Math.round(rot[0]) + clay[0], (int) Math.round(rot[1]) + clay[1]};
    }

    static int[] detectClay(DungeonRoom room) {
        List<int[]> comps = room.getComponents();
        if (comps.isEmpty()) return null;
        String key = room.getName() + "@" + comps.get(0)[0] + "," + comps.get(0)[1] + ":" + comps.size();
        if (!key.equals(cacheKey)) { cacheKey = key; cachedClay = null; } // room changed -> invalidate
        if (cachedClay == null) cachedClay = scanClay(room);
        return cachedClay;
    }

    private static int[] scanClay(DungeonRoom room) {
        net.minecraft.world.level.Level level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return null;
        List<int[]> comps = room.getComponents();
        boolean multi = comps.size() > 1;
        net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int r : new int[]{1, 0, 2, 3}) {
            for (int[] comp : comps) {
                int[] corner = ComponentGrid.gridToWorldCorner(comp[0], comp[1]);
                int bx = corner[0] + ComponentGrid.HALF_ROOM_SIZE + ROT_OFFSETS[r][0];
                int bz = corner[1] + ComponentGrid.HALF_ROOM_SIZE + ROT_OFFSETS[r][1];
                for (int y = 150; y >= 11; y--) {
                    if (level.getBlockState(m.set(bx, y, bz)).is(net.minecraft.world.level.block.Blocks.BLUE_TERRACOTTA)
                            && (!multi || neighborsClear(level, bx, y, bz))) {
                        return new int[]{bx, bz, r};
                    }
                }
            }
        }
        return null;
    }

    private static boolean neighborsClear(net.minecraft.world.level.Level level, int x, int y, int z) {
        net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            net.minecraft.world.level.block.Block b = level.getBlockState(m.set(x + d[0], y, z + d[1])).getBlock();
            if (b != net.minecraft.world.level.block.Blocks.AIR && b != net.minecraft.world.level.block.Blocks.BLUE_TERRACOTTA) {
                return false;
            }
        }
        return true;
    }
}
