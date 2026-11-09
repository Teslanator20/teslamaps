package com.teslamaps.features.mining;

// Ported from SkyHanni's TunnelsMaps.kt; graph = SkyHanni-REPO island_graphs/GLACITE_TUNNELS
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.navigation.IslandNavigation;
import com.teslamaps.features.navigation.IslandNavigation.Node;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Path to gemstone spots, the campfire and fairy souls in the Glacite Tunnels. */
public final class TunnelsMaps {

    private static final Set<String> AREAS = Set.of("Glacite Tunnels", "Dwarven Base Camp", "Great Glacite Lake", "Fossil Research Center");
    private static final Pattern COLLECTOR = Pattern.compile("(\\w+(?: \\w+)?) Collector");
    private static final Pattern GLACITE_REWARD = Pattern.compile("- [\\d,]+ Glacite Powder");
    private static final Pattern INVALID = Pattern.compile("Glacite|Scrap");
    private static final Pattern GEM = Pattern.compile(".*(?:Ruby|Amethyst|Jade|Sapphire|Amber|Topaz|Aquamarine|Onyx|Citrine|Peridot).*");
    private static final List<String> SAMPLE = List.of("§6Tunnels Maps", "§6Active: §9Aquamarine", "§e84m");

    private static Node[] graph;
    private static Map<String, List<Integer>> locations = Map.of();
    private static List<String> fairy = List.of(), gems = List.of(), other = List.of();
    private static int campfire = -1;
    private static String campfireName;

    private static String active = "";
    private static int goal = -1, prevGoal = -1;
    private static boolean goalReached;
    private static final Map<Integer, Long> cooldowns = new HashMap<>();
    private static int closest = -1, pathFrom = -1, pathGoal = -1;
    private static List<Vec3> path = List.of();
    private static List<Integer> pathNodes = List.of();
    private static double distance;
    private static boolean commission;

    private static boolean show;
    private static int ticks;
    private static Object lastLevel;
    private static boolean campDown, nextDown;
    private static long lastCampKey, lastNextKey;
    private static Object lastScreen;
    private static String lastCommission;

    private static List<String> lines = List.of();
    private static int width;
    private static HudEditElement hud;

