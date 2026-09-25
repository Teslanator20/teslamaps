package com.teslamaps.features;

// Ported (slimmed) from SkyHanni's test/WorldEdit.kt, test/ParkourWaypointSaver.kt, test/graph/GraphEditor*.kt
// and the /shtestisland + /shwhereami debug commands
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DevWorldTools {

    private static final String[] ISLANDS = {"Hub", "Private Island", "Garden", "The Farming Islands", "The Park",
            "Gold Mine", "Deep Caverns", "Dwarven Mines", "Crystal Hollows", "Mineshaft", "Spider's Den", "The End",
            "Crimson Isle", "Kuudra", "Dungeon Hub", "Catacombs", "Jerry's Workshop", "The Rift", "Backwater Bayou",
            "Galatea", "Dark Auction"};

    private DevWorldTools() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static final Feature FEATURE = Feature.of("DevWorldTools").when(() -> cfg().devWorldTools);

    private static BlockPos pos1, pos2;
    private static AABB box1, box2, region;

    private static final List<BlockPos> parkour = new ArrayList<>();
    private static final List<AABB> parkourBoxes = new ArrayList<>();
    private static final List<Vec3> parkourCenters = new ArrayList<>();
    private static long lastParkourKey;

    private static final class Node {
        final int id, x, y, z;
        String name;
        final AABB box;
        final Vec3 center, labelPos;
        String label;

        Node(int id, int x, int y, int z, String name) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = name;
            box = new AABB(x + 0.3, y, z + 0.3, x + 0.7, y + 0.4, z + 0.7);
            center = new Vec3(x + 0.5, y + 0.2, z + 0.5);
            labelPos = new Vec3(x + 0.5, y + 0.8, z + 0.5);
            relabel();
        }

        void relabel() { label = name == null ? "§7" + id : "§e" + id + " §f" + name; }
        Node copy() { return new Node(id, x, y, z, name); }
    }

    private record Graph(List<Node> nodes, Set<Long> edges, int nextId, int active) {}

    private static List<Node> nodes = new ArrayList<>();
    private static Set<Long> edges = new HashSet<>();
    private static int nextId = 0, active = -1;
    private static final ArrayList<Graph> history = new ArrayList<>();
    private static final boolean[] keyDown = new boolean[6];
    private static Object lastLevel;

    public static void register() {
        FEATURE.tick(DevWorldTools::tick).world(DevWorldTools::render)
                .config("Interface", b -> {
                    b.toggle("Dev World Tools", () -> cfg().devWorldTools, v -> cfg().devWorldTools = v)
                            .desc("Region selection, parkour recorder and a path graph editor for collecting coordinates");
                    b.toggle("  Region Select (wooden axe)", () -> cfg().devRegionSelect, v -> cfg().devRegionSelect = v)
                            .desc("Left / right click a block with a wooden axe, then /tmap devtools region copy");
                    b.toggle("  Parkour Recorder", () -> cfg().devParkourRecorder, v -> cfg().devParkourRecorder = v)
                            .desc("Saves the block under you per key press and copies the list as JSON");
                    b.keybind("    Save Position Key", () -> cfg().devParkourSaveKey, v -> cfg().devParkourSaveKey = v);
                    b.keybind("    Remove Last Key", () -> cfg().devParkourDeleteKey, v -> cfg().devParkourDeleteKey = v)
                            .desc("Sneak = clear all, empty list = load from clipboard");
                    b.toggle("  Graph Editor", () -> cfg().devGraphEditor, v -> cfg().devGraphEditor = v)
                            .desc("Nodes + edges, export with /tmap devtools graph export (SkyHanni graph format)");
                    b.keybind("    Place Node Key", () -> cfg().devGraphPlaceKey, v -> cfg().devGraphPlaceKey = v);
                    b.keybind("    Select Nearest Key", () -> cfg().devGraphSelectKey, v -> cfg().devGraphSelectKey = v)
                            .desc("Sneak = connect / disconnect the nearest node with the active one");
                    b.keybind("    Delete Nearest Key", () -> cfg().devGraphDeleteKey, v -> cfg().devGraphDeleteKey = v);
                    b.keybind("    Undo Key", () -> cfg().devGraphUndoKey, v -> cfg().devGraphUndoKey = v);
                    b.info("/tmap debug island <name|clear> fakes the island, /tmap debug whereami shows it");
                });

        AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
            if (!level.isClientSide() || !selecting(player.getMainHandItem().is(Items.WOODEN_AXE))) return InteractionResult.PASS;
            setPos(true, pos.immutable());
            return InteractionResult.FAIL;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() || !selecting(player.getItemInHand(hand).is(Items.WOODEN_AXE))) return InteractionResult.PASS;
            setPos(false, hit.getBlockPos().immutable());
            return InteractionResult.FAIL;
        });

        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> {
            d.register(ClientCommands.literal("tmap").then(ClientCommands.literal("debug")
                    .then(ClientCommands.literal("whereami").executes(c -> {
                        String o = SkyblockUtils.areaOverride;
                        SkyblockUtils.areaOverride = null;
                        String real = SkyblockUtils.getArea();
                        SkyblockUtils.areaOverride = o;
                        msg(c.getSource(), "§7Island: §f\"" + SkyblockUtils.getArea() + "\""
                                + (o != null ? " §e(override, detected: \"" + real + "\")" : ""));
                        return 1;
                    }))
                    .then(ClientCommands.literal("island")
                            .executes(c -> {
                                msg(c.getSource(), "§7Override: §f" + (SkyblockUtils.areaOverride == null ? "none" : SkyblockUtils.areaOverride)
                                        + " §7- /tmap debug island <name|clear>");
                                return 1;
                            })
                            .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                    .suggests((c, sb) -> {
                                        String typed = sb.getRemaining().toLowerCase(Locale.ROOT);
                                        for (String s : ISLANDS) if (s.toLowerCase(Locale.ROOT).startsWith(typed) && !s.equalsIgnoreCase(typed)) sb.suggest(s);
                                        if ("clear".startsWith(typed) && !"clear".equals(typed)) sb.suggest("clear");
                                        return sb.buildFuture();
                                    })
                                    .executes(c -> {
                                        String name = StringArgumentType.getString(c, "name").trim();
                                        boolean clear = name.equalsIgnoreCase("clear") || name.equalsIgnoreCase("off") || name.isEmpty();
                                        SkyblockUtils.areaOverride = clear ? null : name;
                                        msg(c.getSource(), clear ? "§7Island override cleared" : "§7Island override: §f" + name);
                                        return 1;
                                    })))));
            d.register(ClientCommands.literal("tmap").then(ClientCommands.literal("devtools")
                    .executes(c -> {
                        msg(c.getSource(), "§7/tmap devtools region [copy|reset|pos1|pos2], parkour [copy|clear], graph [export|import|clear|undo|name <text>]");
                        return 1;
                    })
                    .then(ClientCommands.literal("region")
                            .then(ClientCommands.literal("copy").executes(c -> copyRegion(c.getSource())))
                            .then(ClientCommands.literal("reset").executes(c -> {
                                pos1 = pos2 = null;
                                updateRegion();
                                msg(c.getSource(), "§7Region reset");
                                return 1;
                            }))
                            .then(ClientCommands.literal("pos1").executes(c -> playerPos(c.getSource(), true)))
                            .then(ClientCommands.literal("pos2").executes(c -> playerPos(c.getSource(), false))))
                    .then(ClientCommands.literal("parkour")
                            .then(ClientCommands.literal("copy").executes(c -> {
                                copyParkour();
                                msg(c.getSource(), "§7Copied " + parkour.size() + " positions");
                                return 1;
                            }))
                            .then(ClientCommands.literal("clear").executes(c -> {
                                parkour.clear();
                                rebuildParkour();
                                msg(c.getSource(), "§7Parkour cleared");
                                return 1;
                            })))
                    .then(ClientCommands.literal("graph")
                            .then(ClientCommands.literal("export").executes(c -> exportGraph(c.getSource())))
                            .then(ClientCommands.literal("import").executes(c -> importGraph(c.getSource())))
                            .then(ClientCommands.literal("undo").executes(c -> {
                                msg(c.getSource(), undo() ? "§7Undone" : "§7Nothing to undo");
                                return 1;
                            }))
                            .then(ClientCommands.literal("clear").executes(c -> {
                                save();
                                nodes = new ArrayList<>();
                                edges = new HashSet<>();
                                nextId = 0;
                                active = -1;
                                msg(c.getSource(), "§7Graph cleared (undo works)");
                                return 1;
                            }))
                            .then(ClientCommands.literal("name")
                                    .then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                                        Node n = node(active);
                                        if (n == null) {
                                            msg(c.getSource(), "§cNo active node");
                                            return 0;
                                        }
                                        save();
                                        String t = StringArgumentType.getString(c, "text").trim();
                                        n.name = t.isEmpty() || t.equalsIgnoreCase("clear") ? null : t;
                                        n.relabel();
                                        msg(c.getSource(), "§7Node " + n.id + " named " + (n.name == null ? "-" : n.name));
                                        return 1;
                                    }))))));
        });
    }

    private static boolean selecting(boolean axe) {
        return axe && FEATURE.active() && cfg().devRegionSelect;
    }

    private static void msg(FabricClientCommandSource s, String text) {
        s.sendFeedback(Component.literal(text));
    }

    private static void chat(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §f" + text));
    }

    // ---------------------------------------------------------------- region

    private static void setPos(boolean first, BlockPos p) {
        if (first) pos1 = p;
        else pos2 = p;
        updateRegion();
        chat((first ? "§cPos 1" : "§9Pos 2") + " §f" + p.getX() + ", " + p.getY() + ", " + p.getZ());
    }

    private static int playerPos(FabricClientCommandSource s, boolean first) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        setPos(first, mc.player.blockPosition());
        return 1;
    }

    private static void updateRegion() {
        box1 = pos1 == null ? null : new AABB(pos1).inflate(0.002);
        box2 = pos2 == null ? null : new AABB(pos2).inflate(0.002);
        region = pos1 == null || pos2 == null ? null : new AABB(
                Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()),
                Math.max(pos1.getX(), pos2.getX()) + 1, Math.max(pos1.getY(), pos2.getY()) + 1, Math.max(pos1.getZ(), pos2.getZ()) + 1);
    }

    private static int copyRegion(FabricClientCommandSource s) {
        StringBuilder sb = new StringBuilder();
        if (pos1 != null) sb.append("BlockPos pos1 = new BlockPos(").append(pos1.getX()).append(", ").append(pos1.getY()).append(", ").append(pos1.getZ()).append(");\n");
        if (pos2 != null) sb.append("BlockPos pos2 = new BlockPos(").append(pos2.getX()).append(", ").append(pos2.getY()).append(", ").append(pos2.getZ()).append(");\n");
        if (region != null) sb.append("AABB box = new AABB(").append(region.minX).append(", ").append(region.minY).append(", ").append(region.minZ)
                .append(", ").append(region.maxX).append(", ").append(region.maxY).append(", ").append(region.maxZ).append(");\n");
        if (sb.isEmpty()) {
            msg(s, "§cNothing selected");
            return 0;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
        msg(s, "§7Copied region to clipboard");
        return 1;
    }

    // ---------------------------------------------------------------- tick

    private static boolean pressed(int idx, int key, boolean allowed) {
        boolean down = allowed && KeyNames.isDown(key);
        boolean edge = down && !keyDown[idx];
        keyDown[idx] = down;
        return edge;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            pos1 = pos2 = null;
            updateRegion();
        }
        if (mc.player == null) return;
        TeslaMapsConfig c = cfg();
        boolean free = mc.screen == null;
        boolean graph = c.devGraphEditor;
        boolean park = c.devParkourRecorder && !graph;

        if (pressed(0, c.devParkourSaveKey, free && park)) parkourSave(mc);
        if (pressed(1, c.devParkourDeleteKey, free && park)) parkourDelete(mc);
        if (pressed(2, c.devGraphPlaceKey, free && graph)) place(mc);
        if (pressed(3, c.devGraphSelectKey, free && graph)) select(mc);
        if (pressed(4, c.devGraphDeleteKey, free && graph)) delete(mc);
        if (pressed(5, c.devGraphUndoKey, free && graph)) chat(undo() ? "Undone" : "Nothing to undo");
    }

    // ---------------------------------------------------------------- parkour

    private static void parkourSave(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastParkourKey < 250) return;
        lastParkourKey = now;
        BlockPos below = BlockPos.containing(mc.player.getX(), mc.player.getY() - 1, mc.player.getZ());
        if (!parkour.isEmpty() && parkour.get(parkour.size() - 1).equals(below)) return;
        parkour.add(below);
        rebuildParkour();
        copyParkour();
    }

    private static void parkourDelete(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastParkourKey < 250) return;
        lastParkourKey = now;
        if (parkour.isEmpty()) {
            loadParkour(mc.keyboardHandler.getClipboard());
        } else if (mc.player.isShiftKeyDown()) {
            parkour.clear();
        } else {
            parkour.remove(parkour.size() - 1);
        }
        rebuildParkour();
    }

    private static void loadParkour(String clip) {
        if (clip == null || clip.isBlank()) return;
        List<BlockPos> out = new ArrayList<>();
        try {
            for (String line : clip.split("\n")) {
                String raw = line.replace("\"", "").replace(",", "").trim();
                if (raw.isEmpty()) continue;
                String[] p = raw.split(":");
                out.add(BlockPos.containing(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])));
            }
        } catch (Exception e) {
            chat("§cClipboard is not a parkour list");
            return;
        }
        parkour.clear();
        parkour.addAll(out);
        chat("Loaded " + out.size() + " positions");
    }

    private static void copyParkour() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parkour.size(); i++) {
            BlockPos p = parkour.get(i);
            if (i > 0) sb.append(",\n");
            sb.append('"').append(p.getX()).append(':').append(p.getY()).append(':').append(p.getZ()).append('"');
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
    }

    private static void rebuildParkour() {
        parkourBoxes.clear();
        parkourCenters.clear();
        for (BlockPos p : parkour) {
            parkourBoxes.add(new AABB(p).inflate(0.002));
            parkourCenters.add(new Vec3(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5));
        }
    }

    // ---------------------------------------------------------------- graph

    private static long key(int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xFFFFFFFFL);
    }

    private static Node node(int id) {
        for (Node n : nodes) if (n.id == id) return n;
        return null;
    }

    private static Node nearest(Minecraft mc, double max) {
        Node best = null;
        double bd = max * max;
        for (Node n : nodes) {
            double d = n.center.distanceToSqr(mc.player.position());
            if (d < bd) {
                bd = d;
                best = n;
            }
        }
        return best;
    }

    private static void save() {
        List<Node> copy = new ArrayList<>(nodes.size());
        for (Node n : nodes) copy.add(n.copy());
        history.add(new Graph(copy, new HashSet<>(edges), nextId, active));
        if (history.size() > 100) history.remove(0);
    }

    private static boolean undo() {
        if (history.isEmpty()) return false;
        Graph g = history.remove(history.size() - 1);
        nodes = new ArrayList<>(g.nodes());
        edges = new HashSet<>(g.edges());
        nextId = g.nextId();
        active = g.active();
        return true;
    }

    private static void place(Minecraft mc) {
        BlockPos p = mc.player.blockPosition();
        for (Node n : nodes) {
            if (n.x == p.getX() && n.y == p.getY() && n.z == p.getZ()) {
                active = n.id;
                return;
            }
        }
        save();
        Node n = new Node(nextId++, p.getX(), p.getY(), p.getZ(), null);
        nodes.add(n);
        if (node(active) != null) edges.add(key(active, n.id));
        active = n.id;
    }

    private static void select(Minecraft mc) {
        Node n = nearest(mc, 6);
        if (n == null) return;
        if (mc.player.isShiftKeyDown()) {
            if (node(active) == null || n.id == active) return;
            save();
            long k = key(active, n.id);
            if (!edges.remove(k)) edges.add(k);
            return;
        }
        active = n.id == active ? -1 : n.id;
    }

    private static void delete(Minecraft mc) {
        Node n = nearest(mc, 4);
        if (n == null) return;
        save();
        nodes.remove(n);
        edges.removeIf(k -> (int) (k >> 32) == n.id || (int) (long) k == n.id);
        if (active == n.id) active = -1;
    }

    private static int exportGraph(FabricClientCommandSource s) {
        if (nodes.isEmpty()) {
            msg(s, "§7Graph is empty");
            return 0;
        }
        JsonObject root = new JsonObject();
        for (Node n : nodes) {
            JsonObject o = new JsonObject();
            o.addProperty("Position", n.x + ":" + n.y + ":" + n.z);
            if (n.name != null) o.addProperty("Name", n.name);
            JsonObject nb = new JsonObject();
            for (Node m : nodes) {
                if (m == n || !edges.contains(key(n.id, m.id))) continue;
                double d = Math.sqrt(n.center.distanceToSqr(m.center));
                nb.addProperty(String.valueOf(m.id), Math.round(d * 100) / 100.0);
            }
            o.add("Neighbours", nb);
            root.add(String.valueOf(n.id), o);
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(root.toString());
        msg(s, "§7Copied graph: §f" + nodes.size() + " §7nodes, §f" + edges.size() + " §7edges");
        return 1;
    }

    private static int importGraph(FabricClientCommandSource s) {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        List<Node> in = new ArrayList<>();
        Set<Long> inEdges = new HashSet<>();
        try {
            JsonObject root = JsonParser.parseString(clip).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> e : root.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                String[] p = o.get("Position").getAsString().split(":");
                int id = Integer.parseInt(e.getKey());
                in.add(new Node(id, (int) Math.floor(Double.parseDouble(p[0])), (int) Math.floor(Double.parseDouble(p[1])),
                        (int) Math.floor(Double.parseDouble(p[2])), o.has("Name") ? o.get("Name").getAsString() : null));
                if (o.has("Neighbours"))
                    for (String nb : o.getAsJsonObject("Neighbours").keySet()) inEdges.add(key(id, Integer.parseInt(nb)));
            }
        } catch (Exception e) {
            msg(s, "§cClipboard is not a graph json");
            return 0;
        }
        save();
        nodes = in;
        edges = inEdges;
        nextId = 0;
        for (Node n : in) nextId = Math.max(nextId, n.id + 1);
        active = -1;
        msg(s, "§7Loaded §f" + in.size() + " §7nodes");
        return 1;
    }

    // ---------------------------------------------------------------- render

    private static void render(PoseStack ps, Vec3 cam) {
        TeslaMapsConfig c = cfg();
        if (c.devRegionSelect) {
            if (box1 != null) ESPRenderer.drawBoxOutline(ps, box1, 0xFFFF5555, 2f, cam, true);
            if (box2 != null) ESPRenderer.drawBoxOutline(ps, box2, 0xFF5555FF, 2f, cam, true);
            if (region != null) ESPRenderer.drawFilledBox(ps, region, 0x3C00FFFF, cam, true);
        }
        if (c.devParkourRecorder && !c.devGraphEditor) {
            for (int i = 0; i < parkourBoxes.size(); i++) {
                ESPRenderer.drawFilledBox(ps, parkourBoxes.get(i), 0x6055FF55, cam, true);
                if (i > 0) ESPRenderer.drawLine(ps, parkourCenters.get(i - 1), parkourCenters.get(i), 0xFFFFFF55, 2f, cam);
            }
        }
        if (c.devGraphEditor && !nodes.isEmpty()) {
            for (long k : edges) {
                Node a = node((int) (k >> 32)), b = node((int) k);
                if (a != null && b != null) ESPRenderer.drawLine(ps, a.center, b.center, 0xFFFFAA00, 2f, cam);
            }
            for (Node n : nodes) {
                boolean act = n.id == active;
                ESPRenderer.drawFilledBox(ps, n.box, act ? 0xA055FF55 : n.name != null ? 0xA0FFFF55 : 0x80FFFFFF, cam, true);
                ESPRenderer.drawText(ps, n.label, n.labelPos, 1.0f, cam);
            }
        }
    }
}
