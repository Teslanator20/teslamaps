package com.teslamaps.features.kuudra;

// Ported from IQ's pile waypoints (pile_locations.json)
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Beam boxes on the supply piles during P1; the pile of a called-out missing pre gets its own color. */
public final class KuudraPileWaypoints {

    private record Pile(String name, Vec3 pos, BlockPos block, String label, String missingLabel) {
        Pile(String name, Vec3 pos) { this(name, pos, BlockPos.containing(pos), "§e" + name, "§c§l" + name + " §c(No Pre)"); }
    }

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("kuudra_piles.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern NO_PRE = Pattern.compile("^Party > (?:\\[[^]]+] )?\\w{1,16}: (?:\\[[^]]+] )?no (.+?)!*$", Pattern.CASE_INSENSITIVE);
    private static final Object[][] DEFAULTS = {
            {"Shop", -98.5, 78.4, -113.5}, {"Triangle", -94.5, 78.4, -106.5}, {"Equals", -106.5, 78.4, -99.5},
            {"Slash", -98.5, 78.4, -99.5}, {"X Cannon", -110.5, 78.4, -106.5}, {"X", -106.5, 78.4, -113.5}};

    private static List<Pile> all = List.of();
    private static final List<Pile> active = new ArrayList<>();
    private static String noPre;
    private static int lastPhase = -1, ticks;

    private KuudraPileWaypoints() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        load();
        Feature.of("KuudraPileWaypoints").section("Kuudra").when(() -> cfg().kuudraPileWaypoints)
                .chat(KuudraPileWaypoints::onChat)
                .tick(KuudraPileWaypoints::tick)
                .world(KuudraPileWaypoints::render);
    }

    public static void load() {
        List<Pile> out = new ArrayList<>();
        try {
            if (!Files.exists(FILE)) writeDefaults();
            JsonObject root = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), JsonObject.class);
            for (JsonElement e : root.getAsJsonArray("piles")) {
                JsonObject o = e.getAsJsonObject();
                JsonArray p = o.getAsJsonArray("position");
                Vec3 v = new Vec3(p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble());
                out.add(new Pile(o.get("name").getAsString(), v));
            }
        } catch (Exception ex) {
            TeslaMaps.LOGGER.warn("[TeslaMaps] kuudra_piles.json unreadable, using defaults: {}", ex.toString());
            out.clear();
            for (Object[] d : DEFAULTS) {
                Vec3 v = new Vec3((double) d[1], (double) d[2], (double) d[3]);
                out.add(new Pile((String) d[0], v));
            }
        }
        all = out;
        lastPhase = -1;
    }

    private static void writeDefaults() throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "Kuudra pile waypoints: name + [x, y, z]. Reload with the config button.");
        JsonArray piles = new JsonArray();
        for (Object[] d : DEFAULTS) {
            JsonObject o = new JsonObject();
            o.addProperty("name", (String) d[0]);
            JsonArray p = new JsonArray();
            p.add((double) d[1]);
            p.add((double) d[2]);
            p.add((double) d[3]);
            o.add("position", p);
            piles.add(o);
        }
        root.add("piles", piles);
        Files.createDirectories(FILE.getParent());
        Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    private static void onChat(String raw) {
        if (!KuudraRunInfo.inKuudra()) return;
        String s = KuudraRunInfo.strip(raw);
        Matcher m = NO_PRE.matcher(s);
        if (!m.matches()) return;
        String n = KuudraNoPre.canonical(m.group(1));
        if (n != null) noPre = n.equals("xCannon") ? "X Cannon" : n;
    }

    private static void tick() {
        int phase = KuudraRunInfo.supplies() ? 1 : 0;
        if (phase != lastPhase) {
            lastPhase = phase;
            active.clear();
            if (phase == 1) active.addAll(all);
            else noPre = null;
        }
        if (active.isEmpty() || ++ticks % 5 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand) || !e.hasCustomName() || e.getCustomName() == null) continue;
            if (!e.getCustomName().getString().contains("SUPPLIES RECEIVED")) continue;
            double x = e.getX(), y = e.getY(), z = e.getZ();
            active.removeIf(p -> {
                double dx = p.pos.x - x, dz = p.pos.z - z;
                return dx * dx + dz * dz <= 2.25 && Math.abs(p.pos.y - y) <= 5;
            });
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (active.isEmpty()) return;
        TeslaMapsConfig c = cfg();
        int normal = TeslaMapsConfig.parseColor(c.kuudraPileColor);
        int missing = TeslaMapsConfig.parseColor(c.kuudraPileNoPreColor);
        String np = noPre;
        for (int i = 0, n = active.size(); i < n; i++) {
            Pile p = active.get(i);
            boolean miss = p.name.equals(np);
            int col = miss ? missing : normal;
            ESPRenderer.drawBeaconBeam(pose, p.block, col, cam);
            ESPRenderer.drawBoxOutline(pose, new AABB(p.block), col | 0xFF000000, 2f, cam, true);
            if (c.kuudraPileNames) WaypointSet.drawLabel(pose, cam, miss ? p.missingLabel : p.label, p.pos.add(0, 2.5, 0), 1f, true);
        }
    }
}
