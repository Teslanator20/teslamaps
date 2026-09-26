package com.teslamaps.features.rift;

// After SkyHanni's rift/area/westvillage/RiftGunthersRace.kt
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.ParkourHelper;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.RepoCache;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Gunther's Rift Race: repo route with look-ahead waypoints while a race runs, other players optionally hidden. */
public final class RiftGuntherRace {

    private static final String URL = "https://raw.githubusercontent.com/hannibal002/SkyHanni-REPO/main/constants/rift/RiftRace.json";

    private static Feature feature;
    private static ParkourHelper helper;
    private static boolean loading, inRace, inArea;
    private static int ticks;
    private static Object lastLevel;

    private RiftGuntherRace() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("RiftGuntherRace").section("Rift").when(() -> cfg().riftGuntherRace)
                .tick(RiftGuntherRace::tick)
                .world(RiftGuntherRace::render)
                .serverChat(RiftGuntherRace::onChat)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Gunther's Rift Race Route", () -> c.riftGuntherRace, v -> c.riftGuntherRace = v)
                            .desc("West Village: after \"Race started!\" the race route shows the next waypoints. Clears on finish or cancel.")
                            .tags("rift", "race", "gunther", "west village", "parkour", "route");
                    b.slider("  Look Ahead", 1f, 10f, () -> (float) c.riftGuntherRaceLookAhead, v -> c.riftGuntherRaceLookAhead = Math.round(v));
                    b.toggle("  Rainbow Colors", () -> c.riftGuntherRaceRainbow, v -> c.riftGuntherRaceRainbow = v);
                    b.color("  Single Color", () -> c.riftGuntherRaceColor, v -> c.riftGuntherRaceColor = v);
                    b.toggle("  Hide Players During Race", () -> c.riftGuntherRaceHidePlayers, v -> c.riftGuntherRaceHidePlayers = v);
                });
    }

    /** From HidePlayers: other players are hidden while a race runs. */
    public static boolean hidePlayers() {
        return inRace && inArea && feature != null && cfg().riftGuntherRaceHidePlayers && feature.active();
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            inRace = false;
            if (helper != null) helper.reset();
        }
        if (ticks++ % 20 == 0) {
            inArea = RiftSpot.inRift() && RiftSpot.sub().equals("West Village");
        }
        if (!inArea) return;
        if (!loading) load();
        ParkourHelper h = helper;
        if (h == null) return;
        TeslaMapsConfig c = cfg();
        h.lookAhead = Math.max(1, c.riftGuntherRaceLookAhead) + 1;
        h.rainbow = c.riftGuntherRaceRainbow;
        h.color = (TeslaMapsConfig.parseColor(c.riftGuntherRaceColor) & 0xFFFFFF) | 0xB0000000;
        h.tick();
    }

    private static void render(PoseStack pose, Vec3 cam) {
        ParkourHelper h = helper;
        if (h == null || !inRace || !inArea) return;
        h.render(pose, cam);
    }

    private static void onChat(String raw) {
        String s = StringUtil.stripTrim(raw);
        if (!s.startsWith("RIFT RACING ")) return;
        if (s.startsWith("RIFT RACING Race started!")) {
            inRace = true;
        } else if (s.startsWith("RIFT RACING Race finished in") || s.startsWith("RIFT RACING Race cancelled!")) {
            inRace = false;
            if (helper != null) helper.reset();
        }
    }

    private static void load() {
        loading = true;
        Thread t = new Thread(() -> {
            try {
                JsonObject root = RepoCache.fetch("parkour_RiftRace", URL, 15).getAsJsonObject();
                List<Vec3> locs = new ArrayList<>();
                for (JsonElement e : root.getAsJsonArray("locations")) {
                    String[] p = e.getAsString().split(":");
                    locs.add(new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])));
                }
                List<ParkourHelper.Shortcut> cuts = new ArrayList<>();
                JsonArray arr = root.has("shortCuts") ? root.getAsJsonArray("shortCuts") : root.getAsJsonArray("short_cuts");
                if (arr != null) {
                    for (JsonElement e : arr) {
                        JsonObject o = e.getAsJsonObject();
                        cuts.add(new ParkourHelper.Shortcut(o.get("from").getAsInt(), o.get("to").getAsInt()));
                    }
                }
                ParkourHelper h = new ParkourHelper(locs, cuts, 1.0, 5.0, true);
                Minecraft.getInstance().execute(() -> helper = h);
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[RiftRace] repo load failed: {}", e.toString());
            }
        }, "TeslaMaps-RiftRace");
        t.setDaemon(true);
        t.start();
    }
}
