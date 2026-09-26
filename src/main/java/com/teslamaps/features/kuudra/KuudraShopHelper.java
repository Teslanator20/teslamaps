package com.teslamaps.features.kuudra;

// Ported from Shizo's kuudra/general/ShopHelper.kt (shop-helper-coords.json)
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.monster.MagmaCube;
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
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** P1: maps new Shop / X Cannon tentacles to saved spawn points S1-S5 / XC1-XC3 and shows the safe spot for the combo. */
public final class KuudraShopHelper {

    public static final String[] TARGETS = {"S1", "S2", "S3", "S4", "S5", "XC1", "XC2", "XC3", "Safe Shop Default", "Safe XC Default"};
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("kuudra_shop_helper.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private record Tracked(MagmaCube cube, String name, String label) {}
    private record Spot(Vec3 pos) {}

    private static final Map<String, double[]> saved = new LinkedHashMap<>();
    private static final Map<Integer, Tracked> tracked = new HashMap<>();
    private static final Set<Integer> blacklist = new HashSet<>();
    private static final Set<String> spawned = new TreeSet<>();
    private static final List<Spot> safe = new ArrayList<>();
    private static boolean shopCombo;

    private KuudraShopHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        load();
        Feature.of("KuudraShopHelper").section("Kuudra").when(() -> cfg().kuudraShopHelper)
                .tick(KuudraShopHelper::tick)
                .world(KuudraShopHelper::render);
    }

    private static void reset() {
        tracked.clear();
        blacklist.clear();
        spawned.clear();
        safe.clear();
    }

    private static void tick() {
        long start = KuudraRunInfo.startMs();
        if (!KuudraRunInfo.supplies() || start == 0) {
            if (!tracked.isEmpty() || !blacklist.isEmpty() || !safe.isEmpty()) reset();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > 15000) { tracked.values().removeIf(t -> !t.cube.isAlive()); return; }
        List<MagmaCube> cubes = mc.level.getEntitiesOfClass(MagmaCube.class, p.getBoundingBox().inflate(120));
        if (elapsed < 9000) {
            for (MagmaCube c : cubes) blacklist.add(c.getId());
            return;
        }
        boolean nearShop = dist2(p.getX(), p.getZ(), -75, -132) < 35 * 35;
        boolean nearXc = dist2(p.getX(), p.getZ(), -140, -127) < 35 * 35;
        if (!nearShop && !nearXc) return;
        for (MagmaCube c : cubes) {
            if (blacklist.contains(c.getId()) || tracked.containsKey(c.getId()) || c.getY() > 77) continue;
            double x = c.getX(), z = c.getZ();
            boolean shop;
            if (nearShop && x >= -95 && x <= -55 && z >= -155 && z <= -110) shop = true;
            else if (nearXc && x >= -150 && x <= -130 && z >= -135 && z <= -120) shop = false;
            else continue;
            String best = null;
            double bestD = 64;
            for (Map.Entry<String, double[]> e : saved.entrySet()) {
                String k = e.getKey();
                if (shop ? !(k.length() == 2 && k.startsWith("S")) : !(k.length() == 3 && k.startsWith("XC"))) continue;
                double d = dist2(e.getValue()[0], e.getValue()[2], x, z);
                if (d < bestD) { bestD = d; best = k; }
            }
            if (best == null) continue;
            tracked.put(c.getId(), new Tracked(c, best, "§c" + best));
            spawned.add(best);
            shopCombo = shop;
            p.sendSystemMessage(Component.literal("§a[TeslaMaps] §c[!] §fSpawned: §b" + best));
            updateSafe();
        }
        tracked.values().removeIf(t -> !t.cube.isAlive());
    }

    private static double dist2(double ax, double az, double bx, double bz) {
        double dx = ax - bx, dz = az - bz;
        return dx * dx + dz * dz;
    }

    private static String comboKey() {
        return (shopCombo ? "SHOP_SAFE_" : "XC_SAFE_") + String.join("_", spawned);
    }

    private static boolean matchesCombo(String k, String base) {
        if (k.equals(base)) return true;
        if (!k.startsWith(base + "_")) return false;
        String rest = k.substring(base.length() + 1);
        return !rest.isEmpty() && rest.chars().allMatch(Character::isDigit);
    }

    private static void updateSafe() {
        safe.clear();
        if (spawned.isEmpty()) return;
        String base = comboKey();
        for (Map.Entry<String, double[]> e : saved.entrySet())
            if (matchesCombo(e.getKey(), base)) safe.add(new Spot(vec(e.getValue())));
        if (safe.isEmpty()) {
            double[] d = saved.get(shopCombo ? "Safe Shop Default" : "Safe XC Default");
            if (d != null) safe.add(new Spot(vec(d)));
        }
    }

    private static Vec3 vec(double[] d) { return new Vec3(d[0], d[1], d[2]); }

