package com.teslamaps.features.kuudra;

// Ported from IQ's Etherwarp Helper (a/s.java, EtherwarpConfigLoader, etherwarp_config.json)
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

public final class KuudraEtherwarp {

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("kuudra_etherwarp.json");
    private static final String BUNDLED = "/assets/teslamaps/data/kuudra_etherwarp.json";

    record Spot(AABB box, Vec3 pos, int color) {}
    record Waypoint(String category, List<Spot> spots, EnumSet<Kuudra.Phase> phases, boolean filled, float lineWidth, double maxDistSq) {}

    private static List<String> categories = List.of();
    private static List<Waypoint> all = null;
    private static List<Waypoint> visible = List.of();
    private static Kuudra.Phase visibleFor = null;

    private KuudraEtherwarp() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("KuudraEtherwarp").section("Kuudra").when(() -> cfg().kuuEtherSpots && Kuudra.inKuudra())
                .tick(KuudraEtherwarp::tick)
                .world(KuudraEtherwarp::render)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Etherwarp");
                    b.toggle("Kuudra Etherwarp Spots", () -> c.kuuEtherSpots, v -> { c.kuuEtherSpots = v; visibleFor = null; })
                            .desc("Warp spots per phase from config/teslamaps/kuudra_etherwarp.json (IQ format, part-block shapes like EDGE_TOP)");
                    if (all == null) load();
                    for (String cat : categories) {
                        b.toggle("  " + cat, () -> !c.kuuEtherHidden.contains(cat), v -> {
                            if (v) c.kuuEtherHidden.remove(cat); else c.kuuEtherHidden.add(cat);
                            visibleFor = null;
                        });
                    }
                    b.button("  Reload Etherwarp Spots", () -> {
                        load();
                        var p = Minecraft.getInstance().player;
                        if (p != null) p.sendSystemMessage(Component.literal("§a[TeslaMaps] §7Etherwarp spots reloaded: §f"
                                + all.size() + " §7waypoints in §f" + categories.size() + " §7categories"));
                    });
                });
    }

    private static void tick() {
        if (all == null) load();
        Kuudra.Phase ph = Kuudra.phase();
        if (ph == visibleFor) return;
        visibleFor = ph;
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : all) {
            if (w.phases.contains(ph) && !cfg().kuuEtherHidden.contains(w.category)) out.add(w);
        }
        visible = out;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<Waypoint> list = visible;
        if (list.isEmpty()) return;
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        Vec3 me = p.position();
        for (Waypoint w : list) {
            for (Spot s : w.spots) {
                if (w.maxDistSq > 0 && me.distanceToSqr(s.pos) > w.maxDistSq) continue;
                if (w.filled) ESPRenderer.drawFilledBox(pose, s.box, s.color, cam, true);
                else ESPRenderer.drawBoxOutline(pose, s.box, s.color, w.lineWidth, cam, true);
            }
        }
    }

    static void load() {
        List<Waypoint> out = new ArrayList<>();
        List<String> cats = new ArrayList<>();
        if (!Files.exists(FILE)) {
            try (InputStream in = KuudraEtherwarp.class.getResourceAsStream(BUNDLED)) {
                Files.createDirectories(FILE.getParent());
                if (in != null) Files.copy(in, FILE);
            } catch (Exception ex) {
                TeslaMaps.LOGGER.warn("could not copy kuudra_etherwarp.json to config", ex);
            }
        }
        try {
            JsonObject root;
            if (Files.exists(FILE)) {
                root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            } else {
                try (InputStream in = KuudraEtherwarp.class.getResourceAsStream(BUNDLED)) {
                    root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                }
            }
            for (JsonElement ce : root.getAsJsonArray("categories")) {
                JsonObject co = ce.getAsJsonObject();
                if (co.has("enabled") && !co.get("enabled").getAsBoolean()) continue;
                String cat = co.get("name").getAsString();
                cats.add(cat);
                for (JsonElement we : co.getAsJsonArray("waypoints")) {
                    try {
                        Waypoint w = parse(cat, we.getAsJsonObject());
                        if (w != null) out.add(w);
                    } catch (RuntimeException ex) {
                        TeslaMaps.LOGGER.warn("bad etherwarp waypoint in {}", cat, ex);
                    }
                }
            }
        } catch (Exception ex) {
            TeslaMaps.LOGGER.warn("failed to load kuudra_etherwarp.json", ex);
        }
        all = out;
        categories = cats;
        visibleFor = null;
    }

    private static Waypoint parse(String cat, JsonObject o) {
        List<Vec3> positions = new ArrayList<>();
        if (o.has("positions") && o.get("positions").isJsonArray())
            for (JsonElement e : o.getAsJsonArray("positions")) {
                Vec3 v = vec(e);
                if (v != null && !positions.contains(v)) positions.add(v);
            }
        if (o.has("position")) {
            Vec3 v = vec(o.get("position"));
            if (v != null && !positions.contains(v)) positions.add(v);
        }
        EnumSet<Kuudra.Phase> phases = phases(o, "showInPhases");
        phases.removeAll(phases(o, "hideInPhases"));
        if (positions.isEmpty() || phases.isEmpty()) return null;

        float alpha = o.has("alpha") ? Math.clamp(o.get("alpha").getAsFloat(), 0f, 1f) : 0.8f;
        int a = Math.round(alpha * 255) << 24;
        int base = color(o.get("color"), 0xFFFFFF);
        List<Integer> colors = new ArrayList<>();
        if (o.has("colors") && o.get("colors").isJsonArray())
            for (JsonElement e : o.getAsJsonArray("colors")) colors.add(color(e, base));
        String shape = o.has("shape") ? o.get("shape").getAsString().trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_') : "FULL";
        double[] custom = customBox(o);

        List<Spot> spots = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            Vec3 p = positions.get(i);
            int rgb = colors.isEmpty() ? base : colors.get(Math.min(i, colors.size() - 1));
            spots.add(new Spot(box(shape, p, custom), p, a | rgb));
        }
        boolean filled = o.has("renderStyle") && o.get("renderStyle").getAsString().equalsIgnoreCase("SOLID");
        float lw = o.has("lineWidth") ? Math.clamp(o.get("lineWidth").getAsFloat(), 0.5f, 4f) : 2f;
        double max = o.has("maxRenderDistance") ? o.get("maxRenderDistance").getAsDouble() : -1;
        return new Waypoint(cat, spots, phases, filled, lw, max > 0 ? max * max : -1);
    }

    private static AABB box(String shape, Vec3 p, double[] c) {
        double x = p.x, y = p.y, z = p.z;
        return switch (shape) {
            case "TOP" -> new AABB(x - 0.5, y + 0.875, z - 0.5, x + 0.5, y + 1, z + 0.5);
            case "BOTTOM" -> new AABB(x - 0.5, y, z - 0.5, x + 0.5, y + 0.125, z + 0.5);
            case "SLAB_LOWER", "HALF_LOWER", "LOWER_SLAB", "LOWER_HALF", "BOTTOM_HALF" -> new AABB(x - 0.5, y, z - 0.5, x + 0.5, y + 0.5, z + 0.5);
            case "SLAB_UPPER", "HALF_UPPER", "UPPER_SLAB", "UPPER_HALF", "TOP_HALF" -> new AABB(x - 0.5, y + 0.5, z - 0.5, x + 0.5, y + 1, z + 0.5);
            case "CENTER_PLATE", "PLATE", "CENTER" -> new AABB(x - 0.25, y + 0.45, z - 0.25, x + 0.25, y + 0.55, z + 0.25);
            case "EDGE_TOP", "TOP_EDGE" -> new AABB(x - 0.5, y + 0.95, z - 0.5, x + 0.5, y + 1, z + 0.5);
            case "EDGE_BOTTOM", "BOTTOM_EDGE" -> new AABB(x - 0.5, y, z - 0.5, x + 0.5, y + 0.05, z + 0.5);
            case "PILLAR" -> new AABB(x - 0.2, y, z - 0.2, x + 0.2, y + 1, z + 0.2);
            case "CUSTOM" -> new AABB(x + c[0], y + c[1], z + c[2], x + c[3], y + c[4], z + c[5]);
            default -> new AABB(x - 0.5, y, z - 0.5, x + 0.5, y + 1, z + 0.5);
        };
    }

    private static double[] customBox(JsonObject o) {
        double[] def = {-0.5, 0, -0.5, 0.5, 1, 0.5};
        if (!o.has("box") || !o.get("box").isJsonObject()) return def;
        JsonObject b = o.getAsJsonObject("box");
        Vec3 min = b.has("min") ? vec(b.get("min")) : null, max = b.has("max") ? vec(b.get("max")) : null;
        if (min == null || max == null || max.x <= min.x || max.y <= min.y || max.z <= min.z) return def;
        return new double[]{min.x, min.y, min.z, max.x, max.y, max.z};
    }

    private static Vec3 vec(JsonElement e) {
        if (e == null || !e.isJsonArray()) return null;
        JsonArray a = e.getAsJsonArray();
        if (a.size() != 3) return null;
        try {
            return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static int color(JsonElement e, int def) {
        if (e == null || e.isJsonNull()) return def;
        try {
            if (e.isJsonArray() && e.getAsJsonArray().size() == 3) {
                JsonArray a = e.getAsJsonArray();
                return Math.clamp(a.get(0).getAsInt(), 0, 255) << 16 | Math.clamp(a.get(1).getAsInt(), 0, 255) << 8 | Math.clamp(a.get(2).getAsInt(), 0, 255);
            }
            String s = e.getAsString().trim();
            if (s.startsWith("#")) s = s.substring(1);
            if (s.length() == 6) return Integer.parseInt(s, 16);
        } catch (RuntimeException ignored) {}
        return def;
    }

    private static EnumSet<Kuudra.Phase> phases(JsonObject o, String key) {
        EnumSet<Kuudra.Phase> set = EnumSet.noneOf(Kuudra.Phase.class);
        if (!o.has(key) || !o.get(key).isJsonArray()) return set;
        for (JsonElement e : o.getAsJsonArray(key)) {
            String s = e.getAsString().trim().toUpperCase(Locale.ROOT);
            if (s.isEmpty()) continue;
            for (Kuudra.Phase p : Kuudra.Phase.values()) if (p.name().equals(s)) set.add(p);
        }
        return set;
    }
}
