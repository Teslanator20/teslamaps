package com.teslamaps.features.foraging;

// Ported from SkyHanni's ShulkerFinder, spots and paths from SkyHanni's island graphs
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.navigation.IslandNavigation;
import com.teslamaps.features.navigation.IslandNavigation.Node;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.KeyNames;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Hotkey: walks a round route over the fixed Hideonleaf (Galatea) / Hideonsun (Torrhus) spots. */
public final class ShulkerFinder {

    private static String routeKey;
    private static List<Integer> route = List.of();
    private static int index = -1;
    private static List<Vec3> path;
    private static int lastClosest = -1;
    private static String label;
    private static boolean keyDown;
    private static int ticks;
    private static Object lastLevel;

    private ShulkerFinder() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ShulkerFinder").section("Foraging").when(() -> cfg().shulkerFinder)
                .tick(ShulkerFinder::tick)
                .world(ShulkerFinder::render)
                .config("Foraging", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Shulker Finder", () -> c.shulkerFinder, v -> c.shulkerFinder = v)
                            .desc("Press the key: path to the next Hideonleaf / Hideonsun spot of a round route. No shulker there = next spot. Press again to skip.")
                            .tags("hideonleaf", "hideonsun", "shulker", "hunting");
                    b.keybind("  Next Shulker Key", () -> c.shulkerFinderKey, v -> c.shulkerFinderKey = v);
                    b.color("  Path Color", () -> c.shulkerFinderColor, v -> c.shulkerFinderColor = v);
                });
    }

    private static String key() {
        if (Foraging.inMoonglade()) return "GALATEA";
        if (Foraging.inTorrhus()) return "TORRHUS_CANYON";
        return null;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            stop();
            routeKey = null;
            route = List.of();
        }
        String key = key();
        if (key == null) {
            if (index >= 0) stop();
            keyDown = false;
            return;
        }
        Node[] g = IslandNavigation.graph(key);
        boolean down = mc.screen == null && KeyNames.isDown(cfg().shulkerFinderKey);
        if (down && !keyDown && g != null) next(mc, g, key);
        keyDown = down;
        if (index < 0 || g == null || ticks++ % 5 != 0) return;

        Node goal = g[route.get(index)];
        Vec3 me = mc.player.position();
        Vec3 target = goal.pos.add(0.5, 0, 0.5);
        if (me.distanceTo(target) < 3) {
            if (mc.level.getEntitiesOfClass(Shulker.class, new AABB(goal.pos, goal.pos).inflate(4)).isEmpty()) {
                next(mc, g, key);
            } else {
                msg("§aShulker found.");
                stop();
            }
            return;
        }
        int start = IslandNavigation.closest(g, me);
        if (start != lastClosest || path == null) {
            lastClosest = start;
            double[][] dp = IslandNavigation.dijkstra(g, start);
            List<Vec3> out = new ArrayList<>();
            int t = route.get(index);
            if (!Double.isInfinite(dp[0][t])) {
                for (int v = t; v >= 0; v = (int) dp[1][v]) out.add(g[v].pos.add(0.5, 0.5, 0.5));
                Collections.reverse(out);
            }
            out.add(target);
            path = out;
        }
        String name = "HIDEONSUN".equals(tag(key)) ? "§eHideonsun" : "§2Hideonleaf";
        label = name + " §7" + (index + 1) + "/" + route.size() + " (" + (int) me.distanceTo(target) + "m)";
    }

    private static String tag(String key) {
        return "TORRHUS_CANYON".equals(key) ? "HIDEONSUN" : "HIDEONLEAF";
    }

    private static void next(Minecraft mc, Node[] g, String key) {
        if (!key.equals(routeKey) || route.isEmpty()) {
            route = buildRoute(g, tag(key).toLowerCase(), mc.player.position());
            routeKey = key;
            index = -1;
            if (route.isEmpty()) {
                msg("§cNo shulker spots known for this island.");
                return;
            }
        }
        index = (index + 1) % route.size();
        path = null;
        lastClosest = -1;
    }

    private static List<Integer> buildRoute(Node[] g, String tag, Vec3 from) {
        List<Integer> left = new ArrayList<>();
        for (int i = 0; i < g.length; i++) if (g[i].tags.contains(tag)) left.add(i);
        List<Integer> out = new ArrayList<>();
        int cur = IslandNavigation.closest(g, from);
        while (!left.isEmpty() && cur >= 0) {
            double[] dist = IslandNavigation.dijkstra(g, cur)[0];
            int best = 0;
            for (int i = 1; i < left.size(); i++) if (dist[left.get(i)] < dist[left.get(best)]) best = i;
            cur = left.remove(best);
            out.add(cur);
        }
        return out;
    }

    private static void stop() {
        index = -1;
        path = null;
        label = null;
        lastClosest = -1;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<Vec3> p = path;
        Minecraft mc = Minecraft.getInstance();
        if (p == null || index < 0 || mc.player == null) return;
        int color = TeslaMapsConfig.parseColor(cfg().shulkerFinderColor);
        Vec3 prev = mc.player.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)).add(0, 0.2, 0);
        for (Vec3 v : p) {
            ESPRenderer.drawLine(pose, prev, v, color, 3f, cam, true);
            prev = v;
        }
        String l = label;
        if (l != null) ESPRenderer.drawText(pose, l, p.get(p.size() - 1).add(0, 1.5, 0), 1f, cam);
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }
}