    private static void render(PoseStack pose, Vec3 cam) {
        if (tracked.isEmpty() && safe.isEmpty()) return;
        TeslaMapsConfig c = cfg();
        if (c.kuudraShopTentacles) {
            int col = TeslaMapsConfig.parseColor(c.kuudraShopTentacleColor) | 0xFF000000;
            for (Tracked t : tracked.values()) {
                Vec3 p = t.cube.getPosition(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
                ESPRenderer.drawBoxOutline(pose, new AABB(p.x - 1.5, p.y, p.z - 1.5, p.x + 1.5, p.y + 10, p.z + 1.5), col, 2f, cam, true);
                WaypointSet.drawLabel(pose, cam, t.label, p.add(0, 10.5, 0), 2f, true);
            }
        }
        int sc = TeslaMapsConfig.parseColor(c.kuudraShopSafeColor) | 0xFF000000;
        for (int i = 0, n = safe.size(); i < n; i++) {
            Vec3 p = safe.get(i).pos;
            ESPRenderer.drawBoxOutline(pose, new AABB(p.x - 0.5, p.y, p.z - 0.5, p.x + 0.5, p.y + 1, p.z + 0.5), sc, 2f, cam, true);
        }
    }

    // config buttons

    public static void setTarget() {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        String t = cfg().kuudraShopTarget;
        saved.put(t, new double[]{p.getX(), p.getY(), p.getZ()});
        save();
        msg("§aSet §b" + t + " §ato §f" + p.getBlockX() + ", " + p.getBlockY() + ", " + p.getBlockZ());
    }

    public static void saveComboSpot() {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        if (spawned.isEmpty()) { msg("§cNo tentacles tracked right now, wait for them to spawn first"); return; }
        String base = comboKey(), key = base;
        for (int i = 2; saved.containsKey(key); i++) key = base + "_" + i;
        saved.put(key, new double[]{p.getX(), p.getY(), p.getZ()});
        save();
        updateSafe();
        msg("§aSaved safe spot §b" + key);
    }

    public static void clearCombo() {
        if (spawned.isEmpty()) { msg("§cNo tentacles tracked right now"); return; }
        String base = comboKey();
        int before = saved.size();
        saved.keySet().removeIf(k -> matchesCombo(k, base));
        save();
        updateSafe();
        msg("§cCleared §f" + (before - saved.size()) + " §cspots for §b" + base);
    }

    private static void msg(String s) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§a[TeslaMaps] " + s));
    }

    private static void load() {
        saved.clear();
        try {
            if (Files.exists(FILE)) {
                Map<String, double[]> m = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8),
                        new TypeToken<LinkedHashMap<String, double[]>>() {}.getType());
                if (m != null) m.forEach((k, v) -> { if (v != null && v.length == 3) saved.put(k, v); });
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[TeslaMaps] kuudra_shop_helper.json unreadable: {}", e.toString());
        }
        int n = saved.size();
        defaults();
        if (saved.size() != n) save();
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(saved), StandardCharsets.UTF_8);
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[TeslaMaps] could not save kuudra_shop_helper.json: {}", e.toString());
        }
    }

    private static void defaults() {
        Object[][] d = {
                {"S1", -68.5, 69.0, -151.5}, {"S2", -87.5, 69.0, -134.5}, {"S3", -80.0, 76.0, -130.0},
                {"S4", -61.5, 69.0, -127.5}, {"S5", -90.0, 76.0, -140.0},
                {"XC1", -141.5, 69.0, -118.5}, {"XC2", -146.5, 69.0, -125.5}, {"XC3", -144.5, 69.0, -140.5},
                {"Safe Shop Default", -82.0, 76.0, -122.0}, {"Safe XC Default", -140.0, 76.0, -110.0},
                {"SHOP_SAFE_S1_S5", -70.5, 79.0, -134.5}, {"SHOP_SAFE_S5", -70.5, 79.0, -134.5},
                {"SHOP_SAFE_S5_2", -71.5, 78.0, -135.5}, {"SHOP_SAFE_S5_3", -85.5, 78.0, -128.5},
                {"SHOP_SAFE_S1_S4_S5", -85.3, 78.0, -128.3}, {"SHOP_SAFE_S4_S5", -71.7, 78.0, -135.8},
                {"SHOP_SAFE_S4_S5_2", -77.5, 78.0, -135.5}, {"SHOP_SAFE_S4_S5_3", -71.5, 77.0, -136.5},
                {"SHOP_SAFE_S4_S5_4", -85.3, 78.0, -128.3},
                {"XC_SAFE_XC2", -130.5, 78.0, -114.5}, {"XC_SAFE_XC2_2", -134.5, 78.0, -128.5}};
        for (Object[] r : d) saved.putIfAbsent((String) r[0], new double[]{(double) r[1], (double) r[2], (double) r[3]});
    }
}
