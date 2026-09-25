package com.teslamaps.features.mining;

// Ported from SkyHanni's features/mining/DeepCavernsGuide.kt, path is SkyHanni's DeepCavernsParkour.json, bundled
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class DeepCavernsGuide {

    private static final double RANGE = 3.5;
    private static List<Vec3> route;
    private static boolean loadFailed, show, liftChecked;
    private static int current = -1;

    private DeepCavernsGuide() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("deepcaverns")
                        .executes(c -> { start(true); return 1; })
                        .then(ClientCommands.literal("stop").executes(c -> { show = false; return 1; })))));
        Feature.of("DeepCavernsGuide").section("Mining").when(() -> cfg().deepCavernsGuide)
                .tick(DeepCavernsGuide::tick)
                .serverChat(DeepCavernsGuide::onChat)
                .world(DeepCavernsGuide::render)
                .config("Mining", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Deep Caverns");
                    b.toggle("Deep Caverns Guide", () -> c.deepCavernsGuide, v -> c.deepCavernsGuide = v)
                            .desc("Parkour path to the bottom of the Deep Caverns while the lift is still locked. Starts on its own, or /tmap deepcaverns")
                            .tags("lift", "parkour", "obsidian sanctuary", "rhys");
                    b.slider("  Look Ahead", 1f, 10f, () -> (float) c.deepCavernsLookAhead,
                            v -> c.deepCavernsLookAhead = Math.round(v), 0);
                    b.button("  Start Guide", () -> start(true));
                });
    }

    private static void load() {
        if (route != null || loadFailed) return;
        try (var in = DeepCavernsGuide.class.getResourceAsStream("/assets/teslamaps/data/deep_caverns_parkour.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Vec3> list = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("locations")) {
                String[] p = e.getAsString().split(":");
                list.add(new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])));
            }
            route = list;
        } catch (Exception e) {
            loadFailed = true;
            TeslaMaps.LOGGER.error("Failed to load deep_caverns_parkour.json", e);
        }
    }

    private static boolean inCaverns() {
        return SkyblockUtils.isInArea("Deep Caverns");
    }

    private static void start(boolean manual) {
        if (show) return;
        load();
        if (route == null) return;
        show = true;
        current = -1;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §e" + (manual ? "Deep Caverns Guide enabled."
                    : "Lift locked - enabling the Deep Caverns Guide to the bottom.")));
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!inCaverns()) {
            show = false;
            liftChecked = false;
            return;
        }
        if (mc.screen instanceof AbstractContainerScreen<?> cs
                && StringUtil.stripTrim(cs.getTitle().getString()).equals("Lift")) {
            if (!liftChecked) {
                for (Slot s : cs.getMenu().slots) {
                    if (s.container instanceof Inventory || s.getContainerSlot() != 31 || s.getItem().isEmpty()) continue;
                    liftChecked = true;
                    if (!StringUtil.stripTrim(s.getItem().getHoverName().getString()).equals("Obsidian Sanctuary")) start(false);
                }
            }
        } else {
            liftChecked = false;
        }
        if (!show || route == null || mc.player == null) return;
        if (mc.player.onGround()) {
            Vec3 pos = mc.player.position();
            for (int i = 0; i < route.size(); i++) {
                if (pos.distanceTo(center(route.get(i))) < RANGE) current = i;
            }
        }
        if (current == route.size() - 1) {
            show = false;
            mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §eReached the bottom of the Deep Caverns."));
        }
    }

    private static void onChat(String text) {
        if (!inCaverns()) return;
        if (StringUtil.stripTrim(text).equals("[NPC] Lift Operator: Venture down into the Lapis Quarry to unlock my Lift Menu!")) {
            start(false);
        }
    }

    private static Vec3 center(Vec3 v) {
        return v.add(0.5, 1.0, 0.5);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<Vec3> r = route;
        if (!show || r == null || !inCaverns()) return;
        if (current < 0) {
            Vec3 s = r.get(0);
            WaypointSet.draw(pose, cam, BlockPos.containing(s.x, s.y, s.z), 0xFF55FF55, "§aDeep Caverns Guide Start", true, false, true);
        }
        int look = Math.max(1, cfg().deepCavernsLookAhead) + 1;
        int from = Math.max(0, current);
        int to = Math.min(r.size(), from + look);
        for (int i = from; i < to; i++) {
            Vec3 l = r.get(i);
            int col = color(i);
            ESPRenderer.drawFilledBox(pose, new AABB(l.x, l.y, l.z, l.x + 1, l.y + 1, l.z + 1).inflate(0.05), col, cam, true);
            if (i + 1 < to) ESPRenderer.drawLine(pose, center(l), center(r.get(i + 1)), col, 3f, cam);
        }
    }

    private static int color(int index) {
        float hue = ((-index / 12f) % 1f + 1f) % 1f;
        return 0xB0000000 | (java.awt.Color.HSBtoRGB(hue, 1f, 0.7f) & 0xFFFFFF);
    }
}
