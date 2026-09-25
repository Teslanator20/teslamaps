package com.teslamaps.features.mining;

// After Skyblocker's CrystalsLocationsManager.java, SBE's $SBECHWP and Skytils' CHWaypoints.kt
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.Announce;
import com.teslamaps.features.WorldScanner;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.HypixelModApi;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Named Crystal Hollows waypoints from party chat, commands and subzones, shareable and kept per lobby. */
public final class HollowsWaypoints {

    private record Loc(String name, int color, String... keys) {}

    private static final Loc[] LOCS = {
            new Loc("Jungle Temple", 0xAA00AA, "temple", "jungle temple"),
            new Loc("Mines of Divan", 0x55FF55, "divan", "mines of divan"),
            new Loc("Goblin Queen's Den", 0xFFAA00, "queen", "den"),
            new Loc("Lost Precursor City", 0x55FFFF, "precursor", "city"),
            new Loc("Khazad-dûm", 0xFFFF55, "khazad", "bal"),
            new Loc("Fairy Grotto", 0xFF55FF, "grotto", "fairy"),
            new Loc("Dragon's Lair", 0xFFAA00, "dragon", "lair"),
            new Loc("Corleone", 0xFFFFFF, "corleone", "boss"),
            new Loc("King Yolkar", 0xFF5555, "yolkar", "king"),
            new Loc("Odawa", 0xFF55FF, "odawa"),
            new Loc("Key Guardian", 0xAAAAAA, "guardian"),
            new Loc("Xalx", 0x55FF55, "xalx"),
    };
    private static final Set<String> SUBZONES = Set.of("Jungle Temple", "Mines of Divan", "Goblin Queen's Den",
            "Lost Precursor City", "Khazad-dûm", "Fairy Grotto", "Dragon's Lair");
    private static final Pattern SBE = Pattern.compile("\\$SBECHWP:(.+?)@(-?\\d+),(-?\\d+),(-?\\d+)");
    private static final Pattern SERVER = Pattern.compile("\\d\\d/\\d\\d/\\d\\d\\s+(\\S+)");
    private static final long KEEP_MS = 24 * 60 * 60 * 1000L;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("hollows_waypoints.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class Saved {
        long t;
        List<Point> p = new ArrayList<>();
    }

    private static final class Point {
        String n;
        int x, y, z;
    }

    private static final Map<String, BlockPos> points = new LinkedHashMap<>();
    private static final Set<String> hidden = new HashSet<>();
    private static final Map<String, String> labels = new HashMap<>();
    private static Map<String, Saved> store;
    private static String lobby;
    private static Object lastLevel;
    private static boolean inHollows;
    private static int ticks;

    private HollowsWaypoints() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean on() {
        TeslaMapsConfig c = cfg();
        return c.chScanner && c.chWaypoints;
    }

