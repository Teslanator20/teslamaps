package com.teslamaps.features.waypoints;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// Ported from SkyHanni's OrderedWaypoints.kt (originally Coleweight / SoopyV2)
public class OrderedWaypoints {

    public static final class Wp {
        public int x, y, z;
        public int number;

        public String name;
        public int color = -1;
        private String label;
        private int labelFor = -1;

        public Wp(int x, int y, int z, int number) {
            this.x = x; this.y = y; this.z = z; this.number = number;
        }

        public Wp(int x, int y, int z, int number, String name, int color) {
            this(x, y, z, number);
            this.name = name == null || name.isBlank() ? null : name;
            this.color = color;
        }

        public Wp copy() { return new Wp(x, y, z, number, name, color); }

        public String label() {
            if (labelFor != number) {
                labelFor = number;
                label = name == null || name.equals(String.valueOf(number)) ? "§e" + number : "§e" + number + " §f" + name;
            }
            return label;
        }

        public Vec3 center() { return new Vec3(x + 0.5, y + 0.5, z + 0.5); }
        public double distSq(Vec3 p) { return center().distanceToSqr(p); }
    }

    private static final List<Wp> route = new ArrayList<>();
    private static final List<Integer> renderIdx = new ArrayList<>();
    private static int current = 0;
    private static int lastCloser = 0;
    private static String loadedName = "";
    private static boolean paused;

    public static List<Wp> route() { return route; }
    public static String loadedName() { return loadedName; }

