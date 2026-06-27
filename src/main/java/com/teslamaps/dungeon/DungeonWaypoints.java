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
            double x = coord(o, "x", "field_11175");
            double y = coord(o, "y", "field_11174");
            double z = coord(o, "z", "field_11173");
            int color = parseColor(o.has("color") ? o.get("color").getAsString() : "#FFFFFFFF");
            boolean filled = o.has("filled") && o.get("filled").getAsBoolean();
            boolean depth = !o.has("depth") || o.get("depth").getAsBoolean();

            double[] b = readAabb(o.has("aabb") ? o.getAsJsonObject("aabb") : null);
            return new Waypoint(x, y, z, color, filled, depth, b[0], b[1], b[2], b[3], b[4], b[5]);
        } catch (Exception ex) {
            return null;
        }
    }

    // reads a coordinate from the waypoint object, supporting top-level x/y/z,
    // blockPos.x/y/z, and blockPos.<yarn field> (Vec3i: field_11175/74/73 = x/y/z)
    private static double coord(JsonObject o, String plain, String field) {
        if (o.has(plain)) return o.get(plain).getAsDouble();
        JsonObject bp = o.getAsJsonObject("blockPos");
        if (bp.has(plain)) return bp.get(plain).getAsDouble();
        return bp.get(field).getAsDouble();
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
        AABB box = fitToBlock(wp, wx, wy, wz);
        // leaves are cutout/non-occluding: a depth-tested box on a leaf shows the foliage through it.
        // render those through walls so the waypoint stays clean; solid blocks still occlude normally.
        // red (#ff5555) waypoints always render through walls (important markers).
        boolean throughWalls = wp.depth() || isLeafAt(wx, wy, wz) || (wp.colorArgb() & 0xFFFFFF) == 0xFF5555;
        if (wp.filled()) {
            // grow slightly so the fill sits outside the block faces (no z-fighting flicker) and fully covers it
            ESPRenderer.drawFilledBox(matrices, box.inflate(0.01), wp.colorArgb(), cameraPos, throughWalls);
        } else {
            ESPRenderer.drawBoxOutline(matrices, box, wp.colorArgb(), 2.0f, cameraPos, throughWalls);
        }
    }

    private static boolean isLeafAt(double wx, double wy, double wz) {
        net.minecraft.world.level.Level level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return false;
        return level.getBlockState(net.minecraft.core.BlockPos.containing(wx, wy, wz))
                .is(net.minecraft.tags.BlockTags.LEAVES);
    }

    // when the stored box is the default full cube, hug the actual block shape (slab, wall, stairs, ...)
    private static AABB fitToBlock(Waypoint wp, double wx, double wy, double wz) {
        if (isDefaultCube(wp)) {
            net.minecraft.world.level.Level level = net.minecraft.client.Minecraft.getInstance().level;
            if (level != null) {
                net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(wx, wy, wz);
                net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                if (!state.isAir()) {
                    net.minecraft.world.phys.shapes.VoxelShape shape = state.getShape(level, pos);
                    if (!shape.isEmpty()) {
                        AABB b = shape.bounds();
                        return new AABB(wx + b.minX, wy + b.minY, wz + b.minZ,
                                wx + b.maxX, wy + b.maxY, wz + b.maxZ);
                    }
                }
            }
        }
        return new AABB(wx + wp.minX(), wy + wp.minY(), wz + wp.minZ(),
                wx + wp.maxX(), wy + wp.maxY(), wz + wp.maxZ());
    }

    private static boolean isDefaultCube(Waypoint wp) {
        return wp.minX() == 0 && wp.minY() == 0 && wp.minZ() == 0
                && wp.maxX() == 1 && wp.maxY() == 1 && wp.maxZ() == 1;
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

        // full-footprint scan: find ALL blue terracotta in the room and report offset from each component corner
        net.minecraft.world.level.Level lvl = mc.level;
        if (lvl != null) {
            net.minecraft.core.BlockPos.MutableBlockPos mb = new net.minecraft.core.BlockPos.MutableBlockPos();
            int found = 0;
            for (int[] c : room.getComponents()) {
                int[] corner = ComponentGrid.gridToWorldCorner(c[0], c[1]);
                for (int dx = 0; dx <= ComponentGrid.ROOM_SIZE - 1 && found < 12; dx++)
                    for (int dz = 0; dz <= ComponentGrid.ROOM_SIZE - 1 && found < 12; dz++)
                        for (int y = 150; y >= 11; y--) {
                            if (lvl.getBlockState(mb.set(corner[0] + dx, y, corner[1] + dz))
                                    .is(net.minecraft.world.level.block.Blocks.BLUE_TERRACOTTA)) {
                                msg.accept("§9terracotta §fat §e" + (corner[0] + dx) + "," + y + "," + (corner[1] + dz)
                                        + "§7 (offset from corner " + dx + "," + dz + ", expected one of 0/30)");
                                found++;
                                break;
                            }
                        }
            }
            if (found == 0) msg.accept("§cNo BLUE_TERRACOTTA anywhere in the room footprint (y 11-150).");
        }

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

        String why = !DungeonManager.isInDungeon() ? "not in dungeon"
                : room == null ? "no room at your position"
                : room.getName() == null ? "room has no name"
                : "no terracotta found (clay scan failed)";

        List<Waypoint> list = byRoom.computeIfAbsent(ABSOLUTE_KEY, k -> new ArrayList<>());
        if (removeAt(list, ABSOLUTE_KEY, target.getX(), target.getY(), target.getZ()))
            return "§eRemoved §babsolute§e waypoint @ " + coords;
        list.add(new Waypoint(target.getX(), target.getY(), target.getZ(), colorArgb, filled, depth, 0, 0, 0, 1, 1, 1));
        save();
        return "§aAdded §babsolute§a waypoint @ " + coords + " §7(" + why + ")";
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

        // consider both the current room's waypoints AND absolute ones, remove whichever is physically nearest
        String bestKey = null;
        int best = -1;
        double bestD = Double.MAX_VALUE;
        if (room != null && room.getName() != null && clay != null)
            for (int i = 0; i < sizeOf(room.getName()); i++) {
                Waypoint wp = byRoom.get(room.getName()).get(i);
                double[] rot = rotateAroundNorth(clay[2], wp.rx(), wp.rz());
                double d = dist2(rot[0] + clay[0], wp.ry(), rot[1] + clay[1], px, py, pz);
                if (d < bestD) { bestD = d; best = i; bestKey = room.getName(); }
            }
        for (int i = 0; i < sizeOf(ABSOLUTE_KEY); i++) {
            Waypoint wp = byRoom.get(ABSOLUTE_KEY).get(i);
            double d = dist2(wp.rx(), wp.ry(), wp.rz(), px, py, pz);
            if (d < bestD) { bestD = d; best = i; bestKey = ABSOLUTE_KEY; }
        }
        if (bestKey == null) return "§eNo waypoints here.";

        List<Waypoint> list = byRoom.get(bestKey);
        list.remove(best);
        if (list.isEmpty()) byRoom.remove(bestKey);
        save();
        String where = bestKey.equals(ABSOLUTE_KEY) ? "§babsolute§a " : "";
        return "§aRemoved the nearest " + where + "waypoint (" + list.size() + " left).";
    }

    private static int sizeOf(String key) {
        List<Waypoint> l = byRoom.get(key);
        return l == null ? 0 : l.size();
    }

    private static double dist2(double wx, double wy, double wz, double px, double py, double pz) {
        double dx = wx + 0.5 - px, dy = wy + 0.5 - py, dz = wz + 0.5 - pz;
        return dx * dx + dy * dy + dz * dz;
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

    public static int[] worldToRelative(int[] clay, int worldX, int worldZ) {
        double[] rel = rotateToNorth(clay[2], worldX - clay[0], worldZ - clay[1]);
        return new int[]{(int) Math.round(rel[0]), (int) Math.round(rel[1])};
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
