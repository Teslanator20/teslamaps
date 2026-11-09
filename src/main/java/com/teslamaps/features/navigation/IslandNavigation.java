package com.teslamaps.features.navigation;

// Ported from SkyHanni's IslandGraphs / NavigationHelper / NavigateAllApi / NavigateAllCommand /
// NavigateClipboardCommand / IslandAreaBackend; graphs from SkyHanni-REPO constants/island_graphs
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.RepoCache;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IslandNavigation {

    private static final String URL = "https://raw.githubusercontent.com/hannibal002/SkyHanni-REPO/main/constants/island_graphs/%s.json";
    private static final double REACHED = 3.0;
    private static final List<String> SAMPLE = List.of("§aVillage", "§7Farm §8(84m)", "§7Mountain §8(132m)");

    private static final Map<String, String> ISLANDS = Map.ofEntries(
            Map.entry("Hub", "HUB"), Map.entry("Dark Auction", "DARK_AUCTION"), Map.entry("Jerry's Workshop", "WINTER"),
            Map.entry("The Farming Islands", "THE_FARMING_ISLANDS"), Map.entry("Gold Mine", "GOLD_MINES"),
            Map.entry("Deep Caverns", "DEEP_CAVERNS"), Map.entry("Dwarven Mines", "DWARVEN_MINES"),
            Map.entry("Crystal Hollows", "CRYSTAL_HOLLOWS"), Map.entry("Backwater Bayou", "BACKWATER_BAYOU"),
            Map.entry("Lotus Atoll", "LOTUS_ATOLL"), Map.entry("The Park", "THE_PARK"), Map.entry("Galatea", "GALATEA"),
            Map.entry("Moonglade Marsh", "GALATEA"), Map.entry("Torrhus Canyon", "TORRHUS_CANYON"),
            Map.entry("Spider's Den", "SPIDER_DEN"), Map.entry("The End", "THE_END"), Map.entry("Crimson Isle", "CRIMSON_ISLE"),
            Map.entry("Dungeon Hub", "DUNGEON_HUB"), Map.entry("Kuudra", "KUUDRA_ARENA"), Map.entry("The Rift", "THE_RIFT"),
            Map.entry("Critter Safari", "SAFARI"), Map.entry("Safari", "SAFARI"));
    private static final Set<String> GLACITE = Set.of("Glacite Tunnels", "Dwarven Base Camp", "Great Glacite Lake", "Fossil Research Center");

    // SkyHanni's allowedMultiNavigationTags, id -> display name
    private static final Map<String, String> MULTI_TAGS = new LinkedHashMap<>();
    static {
        MULTI_TAGS.put("hoppity", "Hoppity Egg");
        MULTI_TAGS.put("rift_effigy", "Blood Effigies");
        MULTI_TAGS.put("rift_montezuma", "Montezuma Soul Piece");
        MULTI_TAGS.put("crimson_miniboss", "Crimson Miniboss");
        MULTI_TAGS.put("SPIDER_RELIC", "Spider's Relic");
        MULTI_TAGS.put("end_golem", "Golem Spawn");
        MULTI_TAGS.put("fishing_hotspot", "Fishing Hotspot");
        MULTI_TAGS.put("fishing_wormhole", "Fishing Wormhole");
        MULTI_TAGS.put("fairy_soul", "Fairy Soul");
        MULTI_TAGS.put("hideonleaf", "Hideonleaf");
        MULTI_TAGS.put("hideonsun", "Hideonsun");
        MULTI_TAGS.put("tree_protection_order", "Tree Protection Order");
        MULTI_TAGS.put("honeyhive", "Honeyhive");
        MULTI_TAGS.put("safari_bell", "Safari Bell");
        MULTI_TAGS.put("hideyho", "Hideyho Location");
        MULTI_TAGS.put("pangolin_area", "Pangolin");
        MULTI_TAGS.put("sanger_area", "Sanger");
        MULTI_TAGS.put("floor_drops", "Floor Drops");
    }

    private static final Pattern COORDS = Pattern.compile("(-?\\d+(?:\\.\\d+)?)[,\\s:;/]+(-?\\d+(?:\\.\\d+)?)[,\\s:;/]+(-?\\d+(?:\\.\\d+)?)");

    public static final class Node {
        public final int id;
        public final Vec3 pos;
        public final String name, clean, label;
        public final Set<String> tags;
        public int[] nb = new int[0];
        public double[] w = new double[0];

        Node(int id, Vec3 pos, String name, Set<String> tags) {
            this.id = id;
            this.pos = pos;
            this.name = name;
            this.clean = name == null ? null : StringUtil.stripTrim(name);
            this.label = "§b" + clean;
            this.tags = tags;
        }

        boolean area() { return clean != null && (tags.contains("area") || tags.contains("small_area")); }
    }

    record Stop(int node, Vec3 pos, String label) {}
    record Area(String name, int node, double dist) {}

    private static String loadedKey;
    private static Node[] graph;
    private static int timer;
    private static Object lastLevel;

    private static List<Stop> route = List.of();
    private static int routeIndex;
    private static String routeName;
    private static int routeId;
    private static List<Vec3> path;
    private static int lastClosest = -1;
    private static boolean noPathWarned;
    private static String targetLabel;

    private static List<Area> areas = List.of();
    private static String currentArea = "";
    private static List<String> hudLines = List.of();
    private static int hudWidth;
    private static HudEditElement hud;

    private IslandNavigation() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean enabled() {
        return cfg().islandNav && cfg().section("Ordered Waypoints");
    }

    private static boolean wantAreas() {
        TeslaMapsConfig c = cfg();
        return c.islandNavAreaHud || c.islandNavAreaTitle || c.islandNavAreaLabels;
    }

    public static void register() {
        hud = HudEditElement.of("islandNavHud", "Island Areas").section("Ordered Waypoints")
                .enabled(() -> cfg().islandNav && cfg().islandNavAreaHud)
                .size(() -> width(SAMPLE), () -> SAMPLE.size() * 10 + 6)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, width(SAMPLE)));
        Feature.of("IslandNavigation").section("Ordered Waypoints").when(() -> cfg().islandNav)
                .tick(IslandNavigation::tick)
                .world(IslandNavigation::render)
                .hudHiddenInInventory("islandnav", IslandNavigation::renderHud)
                .config("Ordered Waypoints", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Island Navigation");
                    b.toggle("Island Navigation", () -> c.islandNav, v -> c.islandNav = v)
                            .desc("Path finding over SkyHanni's island graphs. /tmap navigate <place>, /tmap navigate areas, /tmap navigateall <type>, /tmap navclipboard.");
                    b.color("  Path Color", () -> c.islandNavColor, v -> c.islandNavColor = v);
                    b.toggle("  Area List HUD", () -> c.islandNavAreaHud, v -> c.islandNavAreaHud = v);
                    b.toggle("  Title On New Area", () -> c.islandNavAreaTitle, v -> c.islandNavAreaTitle = v);
                    b.toggle("  Area Names In World", () -> c.islandNavAreaLabels, v -> c.islandNavAreaLabels = v);
                    b.button("  Stop Navigation", () -> stop(true));
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("navigate")
                        .executes(c -> usage(c.getSource()))
                        .then(ClientCommands.literal("stop").executes(c -> {
                            stop(true);
                            return 1;
                        }))
                        .then(ClientCommands.literal("areas").executes(c -> listAreas(c.getSource())))
                        .then(ClientCommands.argument("place", StringArgumentType.greedyString())
                                .suggests((c, sb) -> {
                                    String typed = sb.getRemaining().toLowerCase(Locale.ROOT);
                                    Node[] g = graph;
                                    if (g == null) return sb.buildFuture();
                                    Set<String> seen = new HashSet<>();
                                    for (Node n : g) {
                                        if (n.clean == null || n.clean.isEmpty()) continue;
                                        String low = n.clean.toLowerCase(Locale.ROOT);
                                        if (low.contains(typed) && !low.equals(typed) && seen.add(low) && seen.size() <= 40) sb.suggest(n.clean);
                                    }
                                    return sb.buildFuture();
                                })
                                .executes(c -> navigateTo(c.getSource(), StringArgumentType.getString(c, "place")))))
                .then(ClientCommands.literal("navigateall")
                        .executes(c -> usageAll(c.getSource()))
                        .then(ClientCommands.literal("skip").executes(c -> skip(c.getSource())))
                        .then(ClientCommands.literal("undo").executes(c -> undo(c.getSource())))
                        .then(ClientCommands.literal("stop").executes(c -> {
                            stop(true);
                            return 1;
                        }))
                        .then(ClientCommands.argument("type", StringArgumentType.greedyString())
                                .suggests((c, sb) -> {
                                    for (String t : availableTags()) sb.suggest(MULTI_TAGS.get(t).replace(' ', '_'));
                                    return sb.buildFuture();
                                })
                                .executes(c -> navigateAll(c.getSource(), StringArgumentType.getString(c, "type")))))
                .then(ClientCommands.literal("navclipboard").executes(c -> clipboard(c.getSource())))));
    }

    // ---- loading ----

    private static String islandKey() {
        if (!SkyblockUtils.isInSkyblock()) return null;
        String area = SkyblockUtils.getArea();
        String key = ISLANDS.get(area);
        if ("DWARVEN_MINES".equals(key)) {
            for (String line : ScoreboardUtils.getScoreboardLines()) {
                if (line == null || line.indexOf('⏣') < 0) continue;
                String clean = StringUtil.stripTrim(line).replace("⏣", "").trim();
                if (GLACITE.contains(clean)) return "GLACITE_TUNNELS";
                break;
            }
        }
        return key;
    }

    private static final Map<String, Node[]> GRAPHS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Set<String> REQUESTED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Cached SkyHanni graph for an island key, null until the background load is done. */
    public static Node[] graph(String key) {
        Node[] g = GRAPHS.get(key);
        if (g != null || !REQUESTED.add(key)) return g;
        CompletableFuture.runAsync(() -> {
            try {
                GRAPHS.put(key, parse(RepoCache.fetch("sh-graph-" + key, String.format(URL, key), 15).getAsJsonObject()));
            } catch (Exception e) {
                TeslaMaps.LOGGER.info("[Navigation] no island graph for {}: {}", key, e.toString());
            }
        });
        return null;
    }

    private static void load(String key) {
        Node[] cached = GRAPHS.get(key);
        if (cached != null) {
            graph = cached;
            lastClosest = -1;
            return;
        }
        CompletableFuture.runAsync(() -> {
            Node[] g = null;
            try {
                g = parse(RepoCache.fetch("sh-graph-" + key, String.format(URL, key), 15).getAsJsonObject());
            } catch (Exception e) {
                TeslaMaps.LOGGER.info("[Navigation] no island graph for {}: {}", key, e.toString());
            }
            Node[] fin = g;
            if (g != null) GRAPHS.put(key, g);
            Minecraft.getInstance().execute(() -> {
                if (key.equals(loadedKey)) {
                    graph = fin;
                    lastClosest = -1;
                }
            });
        });
    }

    private static Node[] parse(JsonObject json) {
        List<Node> nodes = new ArrayList<>();
        Map<Integer, Integer> index = new HashMap<>();
        List<JsonObject> raw = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            int id;
            try {
                id = Integer.parseInt(e.getKey());
            } catch (NumberFormatException ex) {
                continue;
            }
            if (!e.getValue().isJsonObject()) continue;
            JsonObject o = e.getValue().getAsJsonObject();
            if (!o.has("Position")) continue;
            String[] p = o.get("Position").getAsString().split(":");
            Vec3 pos = new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
            Set<String> tags = new HashSet<>();
            if (o.has("Tags")) for (JsonElement t : o.getAsJsonArray("Tags")) tags.add(t.getAsString());
            String name = o.has("Name") ? o.get("Name").getAsString() : null;
            index.put(id, nodes.size());
            nodes.add(new Node(id, pos, name, tags));
            raw.add(o);
        }
        for (int i = 0; i < nodes.size(); i++) {
            JsonObject o = raw.get(i);
            if (!o.has("Neighbours")) continue;
            List<int[]> ids = new ArrayList<>();
            List<Double> ws = new ArrayList<>();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("Neighbours").entrySet()) {
                Integer j;
                try {
                    j = index.get(Integer.parseInt(e.getKey()));
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (j == null) continue;
                ids.add(new int[]{j});
                ws.add(e.getValue().getAsDouble());
            }
            Node n = nodes.get(i);
            n.nb = new int[ids.size()];
            n.w = new double[ids.size()];
            for (int k = 0; k < ids.size(); k++) {
                n.nb[k] = ids.get(k)[0];
                n.w[k] = ws.get(k);
            }
        }
        return nodes.toArray(new Node[0]);
    }

    // ---- graph ----

    public static int closest(Node[] g, Vec3 p) {
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < g.length; i++) {
            double d = g[i].pos.distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    /** dist in [0], prev in [1] (as doubles). */
    public static double[][] dijkstra(Node[] g, int start) {
        double[] dist = new double[g.length];
        double[] prev = new double[g.length];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        Arrays.fill(prev, -1);
        dist[start] = 0;
        PriorityQueue<double[]> q = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        q.add(new double[]{0, start});
        while (!q.isEmpty()) {
            double[] cur = q.poll();
            int u = (int) cur[1];
            if (cur[0] > dist[u]) continue;
            Node n = g[u];
            for (int k = 0; k < n.nb.length; k++) {
                int v = n.nb[k];
                double nd = cur[0] + n.w[k];
                if (nd < dist[v]) {
                    dist[v] = nd;
                    prev[v] = u;
                    q.add(new double[]{nd, v});
                }
            }
        }
        return new double[][]{dist, prev};
    }

    // ---- tick / render ----

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            if (!route.isEmpty()) msg("§eNavigation stopped because of world switch.");
            stop(false);
            loadedKey = null;
            graph = null;
            areas = List.of();
            currentArea = "";
            hudLines = List.of();
            timer = 0;
        }
        timer++;
        if (timer % 20 == 1) {
            String key = islandKey();
            if (!Objects.equals(key, loadedKey)) {
                loadedKey = key;
                graph = null;
                if (!route.isEmpty()) stop(false);
                if (key != null) load(key);
            }
        }
        Node[] g = graph;
        if (g == null || g.length == 0 || mc.player == null || timer % 5 != 0) return;

        Vec3 me = mc.player.position();
        boolean navigating = !route.isEmpty() && routeIndex < route.size();
        if (navigating) {
            Stop s = route.get(routeIndex);
            if (me.distanceTo(s.pos()) < REACHED) {
                reached();
                return;
            }
        }
        if (!navigating && !wantAreas()) return;
        int start = closest(g, me);
        if (start < 0 || (start == lastClosest && (!navigating || path != null))) {
            if (navigating) updateLabel(me);
            return;
        }
        lastClosest = start;
        double[][] dp = dijkstra(g, start);
        if (navigating) buildPath(g, dp, route.get(routeIndex));
        if (wantAreas()) updateAreas(g, dp[0]);
        if (navigating) updateLabel(me);
    }

    private static void buildPath(Node[] g, double[][] dp, Stop s) {
        List<Vec3> out = new ArrayList<>();
        int t = s.node();
        if (Double.isInfinite(dp[0][t])) {
            if (!noPathWarned) msg("§cNo path found to " + s.label() + "§c, drawing a straight line.");
            noPathWarned = true;
            out.add(s.pos());
            path = out;
            return;
        }
        for (int v = t; v >= 0; v = (int) dp[1][v]) out.add(g[v].pos.add(0.5, 0.5, 0.5));
        java.util.Collections.reverse(out);
        out.add(s.pos());
        path = out;
    }

    private static void updateLabel(Vec3 me) {
        if (route.isEmpty() || routeIndex >= route.size()) return;
        Stop s = route.get(routeIndex);
        String count = route.size() > 1 ? " §7" + (routeIndex + 1) + "/" + route.size() : "";
        targetLabel = "§e" + s.label() + count + " §7(" + (int) me.distanceTo(s.pos()) + "m)";
    }

    private static void updateAreas(Node[] g, double[] dist) {
        List<Area> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Integer[] order = new Integer[g.length];
        int n = 0;
        for (int i = 0; i < g.length; i++) if (g[i].area() && !Double.isInfinite(dist[i])) order[n++] = i;
        Integer[] sorted = Arrays.copyOf(order, n);
        Arrays.sort(sorted, (a, b) -> Double.compare(dist[a], dist[b]));
        for (int i : sorted) if (seen.add(g[i].clean)) list.add(new Area(g[i].clean, i, dist[i]));
        areas = list;
        String now = list.isEmpty() ? "" : list.get(0).name();
        if (!now.equals(currentArea)) {
            String old = currentArea;
            currentArea = now;
            if (cfg().islandNavAreaTitle && !now.isEmpty() && !old.isEmpty()) {
                Minecraft mc = Minecraft.getInstance();
                mc.gui.hud.setTimes(5, 30, 10);
                mc.gui.hud.setTitle(Component.literal(""));
                mc.gui.hud.setSubtitle(Component.literal("§aEntered " + now));
            }
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < list.size() && i < 6; i++) {
            Area a = list.get(i);
            lines.add(i == 0 ? "§a" + a.name() : "§7" + a.name() + " §8(" + (int) a.dist() + "m)");
        }
        hudLines = lines;
        hudWidth = width(lines);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        Node[] g = graph;
        if (mc.player == null) return;
        List<Vec3> p = path;
        if (p != null && !route.isEmpty()) {
            int color = TeslaMapsConfig.parseColor(cfg().islandNavColor);
            Vec3 prev = mc.player.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)).add(0, 0.2, 0);
            for (Vec3 v : p) {
                ESPRenderer.drawLine(pose, prev, v, color, 3f, cam, true);
                prev = v;
            }
            String label = targetLabel;
            if (label != null) ESPRenderer.drawText(pose, label, p.get(p.size() - 1).add(0, 1.5, 0), 1f, cam);
        }
        if (g != null && cfg().islandNavAreaLabels) {
            Vec3 me = mc.player.position();
            List<Area> list = areas;
            for (int i = 1; i < list.size(); i++) {
                Node n = g[list.get(i).node()];
                if (n.pos.distanceToSqr(me) > 64 * 64) continue;
                ESPRenderer.drawText(pose, n.label, n.pos.add(0.5, 2.5, 0.5), 1f, cam);
            }
        }
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> ls = hudLines;
        if (ls.isEmpty() || hud == null || !cfg().islandNavAreaHud) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), ls, hudWidth);
    }

    private static int width(List<String> ls) {
        int w = 0;
        for (String l : ls) w = Math.max(w, Minecraft.getInstance().font.width(l));
        return w + 8;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> ls, int w) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w, ls.size() * 10 + 6);
        for (int i = 0; i < ls.size(); i++) g.text(mc.font, ls.get(i), 4, 4 + i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }

    // ---- navigation control ----

    private static void start(List<Stop> stops, String name) {
        route = stops;
        routeIndex = 0;
        routeName = name;
        routeId++;
        path = null;
        lastClosest = -1;
        noPathWarned = false;
        targetLabel = null;
    }

    /** Path to a point on the current island; false when navigation is off or the graph is not loaded. */
    public static boolean navigate(Vec3 pos, String label) {
        Node[] g = graph;
        if (!enabled() || g == null || g.length == 0) return false;
        int n = closest(g, pos);
        if (n < 0) return false;
        start(List.of(new Stop(n, pos, label)), label);
        return true;
    }

    private static void stop(boolean manual) {
        if (manual && !route.isEmpty()) msg("§eNavigation stopped.");
        route = List.of();
        routeIndex = 0;
        routeName = null;
        routeId++;
        path = null;
        targetLabel = null;
    }

    private static void reached() {
        Stop s = route.get(routeIndex);
        if (route.size() == 1) {
            msg("§aArrived at " + s.label() + "§a.");
            stop(false);
            return;
        }
        routeIndex++;
        if (routeIndex >= route.size()) {
            msg("§aReached all " + route.size() + " " + routeName + "§a.");
            stop(false);
            return;
        }
        path = null;
        lastClosest = -1;
        noPathWarned = false;
    }

    private static List<Stop> optimize(Node[] g, List<Stop> stops, int from) {
        List<Stop> left = new ArrayList<>(stops);
        List<Stop> out = new ArrayList<>();
        int cur = from;
        while (!left.isEmpty()) {
            double[] dist = dijkstra(g, cur)[0];
            int best = 0;
            for (int i = 1; i < left.size(); i++) if (dist[left.get(i).node()] < dist[left.get(best).node()]) best = i;
            Stop s = left.remove(best);
            out.add(s);
            cur = s.node();
        }
        return out;
    }

    private static void startOptimized(Node[] g, List<Stop> stops, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int from = closest(g, mc.player.position());
        int id = ++routeId;
        CompletableFuture.runAsync(() -> {
            List<Stop> ordered = optimize(g, stops, from);
            mc.execute(() -> {
                if (id != routeId || g != graph) return;
                start(ordered, name);
                msg("§aNavigating to " + ordered.size() + " " + name + "§a. §7/tmap navigateall skip | undo | stop");
            });
        });
    }

    // ---- commands ----

    private static Node[] graphOrError(FabricClientCommandSource src) {
        if (!enabled()) {
            src.sendFeedback(Component.literal("§cTurn on Island Navigation (Ordered Waypoints) first."));
            return null;
        }
        Node[] g = graph;
        if (g == null || g.length == 0) {
            src.sendFeedback(Component.literal("§cNo navigation graph for this island."));
            return null;
        }
        return g;
    }

    private static int usage(FabricClientCommandSource src) {
        src.sendFeedback(Component.literal("§b[TeslaMaps] §f/tmap navigate <place> §7| §fstop §7| §fareas"));
        return 1;
    }

    private static int usageAll(FabricClientCommandSource src) {
        Set<String> tags = availableTags();
        List<String> names = new ArrayList<>();
        for (String t : tags) names.add(MULTI_TAGS.get(t));
        src.sendFeedback(Component.literal("§b[TeslaMaps] §f/tmap navigateall <type> §7| §fskip §7| §fundo §7| §fstop"));
        src.sendFeedback(Component.literal("§7Here: §f" + (names.isEmpty() ? "none" : String.join(", ", names))));
        return 1;
    }

    private static int navigateTo(FabricClientCommandSource src, String query) {
        Node[] g = graphOrError(src);
        if (g == null) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        String q = query.trim().toLowerCase(Locale.ROOT);
        Vec3 me = mc.player.position();
        int best = -1;
        boolean exact = false;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < g.length; i++) {
            Node n = g[i];
            if (n.clean == null) continue;
            String low = n.clean.toLowerCase(Locale.ROOT);
            boolean ex = low.equals(q);
            if (!ex && !low.contains(q)) continue;
            double d = n.pos.distanceToSqr(me);
            if ((ex && !exact) || (ex == exact && d < bd)) {
                best = i;
                bd = d;
                exact = ex;
            }
        }
        if (best < 0) {
            src.sendFeedback(Component.literal("§cNothing called \"" + query + "\" on this island."));
            return 0;
        }
        Node n = g[best];
        start(List.of(new Stop(best, n.pos.add(0.5, 0, 0.5), n.clean)), n.clean);
        src.sendFeedback(Component.literal("§aNavigating to §f" + n.clean + "§a. §7/tmap navigate stop"));
        return 1;
    }

    private static int listAreas(FabricClientCommandSource src) {
        Node[] g = graphOrError(src);
        if (g == null) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        int start = closest(g, mc.player.position());
        double[][] dp = dijkstra(g, start);
        updateAreas(g, dp[0]);
        List<Area> list = areas;
        if (list.isEmpty()) {
            src.sendFeedback(Component.literal("§cNo areas on this island."));
            return 0;
        }
        src.sendFeedback(Component.literal("§b[TeslaMaps] §fAreas §7(click to navigate)"));
        for (int i = 0; i < list.size(); i++) {
            Area a = list.get(i);
            if (i == 0) {
                src.sendFeedback(Component.literal(" §a" + a.name() + " §7(you are here)"));
                continue;
            }
            String cmd = "/tmap navigate " + a.name();
            MutableComponent line = Component.literal(" §e" + a.name() + " §8" + (int) a.dist() + "m")
                    .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(cmd))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Navigate to §f" + a.name()))));
            src.sendFeedback(line);
        }
        return 1;
    }

    private static Set<String> availableTags() {
        Set<String> out = new java.util.LinkedHashSet<>();
        Node[] g = graph;
        if (g == null) return out;
        for (String t : MULTI_TAGS.keySet()) {
            for (Node n : g) {
                if (n.tags.contains(t)) {
                    out.add(t);
                    break;
                }
            }
        }
        return out;
    }

    private static int navigateAll(FabricClientCommandSource src, String type) {
        Node[] g = graphOrError(src);
        if (g == null) return 0;
        String want = type.trim().replace('_', ' ');
        String tag = null;
        for (Map.Entry<String, String> e : MULTI_TAGS.entrySet()) {
            if (e.getValue().equalsIgnoreCase(want) || e.getKey().equalsIgnoreCase(type.trim())) tag = e.getKey();
        }
        if (tag == null || !availableTags().contains(tag)) {
            src.sendFeedback(Component.literal("§c" + type + " is not a navigation type on this island."));
            return usageAll(src);
        }
        List<Stop> stops = new ArrayList<>();
        String label = MULTI_TAGS.get(tag);
        for (int i = 0; i < g.length; i++) if (g[i].tags.contains(tag)) stops.add(new Stop(i, g[i].pos.add(0.5, 0, 0.5), label));
        startOptimized(g, stops, label);
        return 1;
    }

    /** Visits the graph nodes closest to the given points, shortest order first. */
    public static boolean navigatePoints(List<Vec3> points, String name) {
        if (!enabled()) {
            msg("§cTurn on Island Navigation (Ordered Waypoints) first.");
            return false;
        }
        Node[] g = graph;
        if (g == null || g.length == 0) {
            msg("§cNo navigation graph for this island.");
            return false;
        }
        List<Stop> stops = new ArrayList<>();
        for (Vec3 p : points) {
            int n = closest(g, p);
            if (n >= 0) stops.add(new Stop(n, g[n].pos.add(0.5, 0, 0.5), name));
        }
        if (stops.isEmpty()) return false;
        startOptimized(g, stops, name);
        return true;
    }

    private static int skip(FabricClientCommandSource src) {
        if (route.isEmpty()) {
            src.sendFeedback(Component.literal("§cNo navigation to skip."));
            return 0;
        }
        src.sendFeedback(Component.literal("§eSkipping " + route.get(routeIndex).label() + "§e."));
        reached();
        return 1;
    }

    private static int undo(FabricClientCommandSource src) {
        if (route.isEmpty() || routeIndex == 0) {
            src.sendFeedback(Component.literal("§cNothing to go back to."));
            return 0;
        }
        routeIndex--;
        path = null;
        lastClosest = -1;
        src.sendFeedback(Component.literal("§eGoing back to " + route.get(routeIndex).label() + " "
                + (routeIndex + 1) + "/" + route.size() + "§e."));
        return 1;
    }

    private static int clipboard(FabricClientCommandSource src) {
        Node[] g = graphOrError(src);
        if (g == null) return 0;
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        List<Stop> stops = new ArrayList<>();
        Matcher m = COORDS.matcher(clip == null ? "" : clip);
        while (m.find() && stops.size() < 300) {
            Vec3 p = new Vec3(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)), Double.parseDouble(m.group(3)));
            int n = closest(g, p);
            if (n >= 0) stops.add(new Stop(n, p, "Location"));
        }
        if (stops.isEmpty()) {
            src.sendFeedback(Component.literal("§cNo coordinates in the clipboard (x y z per entry)."));
            return 0;
        }
        startOptimized(g, stops, "Locations");
        return 1;
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }
}
