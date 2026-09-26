package com.teslamaps.features;

// Ported from SkyHanni's rift/area/mirrorverse/RiftUpsideDownParkour, RiftLavaMazeParkour, TubulatorParkour
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.ParkourHelper;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.RepoCache;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ParkourGuides {

    private static final String REPO = "https://raw.githubusercontent.com/hannibal002/SkyHanni-REPO/main/constants/%s.json";

    private static ParkourHelper upsideDown, lavaMaze;
    private static final ParkourHelper[] helpers = new ParkourHelper[3];
    private static boolean loading, inRift;

    private ParkourGuides() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ParkourGuides").section("Ordered Waypoints").when(() -> cfg().parkourGuides)
                .tick(ParkourGuides::tick)
                .world(ParkourGuides::render)
                .serverChat(ParkourGuides::onChat)
                .config("Ordered Waypoints", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Parkour Guides");
                    b.toggle("Rift Parkour Guides", () -> c.parkourGuides, v -> c.parkourGuides = v)
                            .desc("Mirrorverse Upside Down, Lava Maze and Tubulator: next platforms, shortcuts, rainbow line. Walk onto the first platform to start.");
                    b.slider("  Look Ahead", 1f, 10f, () -> (float) c.parkourLookAhead, v -> c.parkourLookAhead = Math.round(v));
                    b.toggle("  Rainbow Colors", () -> c.parkourRainbow, v -> c.parkourRainbow = v);
                    b.color("  Single Color", () -> c.parkourColor, v -> c.parkourColor = v);
                    b.toggle("  Show Shortcuts", () -> c.parkourShortcuts, v -> c.parkourShortcuts = v);
                    b.toggle("  Hide Players in Mirrorverse", () -> c.parkourHidePlayers, v -> c.parkourHidePlayers = v);
                });
    }

    public static boolean hidePlayers() {
        TeslaMapsConfig c = cfg();
        return c.parkourGuides && c.parkourHidePlayers && c.section("Ordered Waypoints")
                && com.teslamaps.features.rift.RiftSpot.inMirrorverse();
    }

    private static void tick() {
        inRift = com.teslamaps.features.rift.RiftSpot.inRift();
        if (!inRift) return;
        if (!loading) load();
        TeslaMapsConfig c = cfg();
        for (ParkourHelper h : helpers) {
            if (h == null) continue;
            h.lookAhead = Math.max(1, c.parkourLookAhead) + 1;
            h.rainbow = c.parkourRainbow;
            h.color = (TeslaMapsConfig.parseColor(c.parkourColor) & 0xFFFFFF) | 0xB0000000;
            h.showShortcuts = c.parkourShortcuts;
            h.tick();
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!inRift) return;
        for (ParkourHelper h : helpers) if (h != null) h.render(pose, cam);
    }

    private static void onChat(String raw) {
        String s = StringUtil.strip(raw).trim();
        if (s.equals("OH NO! THE LAVA OOFED YOU BACK TO THE START!") && upsideDown != null) upsideDown.reset();
        else if (s.equals("EEK! THE LAVA OOFED YOU!") && lavaMaze != null) lavaMaze.reset();
    }

    private static void load() {
        loading = true;
        Thread t = new Thread(() -> {
            try {
                ParkourHelper u = parse("RiftUpsideDownParkour", -1, 2.0, 2.0);
                ParkourHelper l = parse("RiftLavaMazeParkour", 0, 1.0, 1.0);
                ParkourHelper tb = parse("RiftTubulator", 0, 1.0, 2.0);
                Minecraft.getInstance().execute(() -> {
                    upsideDown = helpers[0] = u;
                    lavaMaze = helpers[1] = l;
                    helpers[2] = tb;
                });
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[Parkour] repo load failed: {}", e.toString());
            }
        }, "TeslaMaps-Parkour");
        t.setDaemon(true);
        t.start();
    }

    private static ParkourHelper parse(String name, double offset, double size, double range) throws Exception {
        JsonObject root = RepoCache.fetch("parkour_" + name, String.format(REPO, name), 15).getAsJsonObject();
        List<Vec3> locs = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("locations")) {
            String[] p = e.getAsString().split(":");
            locs.add(new Vec3(Double.parseDouble(p[0]) + offset, Double.parseDouble(p[1]) + offset, Double.parseDouble(p[2]) + offset));
        }
        List<ParkourHelper.Shortcut> cuts = new ArrayList<>();
        var arr = root.has("shortCuts") ? root.getAsJsonArray("shortCuts") : root.getAsJsonArray("short_cuts");
        if (arr != null) {
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                cuts.add(new ParkourHelper.Shortcut(o.get("from").getAsInt(), o.get("to").getAsInt()));
            }
        }
        return new ParkourHelper(locs, cuts, size, range, false);
    }
}
