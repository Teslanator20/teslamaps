package com.teslamaps.features.rift;

// Ported from Skyblocker's rift/EnigmaSouls.java (positions: its enigma_soul_waypoints.json)
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.qol.QolStore;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class EnigmaSouls {

    private record Soul(BlockPos pos, String key, AABB box) {}

    private static List<Soul> souls;
    private static List<Soul> visible = List.of();
    private static List<Boolean> visibleFound = List.of();
    private static int ticks;

    private EnigmaSouls() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("EnigmaSouls").section("Rift").when(() -> cfg().riftEnigmaSouls)
                .tick(EnigmaSouls::tick)
                .world(EnigmaSouls::render)
                .serverChat(EnigmaSouls::onChat)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Enigma Soul Waypoints", () -> c.riftEnigmaSouls, v -> c.riftEnigmaSouls = v)
                            .desc("Boxes on all 52 enigma souls in the Rift. Found souls are remembered per profile. /tmap enigma found|missing|closest");
                    b.toggle("  Show Found Souls", () -> c.riftEnigmaShowFound, v -> c.riftEnigmaShowFound = v);
                    b.toggle("  Beacon Beams", () -> c.riftEnigmaBeams, v -> c.riftEnigmaBeams = v);
                    b.color("  Missing Color", () -> c.riftEnigmaMissingColor, v -> c.riftEnigmaMissingColor = v);
                    b.color("  Found Color", () -> c.riftEnigmaFoundColor, v -> c.riftEnigmaFoundColor = v);
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("enigma")
                        .executes(c -> status(c.getSource()))
                        .then(ClientCommands.literal("found").executes(c -> markAll(c.getSource(), true)))
                        .then(ClientCommands.literal("missing").executes(c -> markAll(c.getSource(), false)))
                        .then(ClientCommands.literal("closestfound").executes(c -> closest(c.getSource(), true)))
                        .then(ClientCommands.literal("closestmissing").executes(c -> closest(c.getSource(), false))))));
    }

    private static List<Soul> souls() {
        if (souls != null) return souls;
        List<Soul> out = new ArrayList<>(52);
        try (var in = EnigmaSouls.class.getResourceAsStream("/assets/teslamaps/data/enigma_souls.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("waypoints")) {
                JsonObject o = e.getAsJsonObject();
                BlockPos pos = new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
                out.add(new Soul(pos, pos.getX() + "," + pos.getY() + "," + pos.getZ(), new AABB(pos)));
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[EnigmaSouls] load failed: {}", e.toString());
        }
        return souls = List.copyOf(out);
    }

    private static final String BUTTONS_SOUL = "-74,65,-119";

    /** Buttons soul already found per the soul tracking; false while tracking is off. */
    public static boolean buttonsSoulFound() {
        return cfg().riftEnigmaSouls && found().contains(BUTTONS_SOUL);
    }

    private static Set<String> found() {
        return QolStore.get().enigmaFound;
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        if (!RiftSpot.inRift()) {
            visible = List.of();
            return;
        }
        boolean showFound = cfg().riftEnigmaShowFound;
        Set<String> f = found();
        List<Soul> vis = new ArrayList<>();
        List<Boolean> vf = new ArrayList<>();
        for (Soul s : souls()) {
            boolean isFound = f.contains(s.key);
            if (isFound && !showFound) continue;
            vis.add(s);
            vf.add(isFound);
        }
        visibleFound = vf;
        visible = vis;
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<Soul> vis = visible;
        List<Boolean> vf = visibleFound;
        if (vis.isEmpty() || vf.size() != vis.size()) return;
        TeslaMapsConfig c = cfg();
        int missing = TeslaMapsConfig.parseColor(c.riftEnigmaMissingColor);
        int found = TeslaMapsConfig.parseColor(c.riftEnigmaFoundColor);
        ESPRenderer.beginBoxBatch(pose, cam, false, true);
        for (int i = 0; i < vis.size(); i++) ESPRenderer.batchBox(vis.get(i).box, vf.get(i) ? found : missing, 2f);
        ESPRenderer.endBoxBatch();
        if (!c.riftEnigmaBeams) return;
        for (int i = 0; i < vis.size(); i++) {
            if (!vf.get(i)) ESPRenderer.drawBeaconBeam(pose, vis.get(i).pos, missing, cam);
        }
    }

    private static void onChat(String raw) {
        if (raw.indexOf("Enigma Soul") < 0 || !RiftSpot.inRift()) return;
        String t = StringUtil.stripTrim(raw);
        if (t.equals("SOUL! You unlocked an Enigma Soul!") || t.equals("You have already found that Enigma Soul!")) mark(true);
    }

    private static boolean mark(boolean asFound) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        Set<String> f = found();
        Vec3 p = mc.player.position();
        Soul best = null;
        double bestD = 16;
        for (Soul s : souls()) {
            if (f.contains(s.key) == asFound) continue;
            double d = s.pos.distToCenterSqr(p);
            if (d <= bestD) { bestD = d; best = s; }
        }
        if (best == null) return false;
        if (asFound) f.add(best.key);
        else f.remove(best.key);
        QolStore.save();
        ticks = 9;
        return true;
    }

    private static int status(FabricClientCommandSource src) {
        src.sendFeedback(Component.literal("§b[TeslaMaps] §7Enigma souls: §5" + found().size() + "§7/§5" + souls().size() + " §7found"));
        return 1;
    }

    private static int markAll(FabricClientCommandSource src, boolean asFound) {
        Set<String> f = found();
        f.clear();
        if (asFound) for (Soul s : souls()) f.add(s.key);
        QolStore.save();
        ticks = 9;
        src.sendFeedback(Component.literal("§b[TeslaMaps] §7Marked all enigma souls as " + (asFound ? "§afound" : "§cmissing") + "§7."));
        return 1;
    }

    private static int closest(FabricClientCommandSource src, boolean asFound) {
        boolean ok = mark(asFound);
        src.sendFeedback(Component.literal("§b[TeslaMaps] §7" + (ok ? "Closest enigma soul marked as " + (asFound ? "§afound" : "§cmissing")
                : "§cNo enigma soul within 4 blocks.")));
        return 1;
    }
}