    public static void register() {
        Feature.of("HollowsWaypoints").section("Mining").when(HollowsWaypoints::on)
                .tick(HollowsWaypoints::tick)
                .world(HollowsWaypoints::render)
                .chat(HollowsWaypoints::onChat);

        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("chwp")
                        .executes(c -> list())
                        .then(ClientCommands.literal("list").executes(c -> list()))
                        .then(ClientCommands.literal("clear").executes(c -> {
                            points.clear();
                            save();
                            msg("§fCrystal Hollows waypoints cleared");
                            return 1;
                        }))
                        .then(ClientCommands.literal("add")
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .suggests((c, sb) -> suggest(sb, false))
                                        .executes(c -> {
                                            var p = Minecraft.getInstance().player;
                                            if (p == null) return 0;
                                            return add(StringArgumentType.getString(c, "name"), p.blockPosition(), true);
                                        })))
                        .then(ClientCommands.literal("addat")
                                .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                                        .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                                                .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                                                .suggests((c, sb) -> suggest(sb, false))
                                                                .executes(c -> add(StringArgumentType.getString(c, "name"),
                                                                        new BlockPos(IntegerArgumentType.getInteger(c, "x"),
                                                                                IntegerArgumentType.getInteger(c, "y"),
                                                                                IntegerArgumentType.getInteger(c, "z")), true)))))))
                        .then(ClientCommands.literal("remove")
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .suggests((c, sb) -> suggest(sb, true))
                                        .executes(c -> remove(StringArgumentType.getString(c, "name")))))
                        .then(ClientCommands.literal("share")
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .suggests((c, sb) -> suggest(sb, true))
                                        .executes(c -> share(StringArgumentType.getString(c, "name"))))))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(
            com.mojang.brigadier.suggestion.SuggestionsBuilder sb, boolean existing) {
        String rem = sb.getRemaining().toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        if (existing) names.addAll(points.keySet());
        else for (Loc l : LOCS) names.add(l.name());
        for (String n : names) {
            String low = n.toLowerCase(Locale.ROOT);
            if (low.startsWith(rem) && !low.equals(rem)) sb.suggest(n);
        }
        return sb.buildFuture();
    }

    private static void msg(String s) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }

    private static String key(String name) {
        for (String k : points.keySet()) if (k.equalsIgnoreCase(name.trim())) return k;
        return null;
    }

    private static String canonical(String name) {
        for (Loc l : LOCS) if (l.name().equalsIgnoreCase(name.trim())) return l.name();
        return name.trim();
    }

    private static int add(String name, BlockPos pos, boolean feedback) {
        if (!on()) {
            if (feedback) msg("§7Turn on §fNamed Waypoints §7under Crystal Hollows Scanner first");
            return 0;
        }
        String n = canonical(name);
        String old = key(n);
        if (old != null) points.remove(old);
        points.put(n, pos.immutable());
        save();
        if (feedback) msg("§f" + n + " §7set at §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        return 1;
    }

    private static int remove(String name) {
        String k = key(name);
        if (k == null) {
            msg("§cNo waypoint named " + name);
            return 0;
        }
        points.remove(k);
        save();
        msg("§f" + k + " §7removed");
        return 1;
    }

    private static int share(String name) {
        String k = key(name);
        if (k == null) {
            msg("§cNo waypoint named " + name);
            return 0;
        }
        BlockPos p = points.get(k);
        Announce.party(k + " " + p.getX() + " " + p.getY() + " " + p.getZ());
        return 1;
    }

    private static int list() {
        if (points.isEmpty()) {
            msg("§7No named Crystal Hollows waypoints. §f/tmap chwp add <name>");
            return 1;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;
        for (Map.Entry<String, BlockPos> e : points.entrySet()) {
            BlockPos p = e.getValue();
            String n = e.getKey();
            MutableComponent line = Component.literal("§b[TeslaMaps] §f" + n + " §7" + p.getX() + " " + p.getY() + " " + p.getZ() + " ");
            line.append(Component.literal("§a[Share]").withStyle(s -> s
                    .withClickEvent(new ClickEvent.RunCommand("/tmap chwp share " + n))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Send to party chat")))));
            line.append(Component.literal(" "));
            line.append(Component.literal("§c[Remove]").withStyle(s -> s
                    .withClickEvent(new ClickEvent.RunCommand("/tmap chwp remove " + n))));
            player.sendSystemMessage(line);
        }
        return 1;
    }

    private static boolean inBounds(BlockPos p) {
        return p.getX() >= 202 && p.getX() <= 823 && p.getZ() >= 202 && p.getZ() <= 823 && p.getY() >= 31 && p.getY() <= 188;
    }

    private static void onChat(String text) {
        if (!inHollows || !cfg().chWaypointsFromChat) return;
        String plain = StringUtil.strip(text);
        if (plain.startsWith("[TeslaMaps]")) return;
        Matcher sbe = SBE.matcher(plain);
        if (sbe.find()) {
            try {
                BlockPos p = new BlockPos(Integer.parseInt(sbe.group(2)), Integer.parseInt(sbe.group(3)), Integer.parseInt(sbe.group(4)));
                if (inBounds(p) && key(sbe.group(1)) == null) add(sbe.group(1), p, true);
            } catch (NumberFormatException ignored) {}
            return;
        }
        if (StringUtil.isServerMessage(text)) return;
        int colon = plain.indexOf(": ");
        if (colon < 0) return;
        String body = plain.substring(colon + 2);
        BlockPos p = WaypointSet.fromChatLoose(body);
        if (p == null || !inBounds(p)) return;
        String low = body.toLowerCase(Locale.ROOT);
        Set<String> words = new HashSet<>();
        for (String w : low.split("[^a-z'û-]+")) words.add(w.endsWith("'s") ? w.substring(0, w.length() - 2) : w);
        for (Loc l : LOCS) {
            for (String k : l.keys()) {
                if (k.indexOf(' ') >= 0 ? low.contains(k) : words.contains(k)) {
                    if (key(l.name()) == null) add(l.name(), p, true);
                    return;
                }
            }
        }
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        String at = p.getX() + " " + p.getY() + " " + p.getZ();
        MutableComponent line = Component.literal("§b[TeslaMaps] §7Mark §f" + at + " §7as: ");
        for (Loc l : LOCS) {
            if (key(l.name()) != null) continue;
            line.append(Component.literal("[" + l.name() + "] ").withColor(l.color()).withStyle(s -> s
                    .withClickEvent(new ClickEvent.RunCommand("/tmap chwp addat " + at + " " + l.name()))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Add waypoint")))));
        }
        Minecraft.getInstance().execute(() -> player.sendSystemMessage(line));
    }

    private static String lobbyId() {
        String s = HypixelModApi.serverName();
        if (s != null && !s.isEmpty()) return s;
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            if (line == null) continue;
            Matcher m = SERVER.matcher(StringUtil.stripTrim(line));
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            save();
            points.clear();
            hidden.clear();
            lobby = null;
            inHollows = false;
        }
        if (inHollows && mc.player != null) {
            labels.clear();
            for (Map.Entry<String, BlockPos> e : points.entrySet()) {
                BlockPos p = e.getValue();
                int dist = (int) Math.sqrt(mc.player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
                labels.put(e.getKey(), "§b" + e.getKey() + " §7" + dist + "m");
            }
        }
        if (++ticks % 20 != 0) return;
        inHollows = SkyblockUtils.isInArea("Crystal Hollows");
        if (!inHollows) return;
        if (lobby == null) {
            lobby = lobbyId();
            if (lobby != null && cfg().chWaypointsPersist) load();
        }
        if (cfg().chWaypointsSubzone) subzone(mc);
        hidden.clear();
        if (!points.isEmpty()) {
            WorldScanner.visitScanned((name, pos, color) -> {
                for (Map.Entry<String, BlockPos> e : points.entrySet()) {
                    if (e.getValue().distSqr(pos) < 48 * 48 && sameArea(e.getKey(), name)) hidden.add(e.getKey());
                }
            });
        }
    }

    private static boolean sameArea(String mine, String scanned) {
        String a = mine.toLowerCase(Locale.ROOT), b = scanned.toLowerCase(Locale.ROOT);
        if (a.contains(b) || b.contains(a)) return true;
        return (a.startsWith("khazad") && b.equals("bal")) || (a.startsWith("king") && b.equals("king"));
    }

    private static void subzone(Minecraft mc) {
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            if (line == null || line.indexOf('⏣') < 0) continue;
            String area = StringUtil.stripTrim(line).replace("⏣", "").trim();
            for (String s : SUBZONES) {
                if (s.equalsIgnoreCase(area) && key(s) == null) add(s, mc.player.blockPosition(), true);
            }
            return;
        }
    }

    private static void load() {
        if (store == null) {
            store = new HashMap<>();
            try {
                if (Files.exists(FILE)) {
                    Map<String, Saved> read = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8),
                            new TypeToken<Map<String, Saved>>() {}.getType());
                    if (read != null) store.putAll(read);
                }
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("hollows_waypoints.json unreadable: {}", e.toString());
            }
            long now = System.currentTimeMillis();
            store.values().removeIf(s -> s == null || s.p == null || now - s.t > KEEP_MS);
        }
        Saved s = store.get(lobby);
        if (s == null) return;
        for (Point p : s.p) {
            if (p.n != null && key(p.n) == null) points.put(p.n, new BlockPos(p.x, p.y, p.z));
        }
        if (!s.p.isEmpty()) msg("§7Restored §f" + s.p.size() + " §7Crystal Hollows waypoints for this lobby");
    }

    private static void save() {
        if (lobby == null || !cfg().chWaypointsPersist) return;
        if (store == null) load();
        Saved s = new Saved();
        s.t = System.currentTimeMillis();
        for (Map.Entry<String, BlockPos> e : points.entrySet()) {
            Point p = new Point();
            p.n = e.getKey();
            p.x = e.getValue().getX();
            p.y = e.getValue().getY();
            p.z = e.getValue().getZ();
            s.p.add(p);
        }
        if (s.p.isEmpty()) store.remove(lobby);
        else store.put(lobby, s);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(store), StandardCharsets.UTF_8);
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("hollows_waypoints.json not saved: {}", e.toString());
        }
    }

    private static int color(String name) {
        for (Loc l : LOCS) if (l.name().equals(name)) return 0xDC000000 | l.color();
        return 0xDC55FFFF;
    }

    /** Own waypoints for the Hollows minimap. */
    public static void visit(WorldScanner.Visitor v) {
        if (!inHollows || !on()) return;
        for (Map.Entry<String, BlockPos> e : points.entrySet()) {
            if (!hidden.contains(e.getKey())) v.visit(e.getKey(), e.getValue(), color(e.getKey()));
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!inHollows || points.isEmpty()) return;
        boolean tracers = cfg().chScanTracers;
        for (Map.Entry<String, BlockPos> e : points.entrySet()) {
            if (hidden.contains(e.getKey())) continue;
            BlockPos p = e.getValue();
            int col = color(e.getKey());
            ESPRenderer.drawBoxOutline(pose, new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1), col, 1.5f, cam);
            Vec3 center = new Vec3(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            String label = labels.get(e.getKey());
            if (label != null) ESPRenderer.drawText(pose, label, new Vec3(center.x, p.getY() + 1.5, center.z), 2f, cam);
            if (tracers) ESPRenderer.drawTracerFromCamera(pose, center, col, cam);
        }
    }
}