    private TunnelsMaps() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("tunnelsMaps", "Tunnels Maps").ctx(HudEditElement.HudCtx.MINING).section("Mining")
                .enabled(() -> cfg().tunnelsMaps)
                .size(() -> width(SAMPLE), () -> SAMPLE.size() * 10 + 6)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, width(SAMPLE)));
        Feature.of("TunnelsMaps").section("Mining").when(() -> cfg().tunnelsMaps)
                .tick(TunnelsMaps::tick)
                .world(TunnelsMaps::render)
                .hudHiddenInInventory("tunnelsmaps", TunnelsMaps::renderHud)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Glacite Tunnels Maps", () -> c.tunnelsMaps, v -> c.tunnelsMaps = v)
                            .desc("Path through the Glacite Tunnels to a gemstone type, the campfire or a fairy soul. /tmap tunnels lists the places (click one). Picks the closest spot that was not visited in the last minute.")
                            .tags("glacite", "tunnels", "navigation", "path", "gemstone", "campfire", "fairy soul");
                    b.toggle("  Follow Commission", () -> c.tunnelsMapsCommission, v -> c.tunnelsMapsCommission = v)
                            .desc("Opening the Commissions menu targets your open Collector commission");
                    b.keybind("  Campfire Key", () -> c.tunnelsMapsCampfireKey, v -> c.tunnelsMapsCampfireKey = v);
                    b.toggle("    Warp To Base Camp Instead", () -> c.tunnelsMapsTravelScroll, v -> c.tunnelsMapsTravelScroll = v)
                            .desc("The key sends /warp basecamp (needs the travel scroll)");
                    b.keybind("  Next Spot Key", () -> c.tunnelsMapsNextKey, v -> c.tunnelsMapsNextKey = v);
                    b.color("  Path Color", () -> c.tunnelsMapsColor, v -> c.tunnelsMapsColor = v);
                    b.toggle("  Color By Gemstone", () -> c.tunnelsMapsDynamicColor, v -> c.tunnelsMapsDynamicColor = v);
                    b.slider("  Path Width", 1f, 10f, () -> (float) c.tunnelsMapsWidth, v -> c.tunnelsMapsWidth = Math.round(v), 0);
                    b.toggle("  Landmark Names", () -> c.tunnelsMapsLandmarks, v -> c.tunnelsMapsLandmarks = v);
                    b.toggle("  Distance At Next Node", () -> c.tunnelsMapsDistanceFirst, v -> c.tunnelsMapsDistanceFirst = v);
                    b.toggle("  Hide Fairy Souls In List", () -> c.tunnelsMapsNoFairy, v -> c.tunnelsMapsNoFairy = v);
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("tunnels")
                        .executes(c -> list(c.getSource()))
                        .then(ClientCommands.argument("place", StringArgumentType.greedyString())
                                .suggests((c, sb) -> {
                                    String typed = sb.getRemaining().toLowerCase(Locale.ROOT);
                                    for (String k : new String[]{"stop", "next", "campfire"}) if (k.startsWith(typed) && !k.equals(typed)) sb.suggest(k);
                                    for (String n : locations.keySet()) {
                                        String s = StringUtil.stripTrim(n);
                                        String low = s.toLowerCase(Locale.ROOT);
                                        if (low.contains(typed) && !low.equals(typed)) sb.suggest(s);
                                    }
                                    return sb.buildFuture();
                                })
                                .executes(c -> command(c.getSource(), StringArgumentType.getString(c, "place")))))));
    }

    // ---- graph ----

    private static void build(Node[] g) {
        graph = g;
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        for (int i = 0; i < g.length; i++) {
            if (g[i].name == null || g[i].name.isEmpty()) continue;
            map.computeIfAbsent(g[i].name, k -> new ArrayList<>()).add(i);
        }
        List<String> f = new ArrayList<>(), gm = new ArrayList<>(), o = new ArrayList<>();
        campfire = -1;
        campfireName = null;
        for (var e : map.entrySet()) {
            String k = e.getKey();
            if (k.contains("Campfire")) {
                campfire = e.getValue().get(0);
                campfireName = k;
            } else if (k.contains("Fairy")) f.add(k);
            else if (GEM.matcher(k).matches()) gm.add(k);
            else if (!StringUtil.strip(k).equals(k)) o.add(k);
        }
        locations = map;
        fairy = f;
        gems = gm;
        other = o;
        closest = pathFrom = pathGoal = -1;
    }

    private static int next(String name) {
        List<Integer> list = locations.get(name);
        if (list == null || list.isEmpty() || graph == null) return -1;
        goalReached = false;
        if (name.contains("Fairy") || list.size() == 1) return list.get(0);
        long now = System.currentTimeMillis();
        double[] dist = closest >= 0 ? IslandNavigation.dijkstra(graph, closest)[0] : null;
        int best = -1;
        for (int n : list) {
            if (cooldowns.getOrDefault(n, 0L) > now) continue;
            if (best < 0 || (dist != null && dist[n] < dist[best])) best = n;
        }
        if (best < 0) {
            for (int n : list) if (best < 0 || cooldowns.getOrDefault(n, 0L) < cooldowns.getOrDefault(best, 0L)) best = n;
        }
        if (cooldowns.getOrDefault(best, 0L) <= now) cooldowns.put(best, now + 5000);
        return best;
    }

    private static void setActive(String name) {
        active = name;
        setGoal(next(name));
    }

    private static void setGoal(int g) {
        prevGoal = goal;
        goal = g;
        goalReached = false;
    }

    private static void clearPath() {
        setGoal(-1);
        path = List.of();
        pathNodes = List.of();
        pathGoal = -1;
    }

    // ---- tick ----

    private static boolean inTunnels() {
        if (!SkyblockUtils.isInArea("Dwarven Mines")) return false;
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            if (line == null || line.indexOf('⏣') < 0) continue;
            return AREAS.contains(StringUtil.stripTrim(line).replace("⏣", "").trim());
        }
        return false;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            boolean had = lastLevel != null;
            lastLevel = mc.level;
            closest = pathFrom = -1;
            path = List.of();
            pathNodes = List.of();
            pathGoal = -1;
            // a warp inside the tunnels keeps the target, the next spot is picked from the new position
            if (had && goal >= 0 && !active.isEmpty()) ticks = -40;
        }
        ticks++;
        if (ticks % 20 == 0 || ticks == -1) {
            show = inTunnels();
            if (!show && !active.isEmpty() && ticks > 0) {
                active = "";
                clearPath();
                cooldowns.clear();
            }
            Node[] g = show ? IslandNavigation.graph("GLACITE_TUNNELS") : null;
            if (g != null && g != graph) build(g);
            if (ticks == -1 && show && !active.isEmpty()) setGoal(next(active));
        }
        if (!show || graph == null || mc.player == null) {
            lines = List.of();
            return;
        }
        keys(mc);
        if (cfg().tunnelsMapsCommission) scanCommissions(mc);
        if (ticks % 5 != 0) return;

        Vec3 me = mc.player.position();
        if (goal >= 0 && !goalReached) {
            double reach = goal == campfire ? 15 : 6;
            if (graph[goal].pos.distanceToSqr(me) < reach * reach) {
                goalReached = true;
                if (goal == campfire && !active.equals(campfireName)) {
                    setGoal(next(active));
                } else {
                    cooldowns.put(goal, System.currentTimeMillis() + 60_000);
                    clearPath();
                }
            }
        }
        closest = IslandNavigation.closest(graph, me);
        if (goal >= 0 && closest >= 0 && (closest != pathFrom || goal != pathGoal)) {
            pathFrom = closest;
            pathGoal = goal;
            buildPath(me);
        }
        if (goal < 0) {
            path = List.of();
            pathNodes = List.of();
        }
        buildLines(me);
    }

    private static void buildPath(Vec3 me) {
        double[][] dp = IslandNavigation.dijkstra(graph, closest);
        if (Double.isInfinite(dp[0][goal])) {
            path = List.of(graph[goal].pos.add(0.5, 0.5, 0.5));
            pathNodes = List.of(goal);
            distance = me.distanceTo(graph[goal].pos);
            return;
        }
        List<Integer> nodes = new ArrayList<>();
        for (int v = goal; v >= 0; v = (int) dp[1][v]) nodes.add(v);
        java.util.Collections.reverse(nodes);
        double dist = dp[0][goal];
        // skip the first node when walking straight to the second is shorter
        if (nodes.size() > 1) {
            Node first = graph[nodes.get(0)], second = graph[nodes.get(1)];
            double direct = me.distanceTo(second.pos);
            double edge = second.pos.distanceTo(first.pos);
            for (int k = 0; k < first.nb.length; k++) if (first.nb[k] == nodes.get(1)) edge = first.w[k];
            double around = me.distanceTo(first.pos) + edge;
            if (direct < around) {
                nodes.remove(0);
                dist = dist - edge + direct;
            } else dist += me.distanceTo(first.pos);
        } else if (!nodes.isEmpty()) dist += me.distanceTo(graph[nodes.get(0)].pos);
        List<Vec3> out = new ArrayList<>(nodes.size());
        for (int n : nodes) out.add(graph[n].pos.add(0.5, 0.5, 0.5));
        path = out;
        pathNodes = nodes;
        distance = dist;
    }

    private static void buildLines(Vec3 me) {
        List<String> out = new ArrayList<>();
        out.add("§6Tunnels Maps");
        if (active.isEmpty() && goal < 0) {
            out.add("§7No target §8- /tmap tunnels");
        } else if (goal == campfire && !active.equals(campfireName)) {
            out.add("§6Override: " + campfireName);
            if (!active.isEmpty()) out.add("§7then §f" + active);
        } else {
            out.add("§6Active: §f" + (active.isEmpty() ? "-" : active) + (commission ? " §8(commission)" : ""));
        }
        if (goal >= 0) out.add("§e" + Math.round(distance) + "m");
        else if (!active.isEmpty()) out.add("§aReached §8- next spot key or /tmap tunnels next");
        lines = out;
        width = width(out);
    }

    private static void keys(Minecraft mc) {
        TeslaMapsConfig c = cfg();
        long h = mc.getWindow().handle();
        boolean free = mc.gui.screen() == null;
        boolean camp = free && c.tunnelsMapsCampfireKey > 0 && com.mojang.blaze3d.platform.InputConstants.isKeyDown(c.tunnelsMapsCampfireKey);
        boolean nxt = free && c.tunnelsMapsNextKey > 0 && com.mojang.blaze3d.platform.InputConstants.isKeyDown(c.tunnelsMapsNextKey);
        long now = System.currentTimeMillis();
        if (camp && !campDown && now - lastCampKey > 2000) {
            lastCampKey = now;
            if (c.tunnelsMapsTravelScroll) {
                if (mc.getConnection() != null) mc.getConnection().sendCommand("warp basecamp");
            } else if (campfire >= 0) {
                setGoal(campfire);
            }
        }
        if (nxt && !nextDown && now - lastNextKey > 500 && !active.isEmpty()) {
            lastNextKey = now;
            setGoal(next(active));
        }
        campDown = camp;
        nextDown = nxt;
    }

    private static void scanCommissions(Minecraft mc) {
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) {
            lastScreen = null;
            return;
        }
        if (cs != lastScreen) {
            lastScreen = cs;
            lastCommission = null;
        }
        if (ticks % 10 != 0) return;
        if (!StringUtil.stripTrim(cs.getTitle().getString()).equals("Commissions")) return;
        String found = null;
        boolean any = false;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.getItem().isEmpty()) continue;
            List<String> lore = ItemUtil.loreLines(slot.getItem());
            boolean glacite = false, done = false;
            String type = null;
            for (String raw : lore) {
                String l = StringUtil.stripTrim(raw);
                if (GLACITE_REWARD.matcher(l).matches()) glacite = true;
                if (l.contains("COMPLETED")) done = true;
                if (type == null) {
                    Matcher m = COLLECTOR.matcher(l);
                    if (m.find()) type = m.group(1);
                }
            }
            if (!glacite) continue;
            any = true;
            if (done || type == null || INVALID.matcher(type).matches()) continue;
            String name = generic(type);
            if (name != null) {
                found = name;
                break;
            }
        }
        if (!any) return;
        String key = found == null ? "" : found;
        if (key.equals(lastCommission)) return;
        lastCommission = key;
        if (found != null) {
            commission = true;
            setActive(found);
        } else if (commission) {
            commission = false;
            active = "";
            clearPath();
        }
    }

    private static String generic(String type) {
        String up = type.toUpperCase(Locale.ROOT);
        for (String k : locations.keySet()) if (StringUtil.strip(k).toUpperCase(Locale.ROOT).contains(up)) return k;
        return null;
    }

    // ---- render ----

    private static int pathColor() {
        TeslaMapsConfig c = cfg();
        if (c.tunnelsMapsDynamicColor && goal >= 0 && graph != null && goal < graph.length) {
            String n = graph[goal].name;
            int i = n == null ? -1 : n.indexOf('§');
            if (i >= 0 && i + 1 < n.length()) {
                ChatFormatting f = ChatFormatting.getByCode(n.charAt(i + 1));
                net.minecraft.network.chat.TextColor tc = f != null ? net.minecraft.network.chat.TextColor.fromLegacyFormat(f) : null;
                if (tc != null && f != ChatFormatting.WHITE) return 0xFF000000 | tc.getValue();
            }
        }
        return TeslaMapsConfig.parseColor(c.tunnelsMapsColor);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<Vec3> p = path;
        Node[] g = graph;
        if (!show || p.isEmpty() || g == null || goal < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        TeslaMapsConfig c = cfg();
        int color = pathColor();
        float w = Math.max(1, c.tunnelsMapsWidth);
        Vec3 prev = mc.player.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)).add(0, 0.2, 0);
        for (Vec3 v : p) {
            ESPRenderer.drawLine(pose, prev, v, color, w, cam, true);
            prev = v;
        }
        if (c.tunnelsMapsLandmarks) {
            List<Integer> nodes = pathNodes;
            for (int i = 0; i < nodes.size() - 1; i++) {
                Node n = g[nodes.get(i)];
                if (n.name == null || n.name.isEmpty() || n.pos.distanceToSqr(cam) > 48 * 48) continue;
                ESPRenderer.drawText(pose, n.name, n.pos.add(0.5, 2, 0.5), 1f, cam);
            }
        }
        Vec3 end = p.get(p.size() - 1);
        ESPRenderer.drawText(pose, g[goal].name == null ? "§eTarget" : g[goal].name, end.add(0, 2, 0), 1f, cam);
        Vec3 at = c.tunnelsMapsDistanceFirst ? p.get(0) : end;
        ESPRenderer.drawText(pose, "§e" + Math.round(distance) + "m", at.add(0, c.tunnelsMapsDistanceFirst ? 1.5 : 1.4, 0), 1f, cam);
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> ls = lines;
        if (!show || ls.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), ls, width);
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

    // ---- commands ----

    private static boolean ready(FabricClientCommandSource src) {
        if (!cfg().tunnelsMaps || !cfg().section("Mining")) {
            src.sendFeedback(Component.literal("§cTurn on Glacite Tunnels Maps (Mining) first."));
            return false;
        }
        if (!show || graph == null) {
            src.sendFeedback(Component.literal("§cOnly in the Glacite Tunnels (graph still loading?)."));
            return false;
        }
        return true;
    }

    private static int command(FabricClientCommandSource src, String arg) {
        String q = arg.trim();
        if (q.equalsIgnoreCase("stop")) {
            active = "";
            commission = false;
            clearPath();
            src.sendFeedback(Component.literal("§eTunnels Maps stopped."));
            return 1;
        }
        if (!ready(src)) return 0;
        if (q.equalsIgnoreCase("next")) {
            if (active.isEmpty()) return list(src);
            setGoal(next(active));
            return 1;
        }
        if (q.equalsIgnoreCase("campfire")) {
            if (campfire >= 0) setGoal(campfire);
            return 1;
        }
        String low = q.toLowerCase(Locale.ROOT), hit = null;
        for (String k : locations.keySet()) {
            String s = StringUtil.stripTrim(k).toLowerCase(Locale.ROOT);
            if (s.equals(low)) { hit = k; break; }
            if (hit == null && s.contains(low)) hit = k;
        }
        if (hit == null) {
            src.sendFeedback(Component.literal("§cNo place called \"" + q + "\" in the tunnels."));
            return 0;
        }
        commission = false;
        setActive(hit);
        src.sendFeedback(Component.literal("§aTunnels Maps: §f" + hit));
        return 1;
    }

    private static int list(FabricClientCommandSource src) {
        if (!ready(src)) return 0;
        src.sendFeedback(Component.literal("§b[TeslaMaps] §6Tunnels Maps §7(click a place, /tmap tunnels stop)"));
        if (campfireName != null) src.sendFeedback(entry(campfireName));
        if (!cfg().tunnelsMapsNoFairy && !fairy.isEmpty()) {
            var line = Component.literal(" §dFairy Souls ");
            for (String f : fairy) {
                String n = StringUtil.stripTrim(f).replace("Fairy Soul", "").trim();
                line.append(click("§d[" + n + "] ", f));
            }
            src.sendFeedback(line);
        }
        var gemLine = Component.literal(" ");
        for (String gm : gems) gemLine.append(click(gm + " ", gm));
        if (!gems.isEmpty()) src.sendFeedback(gemLine);
        for (String o : other) src.sendFeedback(entry(o));
        return 1;
    }

    private static Component entry(String name) {
        return Component.literal(" ").append(click(name, name));
    }

    private static Component click(String text, String name) {
        String cmd = "/tmap tunnels " + StringUtil.stripTrim(name);
        return Component.literal(text).withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(cmd))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Path to " + name))));
    }
}
