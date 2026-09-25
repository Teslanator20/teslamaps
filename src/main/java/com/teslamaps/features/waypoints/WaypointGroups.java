package com.teslamaps.features.waypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Waypoint groups per island, after Skysoft's Waypoints/WaypointLibrary and Skyblocker's waypoint groups
public final class WaypointGroups {

    public static final class Point {
        public int x, y, z;
        public String name = "";
        public String color;

        public Point() {}

        Point(BlockPos p, String name) {
            x = p.getX();
            y = p.getY();
            z = p.getZ();
            this.name = name == null ? "" : name;
        }
    }

    public static final class Group {
        public String name = "";
        public String island = "";
        public boolean enabled = true;
        public boolean ordered = false;
        public boolean loop = true;
        public String color = "00FFFF";
        public double radius = 3;
        public List<Point> points = new ArrayList<>();
        transient boolean visit;
        transient int index;
    }

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("waypoint_groups.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final List<Group> groups = new ArrayList<>();
    private static final WaypointSet set = new WaypointSet();
    private static Group selected;
    private static boolean dirty = true;
    private static String lastIsland = "";
    private static Object lastLevel;

    private WaypointGroups() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        load();
        Feature.of("WaypointGroups").section("Ordered Waypoints").when(() -> cfg().waypointGroups)
                .tick(WaypointGroups::tick)
                .world(WaypointGroups::render)
                .config("Ordered Waypoints", b -> {
                    b.label("Waypoint Groups");
                    b.toggle("Waypoint Groups", () -> cfg().waypointGroups, v -> cfg().waypointGroups = v)
                            .desc("Named waypoint sets per island, free or as an ordered route. /tmap wpgroup")
                            .tags("waypoints", "groups", "route");
                    b.toggle("  Beam", () -> cfg().waypointGroupsBeam, v -> {
                        cfg().waypointGroupsBeam = v;
                        dirty = true;
                    });
                    b.toggle("  Distance In Label", () -> cfg().waypointGroupsDistance, v -> {
                        cfg().waypointGroupsDistance = v;
                        dirty = true;
                    });
                    b.info("/tmap wpgroup create <name> [ordered|visit], add [label], addlook [label], undo, list");
                    b.info("/tmap wpgroup select|toggle|delete <name>, color <hex>, radius <n>, island <here|any>, skip, back");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("wpgroup")
                        .executes(c -> list(c.getSource()))
                        .then(ClientCommands.literal("list").executes(c -> list(c.getSource())))
                        .then(ClientCommands.literal("create")
                                .then(ClientCommands.argument("name", StringArgumentType.string())
                                        .executes(c -> create(c.getSource(), str(c, "name"), ""))
                                        .then(ClientCommands.argument("mode", StringArgumentType.word())
                                                .suggests((c, sb) -> { sb.suggest("ordered"); sb.suggest("visit"); return sb.buildFuture(); })
                                                .executes(c -> create(c.getSource(), str(c, "name"), str(c, "mode"))))))
                        .then(ClientCommands.literal("select").then(nameArg().executes(c -> select(c.getSource(), str(c, "name")))))
                        .then(ClientCommands.literal("toggle").then(nameArg().executes(c -> toggle(c.getSource(), str(c, "name")))))
                        .then(ClientCommands.literal("delete").then(nameArg().executes(c -> delete(c.getSource(), str(c, "name")))))
                        .then(ClientCommands.literal("add")
                                .executes(c -> add(c.getSource(), false, ""))
                                .then(ClientCommands.argument("label", StringArgumentType.greedyString())
                                        .executes(c -> add(c.getSource(), false, str(c, "label")))))
                        .then(ClientCommands.literal("addlook")
                                .executes(c -> add(c.getSource(), true, ""))
                                .then(ClientCommands.argument("label", StringArgumentType.greedyString())
                                        .executes(c -> add(c.getSource(), true, str(c, "label")))))
                        .then(ClientCommands.literal("undo").executes(c -> undo(c.getSource())))
                        .then(ClientCommands.literal("removenear").executes(c -> removeNear(c.getSource())))
                        .then(ClientCommands.literal("color").then(ClientCommands.argument("hex", StringArgumentType.word())
                                .executes(c -> color(c.getSource(), str(c, "hex"), false))))
                        .then(ClientCommands.literal("pointcolor").then(ClientCommands.argument("hex", StringArgumentType.word())
                                .executes(c -> color(c.getSource(), str(c, "hex"), true))))
                        .then(ClientCommands.literal("radius").then(ClientCommands.argument("blocks", DoubleArgumentType.doubleArg(0.5, 50))
                                .executes(c -> edit(c.getSource(), g -> g.radius = DoubleArgumentType.getDouble(c, "blocks"), "radius set"))))
                        .then(ClientCommands.literal("ordered").executes(c -> edit(c.getSource(), g -> g.ordered = !g.ordered, "ordered toggled")))
                        .then(ClientCommands.literal("loop").executes(c -> edit(c.getSource(), g -> g.loop = !g.loop, "loop toggled")))
                        .then(ClientCommands.literal("island").then(ClientCommands.argument("where", StringArgumentType.greedyString())
                                .executes(c -> island(c.getSource(), str(c, "where")))))
                        .then(ClientCommands.literal("skip").executes(c -> step(c.getSource(), 1)))
                        .then(ClientCommands.literal("back").executes(c -> step(c.getSource(), -1))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<FabricClientCommandSource, String> nameArg() {
        return ClientCommands.argument("name", StringArgumentType.greedyString()).suggests((c, sb) -> {
            String typed = sb.getRemaining().toLowerCase(Locale.ROOT);
            for (Group g : groups) if (g.name.toLowerCase(Locale.ROOT).startsWith(typed) && !g.name.equalsIgnoreCase(typed)) sb.suggest(g.name);
            return sb.buildFuture();
        });
    }

    private static String str(CommandContext<FabricClientCommandSource> c, String name) {
        return StringArgumentType.getString(c, name).trim();
    }

    private static boolean onIsland(Group g, String island) {
        return g.island == null || g.island.isEmpty() || g.island.equalsIgnoreCase(island);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            if (groups.removeIf(g -> g.visit)) {
                if (selected != null && selected.visit) selected = null;
            }
            dirty = true;
        }
        String island = SkyblockUtils.getArea();
        if (!island.equals(lastIsland)) {
            lastIsland = island;
            dirty = true;
        }
        if (mc.player != null) {
            for (Group g : groups) {
                if (!g.enabled || !g.ordered || g.points.isEmpty() || !onIsland(g, island)) continue;
                if (g.index >= g.points.size()) continue;
                Point p = g.points.get(g.index);
                if (mc.player.distanceToSqr(p.x + 0.5, p.y + 0.5, p.z + 0.5) > g.radius * g.radius) continue;
                g.index++;
                if (g.index >= g.points.size() && g.loop) g.index = 0;
                dirty = true;
            }
        }
        if (dirty) rebuild(island);
        set.tick();
    }

    private static void rebuild(String island) {
        dirty = false;
        set.clear();
        set.beam(cfg().waypointGroupsBeam).distance(cfg().waypointGroupsDistance);
        for (Group g : groups) {
            if (!g.enabled || !onIsland(g, island)) continue;
            int base = TeslaMapsConfig.parseColor(g.color) & 0xFFFFFF;
            if (g.ordered) {
                if (g.index >= g.points.size()) continue;
                Point p = g.points.get(g.index);
                set.add(new BlockPos(p.x, p.y, p.z), label(g, p, g.index), color(p, base), 0);
            } else {
                for (int i = 0; i < g.points.size(); i++) {
                    Point p = g.points.get(i);
                    set.add(new BlockPos(p.x, p.y, p.z), label(g, p, i), color(p, base), 0);
                }
            }
        }
    }

    private static String label(Group g, Point p, int i) {
        String n = p.name == null || p.name.isEmpty() ? g.name + " #" + (i + 1) : p.name;
        return g.ordered ? "§e" + (i + 1) + "/" + g.points.size() + " §f" + n : "§f" + n;
    }

    private static int color(Point p, int base) {
        return p.color == null || p.color.isEmpty() ? base : TeslaMapsConfig.parseColor(p.color) & 0xFFFFFF;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        set.render(pose, cam);
    }

    private static Group find(String name) {
        for (Group g : groups) if (g.name.equalsIgnoreCase(name)) return g;
        return null;
    }

    private static int msg(FabricClientCommandSource src, String s) {
        src.sendFeedback(Component.literal("§b[TeslaMaps] §f" + s));
        return 1;
    }

    private static int changed(FabricClientCommandSource src, String s) {
        dirty = true;
        save();
        return msg(src, s);
    }

    private static int list(FabricClientCommandSource src) {
        if (groups.isEmpty()) return msg(src, "No waypoint groups. §7/tmap wpgroup create <name> [ordered|visit]");
        String island = SkyblockUtils.getArea();
        for (Group g : groups) {
            msg(src, (g == selected ? "§a> " : "§7- ") + (g.enabled ? "§f" : "§8") + g.name + " §7(" + g.points.size() + " points, "
                    + (g.island.isEmpty() ? "any island" : g.island) + (g.ordered ? ", ordered" : "") + (g.visit ? ", this visit" : "")
                    + (onIsland(g, island) ? "" : ", other island") + ")");
        }
        return 1;
    }

    private static int create(FabricClientCommandSource src, String name, String mode) {
        if (name.isEmpty()) return msg(src, "§cName needed");
        if (find(name) != null) return msg(src, "§cA group named " + name + " exists");
        Group g = new Group();
        g.name = name;
        g.island = SkyblockUtils.getArea();
        g.ordered = mode.equalsIgnoreCase("ordered");
        g.visit = mode.equalsIgnoreCase("visit");
        groups.add(g);
        selected = g;
        return changed(src, "Created §e" + name + "§f on " + (g.island.isEmpty() ? "any island" : g.island)
                + (g.visit ? " §7(this visit only)" : "") + "§f, now selected");
    }

    private static int select(FabricClientCommandSource src, String name) {
        Group g = find(name);
        if (g == null) return msg(src, "§cNo group " + name);
        selected = g;
        return msg(src, "Selected §e" + g.name);
    }

    private static int toggle(FabricClientCommandSource src, String name) {
        Group g = find(name);
        if (g == null) return msg(src, "§cNo group " + name);
        g.enabled = !g.enabled;
        return changed(src, g.name + (g.enabled ? " §ashown" : " §chidden"));
    }

    private static int delete(FabricClientCommandSource src, String name) {
        Group g = find(name);
        if (g == null) return msg(src, "§cNo group " + name);
        groups.remove(g);
        if (selected == g) selected = null;
        return changed(src, "Deleted §e" + g.name);
    }

    private static Group sel(FabricClientCommandSource src) {
        if (selected == null) msg(src, "§cNo group selected. §7/tmap wpgroup select <name>");
        return selected;
    }

    private static int add(FabricClientCommandSource src, boolean look, String label) {
        Group g = sel(src);
        if (g == null) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        BlockPos pos;
        if (look) {
            if (!(mc.hitResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK)
                return msg(src, "§cNot looking at a block");
            pos = bh.getBlockPos();
        } else {
            pos = mc.player.blockPosition().below();
        }
        g.points.add(new Point(pos, label));
        return changed(src, "Added #" + g.points.size() + " to §e" + g.name + " §7(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");
    }

    private static int undo(FabricClientCommandSource src) {
        Group g = sel(src);
        if (g == null) return 0;
        if (g.points.isEmpty()) return msg(src, "Nothing to undo");
        g.points.removeLast();
        if (g.index >= g.points.size()) g.index = 0;
        return changed(src, "Removed the last point of §e" + g.name);
    }

    private static int removeNear(FabricClientCommandSource src) {
        Group g = sel(src);
        var p = Minecraft.getInstance().player;
        if (g == null || p == null) return 0;
        Point best = null;
        double bd = 25;
        for (Point pt : g.points) {
            double d = p.distanceToSqr(pt.x + 0.5, pt.y + 0.5, pt.z + 0.5);
            if (d < bd) { bd = d; best = pt; }
        }
        if (best == null) return msg(src, "§cNo point within 5 blocks");
        g.points.remove(best);
        if (g.index >= g.points.size()) g.index = 0;
        return changed(src, "Removed the nearest point of §e" + g.name);
    }

    private static int color(FabricClientCommandSource src, String hex, boolean point) {
        Group g = sel(src);
        if (g == null) return 0;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (!h.matches("[0-9a-fA-F]{6}")) return msg(src, "§cUse a 6 digit hex colour");
        if (point) {
            if (g.points.isEmpty()) return msg(src, "§cNo points");
            g.points.getLast().color = h.toUpperCase(Locale.ROOT);
        } else {
            g.color = h.toUpperCase(Locale.ROOT);
        }
        return changed(src, "Colour set");
    }

    private static int edit(FabricClientCommandSource src, java.util.function.Consumer<Group> f, String done) {
        Group g = sel(src);
        if (g == null) return 0;
        f.accept(g);
        return changed(src, g.name + ": " + done + " §7(ordered=" + g.ordered + ", loop=" + g.loop + ", radius=" + g.radius + ")");
    }

    private static int island(FabricClientCommandSource src, String where) {
        Group g = sel(src);
        if (g == null) return 0;
        if (where.equalsIgnoreCase("here")) g.island = SkyblockUtils.getArea();
        else if (where.equalsIgnoreCase("any")) g.island = "";
        else g.island = where;
        return changed(src, g.name + " island: " + (g.island.isEmpty() ? "any" : g.island));
    }

    private static int step(FabricClientCommandSource src, int d) {
        Group g = sel(src);
        if (g == null || g.points.isEmpty()) return 0;
        g.index = Math.floorMod(g.index + d, g.points.size());
        dirty = true;
        return msg(src, g.name + ": point " + (g.index + 1) + "/" + g.points.size());
    }

    private static void load() {
        try {
            if (!Files.exists(FILE)) return;
            List<Group> l = GSON.fromJson(Files.readString(FILE), new TypeToken<List<Group>>() {}.getType());
            if (l == null) return;
            for (Group g : l) {
                if (g == null) continue;
                if (g.name == null) g.name = "";
                if (g.island == null) g.island = "";
                if (g.points == null) g.points = new ArrayList<>();
                g.points.removeIf(java.util.Objects::isNull);
                groups.add(g);
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[WaypointGroups] load failed: {}", e.toString());
        }
    }

    private static void save() {
        try {
            List<Group> keep = new ArrayList<>();
            for (Group g : groups) if (!g.visit) keep.add(g);
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(keep));
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[WaypointGroups] save failed: {}", e.toString());
        }
    }
}
