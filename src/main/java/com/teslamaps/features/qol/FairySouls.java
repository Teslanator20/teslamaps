package com.teslamaps.features.qol;

// Ported from Skyblocker's FairySouls.java, soul positions from NEU-REPO constants/fairy_souls.json
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.RepoCache;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FairySouls {

    private static final String URL = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/fairy_souls.json";
    // tab "Area:" -> NEU location key
    private static final Map<String, String> AREAS = Map.ofEntries(
            Map.entry("Hub", "hub"), Map.entry("Spider's Den", "combat_1"), Map.entry("Crimson Isle", "crimson_isle"),
            Map.entry("The End", "combat_3"), Map.entry("The Park", "foraging_1"), Map.entry("Galatea", "foraging_2"),
            Map.entry("Moonglade Marsh", "foraging_2"), Map.entry("Torrhus Canyon", "foraging_3"),
            Map.entry("The Farming Islands", "farming_1"), Map.entry("Gold Mine", "mining_1"),
            Map.entry("Deep Caverns", "mining_2"), Map.entry("Dwarven Mines", "mining_3"),
            Map.entry("Jerry's Workshop", "winter"), Map.entry("Dungeon Hub", "dungeon_hub"),
            Map.entry("Backwater Bayou", "fishing_1"), Map.entry("Lotus Atoll", "lotus_atoll"),
            Map.entry("Critter Safari", "safari"));

    private record Soul(BlockPos pos, String key, AABB box) {}

    private static volatile Map<String, List<Soul>> souls = Map.of();
    private static volatile int maxSouls;
    private static boolean requested;
    private static String location;
    private static List<Soul> visible = List.of();
    private static List<Boolean> visibleFound = List.of();
    private static int ticks;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("FairySouls").section("Secrets & Waypoints")
                .when(() -> cfg().fairySouls)
                .tick(FairySouls::tick)
                .world(FairySouls::render)
                .serverChat(FairySouls::onChat)
                .config("Secrets & Waypoints", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Fairy Souls");
                    b.toggle("Fairy Soul Waypoints", () -> c.fairySouls, v -> c.fairySouls = v)
                            .desc("Boxes on every fairy soul of the island (NEU repo). Picked souls are remembered per profile. /tmap fairysouls found|missing marks the whole island.");
                    b.toggle("  Show Found Souls", () -> c.fairySoulsShowFound, v -> c.fairySoulsShowFound = v);
                    b.toggle("  Only Nearby (50 blocks)", () -> c.fairySoulsNearby, v -> c.fairySoulsNearby = v);
                    b.color("  Missing Color", () -> c.colorFairySoulMissing, v -> c.colorFairySoulMissing = v);
                    b.color("  Found Color", () -> c.colorFairySoulFound, v -> c.colorFairySoulFound = v);
                });
        RepoCache.onReload("FairySouls", () -> { requested = false; });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("fairysouls")
                        .executes(c -> status(c.getSource()))
                        .then(ClientCommands.literal("found").executes(c -> markAll(c.getSource(), true)))
                        .then(ClientCommands.literal("missing").executes(c -> markAll(c.getSource(), false))))));
    }

    private static void load() {
        requested = true;
        Thread t = new Thread(() -> {
            try {
                JsonObject root = RepoCache.fetch("fairy_souls", URL, 20).getAsJsonObject();
                Map<String, List<Soul>> out = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    if (!e.getValue().isJsonArray()) continue;
                    List<Soul> list = new ArrayList<>();
                    for (JsonElement p : e.getValue().getAsJsonArray()) {
                        String[] xyz = p.getAsString().split(",");
                        if (xyz.length != 3) continue;
                        BlockPos pos = new BlockPos(Integer.parseInt(xyz[0].trim()), Integer.parseInt(xyz[1].trim()), Integer.parseInt(xyz[2].trim()));
                        list.add(new Soul(pos, pos.getX() + "," + pos.getY() + "," + pos.getZ(), new AABB(pos)));
                    }
                    out.put(e.getKey(), List.copyOf(list));
                }
                maxSouls = root.has("Max Souls") ? root.get("Max Souls").getAsInt() : 0;
                souls = out;
                TeslaMaps.debug("[FairySouls] loaded {} islands", out.size());
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[FairySouls] load failed: {}", e.toString());
            }
        }, "TeslaMaps-FairySouls");
        t.setDaemon(true);
        t.start();
    }

    private static String currentLocation() {
        return SkyblockUtils.isInSkyblock() ? AREAS.get(SkyblockUtils.getArea()) : null;
    }

    private static Set<String> found(String loc) {
        return QolStore.get().fairyFound.computeIfAbsent(loc, k -> new HashSet<>());
    }

    private static void tick() {
        if (!requested) load();
        if (++ticks % 10 != 0) return;
        location = currentLocation();
        List<Soul> all = location == null ? null : souls.get(location);
        Minecraft mc = Minecraft.getInstance();
        if (all == null || mc.player == null) {
            visible = List.of();
            return;
        }
        TeslaMapsConfig c = cfg();
        Set<String> f = found(location);
        Vec3 p = mc.player.position();
        List<Soul> vis = new ArrayList<>();
        List<Boolean> vf = new ArrayList<>();
        for (Soul s : all) {
            boolean isFound = f.contains(s.key);
            if (isFound && !c.fairySoulsShowFound) continue;
            if (c.fairySoulsNearby && s.pos.distToCenterSqr(p) > 2500) continue;
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
        int missing = TeslaMapsConfig.parseColor(c.colorFairySoulMissing);
        int found = TeslaMapsConfig.parseColor(c.colorFairySoulFound);
        ESPRenderer.beginBoxBatch(pose, cam, false, true);
        for (int i = 0; i < vis.size(); i++) ESPRenderer.batchBox(vis.get(i).box, vf.get(i) ? found : missing, 2f);
        ESPRenderer.endBoxBatch();
    }

    private static void onChat(String raw) {
        if (raw.indexOf("Fairy Soul") < 0) return;
        String t = StringUtil.stripTrim(raw);
        if (!t.equals("SOUL! You found a Fairy Soul!") && !t.equals("You have already found that Fairy Soul!")) return;
        Minecraft mc = Minecraft.getInstance();
        String loc = currentLocation();
        List<Soul> all = loc == null ? null : souls.get(loc);
        if (all == null || mc.player == null) return;
        Set<String> f = found(loc);
        Vec3 p = mc.player.position();
        Soul best = null;
        double bestD = 16;
        for (Soul s : all) {
            if (f.contains(s.key)) continue;
            double d = s.pos.distToCenterSqr(p);
            if (d <= bestD) { bestD = d; best = s; }
        }
        if (best == null) return;
        f.add(best.key);
        QolStore.save();
        ticks = 9;
    }

    private static int status(FabricClientCommandSource src) {
        String loc = currentLocation();
        List<Soul> all = loc == null ? null : souls.get(loc);
        int total = 0;
        for (Set<String> s : QolStore.get().fairyFound.values()) total += s.size();
        String island = all == null ? "§7no fairy souls on this island"
                : "§d" + found(loc).size() + "§7/§d" + all.size() + " §7on this island";
        src.sendFeedback(Component.literal("§b[TeslaMaps] §7Fairy souls: " + island + "§7, §d" + total
                + "§7/§d" + maxSouls + " §7tracked in total"));
        return 1;
    }

    private static int markAll(FabricClientCommandSource src, boolean asFound) {
        String loc = currentLocation();
        List<Soul> all = loc == null ? null : souls.get(loc);
        if (all == null) {
            src.sendFeedback(Component.literal("§b[TeslaMaps] §cNo fairy souls known for this island."));
            return 0;
        }
        Set<String> f = found(loc);
        f.clear();
        if (asFound) for (Soul s : all) f.add(s.key);
        QolStore.save();
        ticks = 9;
        src.sendFeedback(Component.literal("§b[TeslaMaps] §7Marked all " + all.size() + " fairy souls here as "
                + (asFound ? "§afound" : "§cmissing") + "§7."));
        return 1;
    }
}
