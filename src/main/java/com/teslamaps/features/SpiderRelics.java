package com.teslamaps.features;

// Ported from Skyblocker's Relics.java (positions from its spidersden/relics.json) and SkyHanni's SpiderDenRelicPathfinder.kt
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SpiderRelics {

    private static final int[][] RELICS = {
            {-342, 122, -253}, {-384, 89, -225}, {-274, 100, -178}, {-178, 136, -297}, {-147, 83, -335},
            {-188, 80, -346}, {-183, 68, -283}, {-342, 89, -221}, {-355, 86, -213}, {-372, 89, -242},
            {-354, 73, -285}, {-317, 69, -273}, {-296, 37, -270}, {-275, 64, -272}, {-303, 71, -318},
            {-311, 69, -251}, {-348, 65, -202}, {-328, 50, -238}, {-313, 58, -250}, {-300, 51, -254},
            {-284, 49, -234}, {-300, 50, -218}, {-236, 51, -239}, {-183, 51, -252}, {-217, 58, -304},
            {-272, 48, -291}, {-225, 70, -316}, {-254, 57, -279}};
    private static final int TOTAL = RELICS.length;
    private static final int MISSING = 0xFFFED83D, FOUND = 0xFF835432, PATH = 0xFFAA00AA;

    private static final Gson GSON = new Gson();
    private static final ProfiledFile FILE = new ProfiledFile("found_relics.json", null);
    private static final BlockPos[] POS = new BlockPos[TOTAL];
    private static final AABB[] BOX = new AABB[TOTAL];

    static {
        for (int i = 0; i < TOTAL; i++) {
            POS[i] = new BlockPos(RELICS[i][0], RELICS[i][1], RELICS[i][2]);
            BOX[i] = new AABB(POS[i]);
        }
    }

    private static final Set<Integer> found = new HashSet<>();
    private static boolean loaded;
    private static boolean inDen;
    private static int ticks;
    private static int next = -1;
    private static Vec3 nextLabelPos;
    private static String nextLabel;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SpiderRelics").section("Combat")
                .when(() -> cfg().spiderRelics)
                .tick(SpiderRelics::tick)
                .world(SpiderRelics::render)
                .serverChat(SpiderRelics::onChat)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Spider's Den Relics");
                    b.toggle("Relic Waypoints", () -> c.spiderRelics, v -> c.spiderRelics = v)
                            .desc("Missing relics yellow in Spider's Den. Picked-up ones are remembered per profile. /tmap relics foundall | reset")
                            .tags("relic", "spider");
                    b.toggle("  Show Found (brown)", () -> c.spiderRelicsShowFound, v -> c.spiderRelicsShowFound = v);
                    b.toggle("  Route Line + Progress", () -> c.spiderRelicsPath, v -> c.spiderRelicsPath = v)
                            .desc("Line to the next missing relic on a short route, with found/total.");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("relics")
                        .then(ClientCommands.literal("foundall").executes(c -> {
                            ensure();
                            for (int i = 0; i < TOTAL; i++) found.add(i);
                            changed();
                            c.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §7Marked all relics as found."));
                            return 1;
                        }))
                        .then(ClientCommands.literal("reset").executes(c -> {
                            ensure();
                            found.clear();
                            changed();
                            c.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §7Marked all relics as missing."));
                            return 1;
                        })))));
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        inDen = SkyblockUtils.isInArea("Spider's Den");
        if (!inDen) return;
        ensure();
        if (next >= 0 && found.contains(next)) next = -1;
        if (next < 0) route();
        if (next >= 0) {
            float pct = found.size() * 100f / TOTAL;
            nextLabel = "§b" + found.size() + "/" + TOTAL + " §5Relics §8(§b" + String.format(Locale.ROOT, "%.1f", pct) + "%§8)";
            nextLabelPos = new Vec3(POS[next].getX() + 0.5, POS[next].getY() + 1.5, POS[next].getZ() + 0.5);
        } else {
            nextLabel = null;
        }
    }

    // greedy nearest-neighbour route from the player, first stop is the target
    private static void route() {
        next = -1;
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < TOTAL; i++) {
            if (found.contains(i)) continue;
            double d = POS[i].distToCenterSqr(p.position());
            if (d < best) { best = d; next = i; }
        }
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!inDen || !loaded) return;
        TeslaMapsConfig c = cfg();
        for (int i = 0; i < TOTAL; i++) {
            boolean f = found.contains(i);
            if (f && !c.spiderRelicsShowFound) continue;
            ESPRenderer.drawBoxOutline(pose, BOX[i], f ? FOUND : MISSING, 2f, cam, true);
            if (!f) ESPRenderer.drawBeaconBeam(pose, POS[i], MISSING & 0xFFFFFF, cam);
        }
        if (c.spiderRelicsPath && next >= 0 && nextLabel != null) {
            ESPRenderer.drawTracerFromCamera(pose, BOX[next].getCenter(), PATH, cam, true, 2f);
            ESPRenderer.drawText(pose, nextLabel, nextLabelPos, 1f, cam, true, true);
        }
    }

    private static void onChat(String raw) {
        String s = StringUtil.stripTrim(raw);
        boolean pickup = s.startsWith("+") && s.endsWith(" Relics)") && s.contains("Coins! (");
        if (!pickup && !s.equals("You've already found this relic!") && !s.equals("You've already found all the relics!")) return;
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        ensure();
        if (s.equals("You've already found all the relics!")) {
            for (int i = 0; i < TOTAL; i++) found.add(i);
            changed();
            return;
        }
        int nearest = -1;
        double best = 100;
        for (int i = 0; i < TOTAL; i++) {
            double d = POS[i].distToCenterSqr(p.position());
            if (d <= best) { best = d; nearest = i; }
        }
        if (nearest >= 0 && found.add(nearest)) changed();
    }

    private static void changed() {
        next = -1;
        save();
    }

    private static void ensure() {
        if (!loaded || FILE.stale()) load();
    }

    private static void load() {
        found.clear();
        next = -1;
        loaded = true;
        Path path = FILE.path();
        try {
            if (path != null && Files.exists(path)) {
                List<String> read = GSON.fromJson(Files.readString(path), new TypeToken<List<String>>() {}.getType());
                if (read != null) for (String k : read) {
                    int i = index(k);
                    if (i >= 0) found.add(i);
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load found_relics.json", e);
        }
    }

    private static void save() {
        Path path = FILE.path();
        if (path == null) return;
        List<String> out = new ArrayList<>();
        for (int i : found) out.add(POS[i].getX() + "," + POS[i].getY() + "," + POS[i].getZ());
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(out));
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to save found_relics.json", e);
        }
    }

    private static int index(String key) {
        for (int i = 0; i < TOTAL; i++) {
            if (key.equals(POS[i].getX() + "," + POS[i].getY() + "," + POS[i].getZ())) return i;
        }
        return -1;
    }
}
