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

    /** type = Odin's WaypointType name (NORMAL / SECRET / ETHERWARP) or null; title may be null. */
    public record Waypoint(double rx, double ry, double rz, int colorArgb, boolean filled, boolean depth,
                           double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                           String type, String title) {
        public Waypoint(double rx, double ry, double rz, int colorArgb, boolean filled, boolean depth,
                        double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this(rx, ry, rz, colorArgb, filled, depth, minX, minY, minZ, maxX, maxY, maxZ, null, null);
        }
        Waypoint withType(String t) {
            return new Waypoint(rx, ry, rz, colorArgb, filled, depth, minX, minY, minZ, maxX, maxY, maxZ, t, title);
        }
        Waypoint withTitle(String t) {
            return new Waypoint(rx, ry, rz, colorArgb, filled, depth, minX, minY, minZ, maxX, maxY, maxZ, type, t);
        }
    }

    public static final String[] TYPES = {"None", "Normal", "Secret", "Etherwarp"};

    private static final Map<String, List<Waypoint>> byRoom = new HashMap<>();
    // secret / etherwarp waypoints already done this run
    private static final java.util.Set<Waypoint> clicked = new java.util.HashSet<>();


    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("teslamaps").resolve("dungeon_waypoints.json");

    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        byRoom.clear();
        clicked.clear();
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
            String type = str(o, "type");
            if (type != null) type = type.toUpperCase(java.util.Locale.ROOT);
            if ("NONE".equals(type)) type = null;
            return new Waypoint(x, y, z, color, filled, depth, b[0], b[1], b[2], b[3], b[4], b[5], type, str(o, "title"));
        } catch (Exception ex) {
            return null;
        }
    }

    private static String str(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return null;
        String s = o.get(key).getAsString();
        return s.isBlank() ? null : s;
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

        // Context-scoped waypoints (boss arena / per area) — absolute coords, shown only in that context.
        String ctx = currentContextKey();
        if (ctx != null) {
            List<Waypoint> ctxList = byRoom.get(ctx);
            if (ctxList != null) {
                for (Waypoint wp : ctxList) {
                    if (!clicked.isEmpty() && clicked.contains(wp)) continue;
                    drawWaypoint(matrices, cameraPos, wp, wp.rx(), wp.ry(), wp.rz());
                }
            }
        }

        if (!DungeonManager.isInDungeon()) return;
        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null) return;
        List<Waypoint> list = byRoom.get(room.getName());
        if (list == null || list.isEmpty()) return;
        int[] clay = detectClay(room); // {clayX, clayZ, rotation}, or null if terracotta not found yet
        if (clay == null) return;

        for (Waypoint wp : list) {
            if (!clicked.isEmpty() && clicked.contains(wp)) continue;
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
        if (wp.title() != null) {
            float s = TeslaMapsConfig.get().waypointTitleScale;
            ESPRenderer.drawText(matrices, wp.title(), new Vec3(wx + 0.5, wy + 0.5 + 0.1 * s, wz + 0.5), s, cameraPos, true, throughWalls);
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

    // Scope key for non-room waypoints: the dungeon boss arena (shared F/M per floor number)
    // or the current SkyBlock area. These render only while you are back in that same context.
    static String currentContextKey() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (DungeonManager.isInDungeon()) {
            if (mc.player != null && ComponentGrid.worldToGrid((int) mc.player.getX(), (int) mc.player.getZ()) == null) {
                DungeonFloor f = DungeonManager.getCurrentFloor();
                return "boss:" + (f != null ? f.getLevel() : 0); // F7 and M7 share boss:7
            }
            return "area:Catacombs"; // inside the dungeon but not in a named room
        }
        String area = currentArea();
        return "area:" + (area != null ? area : "unknown");
    }

    private static String currentArea() {
        String a = com.teslamaps.utils.SkyblockUtils.getArea();
        return a.isEmpty() ? null : a;
    }

    public static String addAtTarget(int colorArgb, boolean filled, boolean depth) {
        return addAtTarget(colorArgb, filled, depth, null);
    }

    private static String typeKey(String display) {
        if (display == null || display.equalsIgnoreCase("None")) return null;
        return display.toUpperCase(java.util.Locale.ROOT);
    }

    private static String typeName(String key) {
        if (key == null) return "None";
        return key.charAt(0) + key.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private static Waypoint newWaypoint(double x, double y, double z, int colorArgb, boolean filled, boolean depth, String type) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        double hx = 0, hy = 0, hz = 0;
        if (!c.waypointUseBlockSize) {
            hx = (c.waypointSizeX - 1) / 2.0; hy = (c.waypointSizeY - 1) / 2.0; hz = (c.waypointSizeZ - 1) / 2.0;
        }
        return new Waypoint(x, y, z, colorArgb, filled, depth, -hx, -hy, -hz, 1 + hx, 1 + hy, 1 + hz, typeKey(type), null);
    }

    public static void resetClicked() {
        clicked.clear();
    }

    /** A secret was taken at {@code pos}: hide the matching SECRET waypoint (exact block, or nearest within sqrt(maxDistSq)). */
    public static void onSecret(net.minecraft.core.BlockPos pos, int maxDistSq) {
        if (!TeslaMapsConfig.get().dungeonWaypoints || !DungeonManager.isInDungeon()) return;
        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getName() == null) return;
        List<Waypoint> list = byRoom.get(room.getName());
        int[] clay = list == null ? null : detectClay(room);
        if (clay == null) return;
        int[] rel = worldToRelative(clay, pos.getX(), pos.getZ());
        Waypoint best = null;
        double bestD = Double.MAX_VALUE;
        for (Waypoint wp : list) {
            if (!"SECRET".equals(wp.type()) || clicked.contains(wp)) continue;
            double dx = wp.rx() - rel[0], dy = wp.ry() - pos.getY(), dz = wp.rz() - rel[1];
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= maxDistSq && d < bestD) { bestD = d; best = wp; }
        }
        if (best != null) clicked.add(best);
    }

    /** Server teleport (etherwarp landing): hide the ETHERWARP waypoint the player now stands on. */
    public static void onTeleport(Vec3 pos) {
        if (!net.minecraft.client.Minecraft.getInstance().isSameThread()) return;
        if (!TeslaMapsConfig.get().dungeonWaypoints || !DungeonManager.isInDungeon()) return;
        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getName() == null) return;
        List<Waypoint> list = byRoom.get(room.getName());
        int[] clay = list == null ? null : detectClay(room);
        if (clay == null) return;
        for (Waypoint wp : list) {
            if (!"ETHERWARP".equals(wp.type()) || clicked.contains(wp)) continue;
            double[] rot = rotateAroundNorth(clay[2], wp.rx(), wp.rz());
            double dx = rot[0] + clay[0] + 0.5 - pos.x, dy = wp.ry() + 1 - pos.y, dz = rot[1] + clay[1] + 0.5 - pos.z;
            if (dx * dx + dy * dy + dz * dz <= 2.25) clicked.add(wp);
        }
    }

    /** Waypoint list + stored coords of the looked-at block: {key, rx, ry, rz}, or null. */
    private static Object[] target() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr)
                || bhr.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return null;
        net.minecraft.core.BlockPos t = bhr.getBlockPos();
        DungeonRoom room = DungeonManager.isInDungeon() ? DungeonManager.getCurrentRoom() : null;
        int[] clay = (room != null && room.getName() != null) ? detectClay(room) : null;
        if (clay != null) {
            int[] rel = worldToRelative(clay, t.getX(), t.getZ());
            return new Object[]{room.getName(), (double) rel[0], (double) t.getY(), (double) rel[1]};
        }
        return new Object[]{currentContextKey(), (double) t.getX(), (double) t.getY(), (double) t.getZ()};
    }

    private static int indexAt(List<Waypoint> list, double rx, double ry, double rz) {
        if (list == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            Waypoint wp = list.get(i);
            if (Math.abs(wp.rx() - rx) < 0.001 && Math.abs(wp.ry() - ry) < 0.001 && Math.abs(wp.rz() - rz) < 0.001) return i;
        }
        return -1;
    }

    private static Waypoint lookedAt() {
        Object[] t = target();
        if (t == null) return null;
        List<Waypoint> list = byRoom.get((String) t[0]);
        int i = indexAt(list, (double) t[1], (double) t[2], (double) t[3]);
        return i < 0 ? null : list.get(i);
    }

    /** Title / type of the looked-at waypoint; null title clears it. */
    public static String editTarget(String title, String type, boolean setTitle) {
        Object[] t = target();
        if (t == null) return "§cLook at a waypoint block.";
        List<Waypoint> list = byRoom.get((String) t[0]);
        int i = indexAt(list, (double) t[1], (double) t[2], (double) t[3]);
        if (i < 0) return "§cNo waypoint on that block.";
        Waypoint wp = list.get(i);
        list.set(i, setTitle ? wp.withTitle(title == null || title.isBlank() ? null : title) : wp.withType(typeKey(type)));
        save();
        return setTitle ? "§aWaypoint title " + (title == null || title.isBlank() ? "cleared." : "set to §f" + title)
                : "§aWaypoint type set to §f" + typeName(typeKey(type));
    }

    // --- editor HUD ---
    private static String editorLine = null;
    private static int editorColor = 0xFFFFFFFF;

    public static void register() {
        com.teslamaps.registry.Feature.of("DungeonWaypointEditor").section("Secrets & Waypoints")
                .when(() -> TeslaMapsConfig.get().dungeonWaypoints && TeslaMapsConfig.get().dungeonWaypointEditor)
                .tick(DungeonWaypoints::tickEditor)
                .hud("dungeonwaypointeditor", DungeonWaypoints::renderEditor);
        com.teslamaps.registry.HudEditElement.of("dungeonWaypointEditor", "Waypoint Editor")
                .ctx(com.teslamaps.registry.HudEditElement.HudCtx.DUNGEON).section("Secrets & Waypoints")
                .enabled(() -> TeslaMapsConfig.get().dungeonWaypoints && TeslaMapsConfig.get().dungeonWaypointEditor)
                .size(() -> net.minecraft.client.Minecraft.getInstance().font.width(EDITOR_SAMPLE), () -> 19)
                .preview((g, mc, x, y, s) -> drawEditor(g, mc, x, y, s, "§fPlacing", EDITOR_SAMPLE));
    }

    private static final String EDITOR_SAMPLE = "§fType: §5Secret§7, §r#55FF55§7, §3Outline§7, §cThrough Walls§7, §2Block Size";
    private static String editorTitle = "";

    private static void tickEditor() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!DungeonManager.isInDungeon()) { editorLine = null; return; }
        Waypoint wp = lookedAt();
        StringBuilder sb = new StringBuilder();
        if (wp != null) {
            editorTitle = "§fEditing Waypoints §8|§f Viewing";
            editorColor = wp.colorArgb() | 0xFF000000;
            sb.append("§fType: §5").append(typeName(wp.type()));
            sb.append("§7, §r#").append(String.format("%06X", wp.colorArgb() & 0xFFFFFF)).append("§7");
            if (wp.title() != null) sb.append(", §fTitle: §a").append(wp.title()).append("§7");
            sb.append(", ").append(wp.filled() ? "§2Filled" : "§3Outline");
            sb.append("§7, ").append(wp.depth() ? "§cThrough Walls" : "§2Depth Check");
            sb.append("§7, §3Size: ").append(String.format(java.util.Locale.ROOT, "%.2fx%.2fx%.2f",
                    wp.maxX() - wp.minX(), wp.maxY() - wp.minY(), wp.maxZ() - wp.minZ()));
        } else {
            editorTitle = "§fEditing Waypoints §8|§f Placing (#1)";
            editorColor = TeslaMapsConfig.parseColor(c.waypointAddColor) | 0xFF000000;
            sb.append("§fType: §5").append(c.waypointAddType == null ? "None" : c.waypointAddType);
            sb.append("§7, §r#").append(c.waypointAddColor).append("§7");
            sb.append(", ").append(c.waypointAddFilled ? "§2Filled" : "§3Outline");
            sb.append("§7, ").append(c.waypointAddThroughWalls ? "§cThrough Walls" : "§2Depth Check");
            sb.append("§7, ").append(c.waypointUseBlockSize ? "§2Block Size" : String.format(java.util.Locale.ROOT,
                    "§3Size: %.2fx%.2fx%.2f", c.waypointSizeX, c.waypointSizeY, c.waypointSizeZ));
        }
        editorLine = sb.toString();
    }

    private static void renderEditor(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker d) {
        if (editorLine == null) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        drawEditor(g, net.minecraft.client.Minecraft.getInstance(), c.dungeonWaypointEditorX, c.dungeonWaypointEditorY,
                c.dungeonWaypointEditorScale, editorTitle, editorLine);
    }

    private static void drawEditor(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.Minecraft mc,
                                   int x, int y, float scale, String title, String line) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int w = Math.max(mc.font.width(line), mc.font.width(title));
        com.teslamaps.render.gui.TeslaGui.backdrop(g, 0, 0, w, 19);
        g.centeredText(mc.font, title, w / 2, 0, 0xFFFFFFFF);
        g.text(mc.font, line, 0, 10, editorColor);
        pose.popMatrix();
    }

    public static String addAtTarget(int colorArgb, boolean filled, boolean depth, String type) {
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
            list.add(newWaypoint(rel[0], target.getY(), rel[1], colorArgb, filled, depth, type));
            save();
            return "§aAdded waypoint in §e\"" + room.getName() + "\"§a @ " + coords;
        }

        String ctx = currentContextKey();
        List<Waypoint> list = byRoom.computeIfAbsent(ctx, k -> new ArrayList<>());
        if (removeAt(list, ctx, target.getX(), target.getY(), target.getZ()))
            return "§eRemoved §b" + ctx + "§e waypoint @ " + coords;
        list.add(newWaypoint(target.getX(), target.getY(), target.getZ(), colorArgb, filled, depth, type));
        save();
        return "§aAdded §b" + ctx + "§a waypoint @ " + coords;
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
        String ctx = currentContextKey();
        for (int i = 0; i < sizeOf(ctx); i++) {
            Waypoint wp = byRoom.get(ctx).get(i);
            double d = dist2(wp.rx(), wp.ry(), wp.rz(), px, py, pz);
            if (d < bestD) { bestD = d; best = i; bestKey = ctx; }
        }
        if (bestKey == null) return "§eNo waypoints here.";

        List<Waypoint> list = byRoom.get(bestKey);
        list.remove(best);
        if (list.isEmpty()) byRoom.remove(bestKey);
        save();
        String where = bestKey.startsWith("boss:") || bestKey.startsWith("area:") ? "§b" + bestKey + "§a " : "";
        return "§aRemoved the nearest " + where + "waypoint (" + list.size() + " left).";
    }

    private static int sizeOf(String key) {
        List<Waypoint> l = byRoom.get(key);
        return l == null ? 0 : l.size();
    }

    // moves legacy "__absolute__" waypoints (pre-context format, never rendered anymore) that are
    // near the player into the current context key — run once per boss arena to adopt them
    public static String convertLegacy() {
        List<Waypoint> legacy = byRoom.get("__absolute__");
        if (legacy == null || legacy.isEmpty()) return "§eNo legacy __absolute__ waypoints in the file.";
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return "§cNo player.";
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();

        String ctx = currentContextKey();
        List<Waypoint> target = byRoom.computeIfAbsent(ctx, k -> new ArrayList<>());
        int moved = 0;
        for (java.util.Iterator<Waypoint> it = legacy.iterator(); it.hasNext(); ) {
            Waypoint wp = it.next();
            if (dist2(wp.rx(), wp.ry(), wp.rz(), px, py, pz) <= 200 * 200) {
                target.add(wp);
                it.remove();
                moved++;
            }
        }
        if (legacy.isEmpty()) byRoom.remove("__absolute__");
        if (target.isEmpty()) byRoom.remove(ctx);
        save();
        return moved == 0
                ? "§eNo legacy waypoints within 200 blocks. §7(" + legacy.size() + " stored elsewhere — run this in their boss arena)"
                : "§aMoved " + moved + " legacy waypoint(s) into §b" + ctx + "§a. §7(" + legacy.size() + " remaining)";
    }

    private static double dist2(double wx, double wy, double wz, double px, double py, double pz) {
        double dx = wx + 0.5 - px, dy = wy + 0.5 - py, dz = wz + 0.5 - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    public static String clearRoom() {
        DungeonRoom room = DungeonManager.isInDungeon() ? DungeonManager.getCurrentRoom() : null;
        String key = (room != null && room.getName() != null) ? room.getName() : currentContextKey();
        List<Waypoint> removed = byRoom.remove(key);
        save();
        return removed == null ? "§eNo waypoints to clear (" + key + ")."
                : "§aCleared " + removed.size() + " §b" + key + "§a waypoint(s).";
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
                    if (wp.type() != null) o.addProperty("type", wp.type());
                    if (wp.title() != null) o.addProperty("title", wp.title());
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
    private static DungeonRoom cacheRoom = null;
    private static String cacheName = null;
    private static int cacheX, cacheZ, cacheSize;
    private static long nextClayScan = 0;

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
        int[] c0 = comps.get(0);
        if (room != cacheRoom || !java.util.Objects.equals(room.getName(), cacheName)
                || c0[0] != cacheX || c0[1] != cacheZ || comps.size() != cacheSize) {
            cacheRoom = room;
            cacheName = room.getName();
            cacheX = c0[0];
            cacheZ = c0[1];
            cacheSize = comps.size();
            cachedClay = null;
            nextClayScan = 0;
        }
        if (cachedClay == null) {
            long now = System.currentTimeMillis();
            if (now < nextClayScan) return null;
            cachedClay = scanClay(room);
            if (cachedClay == null) nextClayScan = now + 1000;
        }
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