    private static Object lastLevel;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            onWorldChange();
        }
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (cfg.orderedAutoLoadByArea) autoLoadForArea();
        if (!cfg.orderedWaypoints || route.isEmpty()) { renderIdx.clear(); return; }
        if (mc.player == null || paused) return;
        decide(mc.player.position());
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.orderedWaypoints || route.isEmpty() || renderIdx.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 playerPos = mc.player.position();

        int nextCount = Math.max(1, cfg.orderedNextCount);

        for (int i = 0; i < renderIdx.size(); i++) {
            int idx = renderIdx.get(i);
            if (idx < 0 || idx >= route.size()) continue;
            Wp wp = route.get(idx);

            String colorHex;
            if (cfg.orderedShowAll) {
                colorHex = cfg.orderedColorAll;
            } else if (i == 0) {
                colorHex = cfg.orderedColorPrevious;
            } else if (i == 1) {
                colorHex = cfg.orderedColorCurrent;
            } else if (i <= 1 + nextCount) {
                colorHex = cfg.orderedColorNext;
            } else {
                colorHex = cfg.orderedColorSetup;
            }
            int color = cfg.orderedImportedStyle && wp.color != -1 && !cfg.orderedSetupMode
                    ? 0xFF000000 | wp.color : TeslaMapsConfig.parseColor(colorHex);

            AABB box = new AABB(wp.x, wp.y, wp.z, wp.x + 1, wp.y + 1, wp.z + 1);
            if (cfg.orderedFillBlock) {
                ESPRenderer.drawFilledBox(matrices, box, color & 0x00FFFFFF, cameraPos, true);
            } else {
                ESPRenderer.drawBoxOutline(matrices, box, color, cfg.orderedOutlineThickness, cameraPos, true);
            }

            if (cfg.orderedShowName && (cfg.orderedSetupMode || cfg.orderedShowAll || i <= 1 + nextCount)) {
                ESPRenderer.drawText(matrices, cfg.orderedImportedStyle ? wp.label() : "§e" + wp.number,
                        new Vec3(wp.x + 0.5, wp.y + 2.5, wp.z + 0.5), 1.0f, cameraPos);
            }
            if (cfg.orderedShowDistance) {
                double d = Math.sqrt(wp.distSq(playerPos));
                ESPRenderer.drawText(matrices, String.format("§e%.1f m", d),
                        new Vec3(wp.x + 0.5, wp.y + 2.0, wp.z + 0.5), 1.0f, cameraPos);
            }
        }

        if (renderIdx.size() <= 1) return;

        Wp trace = renderIdx.size() == 2 ? route.get(renderIdx.get(0)) : route.get(renderIdx.get(2));

        if (cfg.orderedTraceLine && !cfg.orderedShowAll && !cfg.orderedSetupMode) {
            ESPRenderer.drawTracerFromCamera(matrices,
                    new Vec3(trace.x + 0.5, trace.y + 0.25, trace.z + 0.5),
                    TeslaMapsConfig.parseColor(cfg.orderedColorTraceLine), cameraPos, true);
        }

        if (cfg.orderedSetupMode && !cfg.orderedShowAll) {
            Wp cur = route.get(renderIdx.get(1));
            double eye = cfg.orderedSneaking ? 1.54 : 1.62;
            ESPRenderer.drawLine(matrices,
                    new Vec3(cur.x + 0.5, cur.y + 1.0 + eye, cur.z + 0.5),
                    new Vec3(trace.x + 0.5, trace.y + 0.5, trace.z + 0.5),
                    TeslaMapsConfig.parseColor(cfg.orderedColorSetupLine), cfg.orderedSetupLineThickness, cameraPos);
        }
    }

    private static String lastArea = "";

    // a saved route named exactly like the current area loads itself on entering it
    private static void autoLoadForArea() {
        String area = com.teslamaps.utils.SkyblockUtils.getArea();
        if (area.equals(lastArea)) return;
        lastArea = area;
        if (area.isEmpty() || area.equals(loadedName)) return;
        if (RouteStorage.get(area) == null) return;
        String msg = load(area);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("§b[TeslaMaps] §r" + msg));
    }

    private static void decide(Vec3 player) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        renderIdx.clear();
        if (route.isEmpty()) return;

        double range = cfg.orderedWaypointRange;
        double rangeSq = range * range;

        boolean skipAir = cfg.orderedSkipAir;
        if (skipAir) {
            for (int guard = route.size(); guard > 0 && isAir(route.get(current)); guard--) increment(1);
        }

        if (cfg.orderedAutoSkipForward) {
            Wp closest = null;
            double best = rangeSq;
            for (Wp wp : route) {
                if (skipAir && isAir(wp)) continue;
                double d = wp.distSq(player);
                if (d < best) { best = d; closest = wp; }
            }
            if (closest != null && closest.number - 1 > current) {
                current = closest.number - 1;
                lastCloser = current;
                return;
            }
        }

        Wp before = current - 1 >= 0 && current - 1 < route.size()
                ? route.get(current - 1) : route.get(route.size() - 1);
        renderIdx.add(before.number - 1);

        double distCur = Double.POSITIVE_INFINITY;
        if (current >= 0 && current < route.size()) {
            Wp cur = route.get(current);
            distCur = Math.sqrt(cur.distSq(player));
            renderIdx.add(cur.number - 1);
        }

        int nextIdx = Math.floorMod(current + 1, route.size());
        if (skipAir) nextIdx = firstAlive(nextIdx);
        Wp next = route.get(nextIdx);
        double distNext = Math.sqrt(next.distSq(player));
        if (!renderIdx.contains(nextIdx)) renderIdx.add(nextIdx);
        int idx = nextIdx;
        for (int i = 1; i < Math.max(1, cfg.orderedNextCount); i++) {
            idx = Math.floorMod(idx + 1, route.size());
            if (skipAir) idx = firstAlive(idx);
            if (!renderIdx.contains(idx)) renderIdx.add(idx);
        }

        if (lastCloser == current && distCur > distNext && distNext < range) {
            increment(1);
            return;
        }
        if (distCur < range) lastCloser = current;
        if (distNext < range) increment(1);

        if (cfg.orderedShowAll) {
            for (Wp wp : route) if (!renderIdx.contains(wp.number - 1)) renderIdx.add(wp.number - 1);
        } else if (cfg.orderedSetupMode) {
            double setupSq = cfg.orderedSetupRange * cfg.orderedSetupRange;
            for (Wp wp : route) {
                if (renderIdx.contains(wp.number - 1)) continue;
                if (wp.distSq(player) < setupSq) renderIdx.add(wp.number - 1);
            }
        }
    }

    // waypoint block gone (harvested / mined) - treat as done
    private static boolean isAir(Wp wp) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(wp.x, wp.y, wp.z);
        if (!mc.level.hasChunkAt(pos)) return false;
        return mc.level.getBlockState(pos).isAir();
    }

    private static int firstAlive(int from) {
        for (int i = 0; i < route.size(); i++) {
            int idx = Math.floorMod(from + i, route.size());
            if (!isAir(route.get(idx))) return idx;
        }
        return from;
    }

    private static void increment(int amount) {
        current = Math.floorMod(current + amount, route.size());
    }

    private static void renumber() {
        for (int i = 0; i < route.size(); i++) route.get(i).number = i + 1;
    }

    public static void onWorldChange() {
        current = 0;
        lastCloser = 0;
        renderIdx.clear();
        if (TeslaMapsConfig.get().orderedAutoUnload) {
            route.clear();
            loadedName = "";
        }
    }

    // commands

    public static String load(String name) {
        if (name == null || name.isEmpty()) return importClipboard();

        List<Wp> loaded = RouteStorage.get(name);
        if (loaded == null) {
            return "§cRoute " + name + " doesn't exist. Saved: " + String.join(", ", RouteStorage.names());
        }
        if (loaded.isEmpty()) return "§cThat route is empty.";

        activate(name, loaded);
        return withEnableNote("§aLoaded " + route.size() + " waypoints from §e" + name + "§a.");
    }

    /**
     * Reads whatever route data sits in the clipboard. Multi group exports (Skytils categories,
     * Skyblocker groups) land as one saved route each, and the first one is activated.
     */
    public static String importClipboard() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        RouteFormats.Result parsed = RouteFormats.load(clip);
        if (parsed == null) {
            if (RouteFormats.lastError() != null) return "§c" + RouteFormats.lastError();
            return "§cCould not read a route from the clipboard. Formats: "
                    + "coleweight, skytils V1, skyblocker, firmament, waypointer (WP:), soopy, json, coordinate list";
        }

        List<RouteFormats.Route> routes = parsed.routes();
        String firstSaved = null;
        for (RouteFormats.Route r : routes) {
            String saved = uniqueName(r.name());
            RouteStorage.put(saved, r.waypoints());
            if (r.island() != null) RouteStorage.setIsland(saved, r.island());
            if (firstSaved == null) firstSaved = saved;
        }
        activate(firstSaved, routes.get(0).waypoints());

        String msg = routes.size() == 1
                ? "§aImported " + route.size() + " waypoints (§e" + parsed.format() + "§a) as §e" + loadedName + "§a."
                : "§aImported §e" + routes.size() + "§a routes (§e" + parsed.format() + "§a), loaded §e" + loadedName + "§a.";
        return withEnableNote(msg);
    }

    private static void activate(String name, List<Wp> waypoints) {
        route.clear();
        for (Wp wp : waypoints) route.add(wp.copy());
        renumberIfBroken();
        renderIdx.clear();
        loadedName = name == null ? "" : name;
        lastCloser = 0;
        paused = false;
        current = closestIndex();
    }

    private static String withEnableNote(String msg) {
        if (!TeslaMapsConfig.get().orderedWaypoints) {
            TeslaMapsConfig.get().orderedWaypoints = true;
            TeslaMapsConfig.save();
            return msg + " §eRendering was off, enabled it.";
        }
        return msg;
    }

    private static String uniqueName(String base) {
        String name = base == null || base.isBlank() ? "Imported" : base;
        if (RouteStorage.get(name) == null) return name;
        for (int i = 2; i < 100; i++) {
            if (RouteStorage.get(name + " " + i) == null) return name + " " + i;
        }
        return name;
    }

    private static void renumberIfBroken() {
        for (Wp wp : route) {
            if (wp.number < 1 || wp.number > route.size()) { renumber(); return; }
        }
    }

    private static int closestIndex() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        Vec3 p = mc.player.position();
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (Wp wp : route) {
            double d = wp.distSq(p);
            if (d < bestD) { bestD = d; best = wp.number - 1; }
        }
        return best;
    }

    public static String unload() {
        if (route.isEmpty()) return "§cNo route loaded.";
        route.clear();
        renderIdx.clear();
        current = 0;
        lastCloser = 0;
        loadedName = "";
        paused = false;
        return "§aUnloaded ordered waypoints.";
    }

    public static String skip(int amount) {
        if (route.isEmpty()) return "§cThere are no waypoints to skip.";
        increment(amount);
        return "§aSkipped " + amount + " waypoint" + (amount == 1 ? "" : "s") + ".";
    }

    public static String unskip(int amount) {
        if (route.isEmpty()) return "§cThere are no waypoints to unskip.";
        increment(-amount);
        return "§aUnskipped " + amount + " waypoint" + (amount == 1 ? "" : "s") + ".";
    }

    public static String skipTo(int number) {
        if (route.isEmpty()) return "§cThere are no waypoints to skip to.";
        if (number < 1 || number > route.size()) return "§c" + number + " is not between 1 and " + route.size() + ".";
        current = number - 1;
        lastCloser = current;
        return "§aSkipped to " + number + ".";
    }

    public static String delete(int number) {
        if (route.isEmpty()) return "§cThere are no waypoints to delete.";
        if (number < 1 || number > route.size()) return "§c" + number + " is not between 1 and " + route.size() + ".";
        route.remove(number - 1);
        renumber();
        renderIdx.clear();
        if (current >= route.size()) current = 0;
        return "§aRemoved waypoint " + number + ".";
    }

    public static String add(int number) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "§cNo player.";
        if (number < 1 || number > route.size() + 1) return "§c" + number + " is not between 1 and " + (route.size() + 1) + ".";
        Vec3 p = mc.player.position();
        int x = (int) Math.floor(p.x);
        int y = (int) Math.floor(p.y) - 1;
        int z = (int) Math.floor(p.z);
        route.add(number - 1, new Wp(x, y, z, number));
        renumber();
        renderIdx.clear();
        return "§aInserted waypoint " + number + " at " + x + " " + y + " " + z + ".";
    }

    public static String addHere() { return add(route.size() + 1); }

    public static String removeNearest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || route.isEmpty()) return "§cNo waypoints to remove.";
        Vec3 p = mc.player.position();
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            double d = route.get(i).distSq(p);
            if (d < bestD) { bestD = d; best = i; }
        }
        return delete(best + 1);
    }

    public static String export(String format) {
        if (route.isEmpty()) return "§cNo route loaded.";
        return exportList(route, loadedName.isEmpty() ? "TeslaMaps" : loadedName, format);
    }

    public static String exportList(List<Wp> waypoints, String name, String format) {
        if (waypoints == null || waypoints.isEmpty()) return "§cThat route is empty.";
        String out = RouteFormats.export(waypoints, name, format);
        if (out == null) return "§cUnknown format. Formats: " + String.join(", ", RouteFormats.names());
        Minecraft.getInstance().keyboardHandler.setClipboard(out);
        return "§aCopied §e" + name + "§a to clipboard (§e" + format + "§a).";
    }

    private static List<RouteFormats.Route> allRoutes() {
        List<RouteFormats.Route> out = new ArrayList<>();
        for (String n : RouteStorage.names()) out.add(new RouteFormats.Route(n, RouteStorage.island(n), RouteStorage.get(n)));
        return out;
    }

    public static String exportAll(String format) {
        List<RouteFormats.Route> all = allRoutes();
        if (all.isEmpty()) return "§cNo saved routes.";
        String out = RouteFormats.exportAll(all, format);
        if (out == null) return "§cUnknown format. Formats: skytils, skyblocker";
        Minecraft.getInstance().keyboardHandler.setClipboard(out);
        return "§aCopied §e" + all.size() + "§a routes to clipboard (§e" + format + "§a).";
    }

    public static String exportFile() {
        List<RouteFormats.Route> all = allRoutes();
        if (all.isEmpty()) return "§cNo saved routes.";
        try {
            java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                    .resolve("teslamaps").resolve("route_exports");
            java.nio.file.Files.createDirectories(dir);
            String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            java.nio.file.Path file = dir.resolve("routes_" + stamp + ".json");
            java.nio.file.Files.writeString(file, RouteFormats.exportJson(all));
            return "§aSaved §e" + all.size() + "§a routes to §e" + file.getFileName() + " §7(config/teslamaps/route_exports)";
        } catch (Exception e) {
            return "§cCould not write the file: " + e.getMessage();
        }
    }

    /** GUI edits act on the live route; nothing else may mutate the list. */
    public static void setActiveRoute(String name, List<Wp> waypoints) {
        activate(name, waypoints);
    }

    public static boolean isActive(String name) {
        return !loadedName.isEmpty() && loadedName.equals(name);
    }

    public static void clearActive() {
        route.clear();
        renderIdx.clear();
        loadedName = "";
        current = 0;
        lastCloser = 0;
    }

    public static void renameActive(String name) {
        loadedName = name;
    }

    public static int currentIndex() { return current; }

    public static boolean paused() { return paused; }

    public static String togglePause() {
        if (route.isEmpty()) return "§cNo route loaded.";
        paused = !paused;
        return paused ? "§eRoute paused." : "§aRoute resumed.";
    }

    /** Appends a point to the loaded route (a new one if none) and saves it. */
    public static String place(int x, int y, int z) {
        if (loadedName.isEmpty()) {
            if (!route.isEmpty()) return "§cThe loaded route has no name, save it first.";
            loadedName = uniqueName("Route");
        }
        route.add(new Wp(x, y, z, route.size() + 1));
        renumber();
        renderIdx.clear();
        RouteStorage.put(loadedName, route);
        if (!TeslaMapsConfig.get().orderedWaypoints) {
            TeslaMapsConfig.get().orderedWaypoints = true;
            TeslaMapsConfig.save();
        }
        return "§aAdded point " + route.size() + " to §e" + loadedName + "§a at " + x + " " + y + " " + z + ".";
    }

    /** Index of the loaded waypoint closest to the view ray within 128 blocks, -1 if none. */
    public static int lookedAt() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || route.isEmpty()) return -1;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1f);
        int best = -1;
        double bestD = 1.5 * 1.5;
        for (int i = 0; i < route.size(); i++) {
            Vec3 to = route.get(i).center().subtract(eye);
            double along = to.dot(look);
            if (along < 0 || along > 128) continue;
            double perp = to.lengthSqr() - along * along;
            double allowed = bestD * Math.max(1, along / 16);
            if (perp < allowed) { bestD = perp / Math.max(1, along / 16); best = i; }
        }
        return best;
    }

    public static void moveWaypoint(int index, int delta) {
        int target = index + delta;
        if (index < 0 || index >= route.size() || target < 0 || target >= route.size()) return;
        Wp wp = route.remove(index);
        route.add(target, wp);
        renumber();
        renderIdx.clear();
    }

    public static String save(String name) {
        if (route.isEmpty()) return "§cNo route loaded.";
        RouteStorage.put(name, route);
        loadedName = name;
        return "§aRoute saved as " + name + ". Load it with /tmap route load " + name;
    }

    public static String erase(String name) {
        if (!RouteStorage.remove(name)) return "§cRoute " + name + " doesn't exist.";
        return "§aRoute " + name + " deleted.";
    }

    public static String list() {
        List<String> names = RouteStorage.names();
        if (names.isEmpty()) return "§cNo saved routes.";
        return "§aSaved routes: §e" + String.join("§a, §e", names);
    }

    public static String toggleSetup() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        cfg.orderedSetupMode = !cfg.orderedSetupMode;
        TeslaMapsConfig.save();
        return "§aSetup mode " + (cfg.orderedSetupMode ? "enabled" : "disabled") + ".";
    }
}
