package com.teslamaps.features;

// Ported from SkyHanni's HoppityEggLocator.kt + HoppityEggLocations.kt, curve fit through utils/BezierFitter
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.BezierFitter;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class HoppityEggLocator {

    private static final double MAX_GUESS_DISTANCE = 5.0;
    private static final Pattern EGG_FOUND = Pattern.compile(
            "(?:HOPPITY'S HUNT You found a Chocolate|You have already collected this Chocolate) "
            + "(?:Breakfast|Lunch|Dinner|Brunch|Déjeuner|Supper) Egg");

    private record Spot(String name, BlockPos pos) {}

    private static final Map<String, List<Spot>> SPOTS = new HashMap<>();
    private static boolean loaded;

    private static final BezierFitter fitter = new BezierFitter(3);
    private static Feature feature;
    private static long lastClick;
    private static boolean warnPending;
    private static BlockPos guess;
    private static boolean guessRough;
    private static Object lastLevel;
    private static int timer;

    // built in tick, drawn in render
    private static volatile List<Spot> drawSpots = List.of();
    private static volatile List<BlockPos> drawDupes = List.of();
    private static volatile boolean guessDupe;

    private HoppityEggLocator() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("HoppityEggLocator").section("Events")
                .when(() -> cfg().hoppityEggGuess || cfg().hoppityEggSpots)
                .tick(HoppityEggLocator::tick)
                .world(HoppityEggLocator::render)
                .serverChat(HoppityEggLocator::onChat)
                .config("Events", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Hoppity Egglocator Guess", () -> c.hoppityEggGuess, v -> c.hoppityEggGuess = v)
                            .desc("Use the Egglocator: its particle trail is fitted to a curve and snapped to the nearest known egg spot of the island. Warns when the trail was unusable")
                            .tags("hoppity", "egg", "egglocator", "guess", "waypoint");
                    b.toggle("  Line To Crosshair", () -> c.hoppityEggGuessLine, v -> c.hoppityEggGuessLine = v);
                    b.toggle("  Mark Found Spots Nearby", () -> c.hoppityEggGuessDupes, v -> c.hoppityEggGuessDupes = v)
                            .desc("Egg spots you already found, closer than 10 blocks, are marked red as \"Duplicate Location!\"");
                    b.toggle("All Hoppity Egg Spots", () -> c.hoppityEggSpots, v -> c.hoppityEggSpots = v)
                            .desc("During Hoppity's Hunt: every known egg spot of the island as a waypoint, while the Egglocator is not in your hotbar. Spots you already found are red")
                            .tags("hoppity", "egg", "locations", "spots", "waypoint");
                    b.toggle("  Hide Found Spots", () -> c.hoppityEggSpotsHideFound, v -> c.hoppityEggSpotsHideFound = v);
                    b.info("Found spots are saved per island. /tmap hoppityeggs import loads them from your profile API");
                });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide() && feature.active() && cfg().hoppityEggGuess
                    && "EGGLOCATOR".equals(ItemUtil.skyblockId(player.getItemInHand(hand)))) onUse();
            return InteractionResult.PASS;
        });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("hoppityeggs")
                        .then(ClientCommands.literal("import").executes(c -> { importFromApi(); return 1; }))
                        .then(ClientCommands.literal("clear").executes(c -> {
                            cfg().hoppityUniqueEggs.clear();
                            TeslaMapsConfig.save();
                            msg("§eCleared all found Hoppity egg spots.");
                            return 1;
                        })))));
    }

    private static void load() {
        loaded = true;
        try (var in = HoppityEggLocator.class.getResourceAsStream("/assets/teslamaps/data/hoppity_eggs.json")) {
            if (in == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> island : root.entrySet()) {
                List<Spot> list = new ArrayList<>();
                for (Map.Entry<String, JsonElement> e : island.getValue().getAsJsonObject().entrySet()) {
                    BlockPos p = parse(e.getValue().getAsString(), ":");
                    if (p != null) list.add(new Spot(e.getKey(), p));
                }
                SPOTS.put(island.getKey(), list);
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[Hoppity] could not load egg spots", e);
        }
    }

    private static BlockPos parse(String s, String sep) {
        String[] p = s.split(sep);
        if (p.length != 3) return null;
        try {
            return BlockPos.containing(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Spot> islandSpots() {
        if (!loaded) load();
        List<Spot> l = SPOTS.get(SkyblockUtils.getArea());
        return l == null ? List.of() : l;
    }

    private static List<BlockPos> foundSpots(String island) {
        List<String> raw = cfg().hoppityUniqueEggs.get(island);
        if (raw == null || raw.isEmpty()) return List.of();
        List<BlockPos> out = new ArrayList<>(raw.size());
        for (String s : raw) {
            BlockPos p = parse(s, ",");
            if (p != null) out.add(p);
        }
        return out;
    }

    private static boolean isFound(BlockPos pos, List<BlockPos> found) {
        for (BlockPos f : found) if (f.distSqr(pos) < 25) return true;
        return false;
    }

    private static boolean enabled() {
        return SkyblockUtils.isInSkyblock() && !DungeonManager.isInDungeon() && !SkyblockUtils.isInGarden()
                && HoppityUnclaimedEggs.eventActive();
    }

    private static void reset() {
        fitter.reset();
        guess = null;
        guessRough = false;
        warnPending = false;
    }

    private static void onUse() {
        long now = System.currentTimeMillis();
        if (now - lastClick < 5000 || !enabled()) return;
        reset();
        lastClick = now;
        warnPending = true;
    }

    /** From ClientPlayNetworkHandlerMixin. */
    public static void onParticle(ClientboundLevelParticlesPacket p) {
        if (lastClick == 0 || System.currentTimeMillis() - lastClick > 5000) return;
        if (!Minecraft.getInstance().isSameThread() || feature == null || !feature.active() || !cfg().hoppityEggGuess) return;
        if (p.particle().getType() != ParticleTypes.HAPPY_VILLAGER || p.count() != 1 || p.xMaxSpeed() != 0f) return;
        if (!fitter.tryAdd(new Vec3(p.x(), p.y(), p.z()), 3.0)) return;
        Vec3 solved = fitter.solve();
        if (solved == null) return;
        List<Spot> spots = islandSpots();
        if (spots.isEmpty()) {
            guess = BlockPos.containing(solved);
            guessRough = true;
            warnPending = false;
            return;
        }
        Spot best = null;
        double bestD = Double.MAX_VALUE;
        for (Spot s : spots) {
            double d = Vec3.atCenterOf(s.pos).distanceToSqr(solved);
            if (d < bestD) { bestD = d; best = s; }
        }
        if (best == null || Math.sqrt(bestD) > MAX_GUESS_DISTANCE) return;
        guess = best.pos;
        guessRough = false;
        warnPending = false;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            reset();
        }
        long now = System.currentTimeMillis();
        if (warnPending && lastClick > 0 && now - lastClick > 5500) {
            warnPending = false;
            if (!fitter.isEmpty() && !islandSpots().isEmpty()) msg("§cEgglocator result was unreliable, please use it again!");
        }
        if (guess != null && mc.player != null && mc.player.blockPosition().distSqr(guess) < 9) guess = null;
        if (++timer % 10 != 0) return;
        if (!enabled()) {
            drawSpots = List.of();
            drawDupes = List.of();
            guess = null;
            return;
        }
        TeslaMapsConfig c = cfg();
        String island = SkyblockUtils.getArea();
        List<Spot> spots = islandSpots();
        List<BlockPos> found = foundSpots(island);
        boolean allFound = found.size() >= 15;
        guessDupe = guess != null && !allFound && isFound(guess, found);

        List<Spot> show = new ArrayList<>();
        if (c.hoppityEggSpots && guess == null && !locatorInHotbar(mc)) {
            for (Spot s : spots) {
                if (c.hoppityEggSpotsHideFound && isFound(s.pos, found)) continue;
                show.add(s);
            }
        }
        drawSpots = show;

        List<BlockPos> dupes = new ArrayList<>();
        if (c.hoppityEggGuess && c.hoppityEggGuessDupes && !allFound && mc.player != null && show.isEmpty()) {
            for (Spot s : spots) {
                if (mc.player.blockPosition().distSqr(s.pos) < 100 && isFound(s.pos, found)) dupes.add(s.pos);
            }
        }
        drawDupes = dupes;
    }

    private static boolean locatorInHotbar(Minecraft mc) {
        if (mc.player == null) return false;
        for (int i = 0; i < 9; i++) {
            if ("EGGLOCATOR".equals(ItemUtil.skyblockId(mc.player.getInventory().getItem(i)))) return true;
        }
        return false;
    }

    private static void onChat(String raw) {
        String text = StringUtil.strip(raw);
        if (!text.contains("Chocolate") || !EGG_FOUND.matcher(text).find()) return;
        guess = null;
        Minecraft mc = Minecraft.getInstance();
        String island = SkyblockUtils.getArea();
        if (mc.player == null || island.isEmpty()) return;
        BlockPos at = null;
        double best = 100;
        for (Spot s : islandSpots()) {
            double d = mc.player.blockPosition().distSqr(s.pos);
            if (d < best) { best = d; at = s.pos; }
        }
        if (at == null) {
            Vec3 egg = HoppityEggESP.nearestEgg(8);
            at = BlockPos.containing(egg != null ? egg : mc.player.position());
        }
        List<String> spots = cfg().hoppityUniqueEggs.computeIfAbsent(island, k -> new ArrayList<>());
        for (String s : spots) {
            BlockPos p = parse(s, ",");
            if (p != null && p.distSqr(at) < 16) return;
        }
        spots.add(at.getX() + "," + at.getY() + "," + at.getZ());
        TeslaMapsConfig.save();
    }

    private static void render(PoseStack pose, Vec3 cam) {
        TeslaMapsConfig c = cfg();
        BlockPos g = guess;
        if (g != null && c.hoppityEggGuess) {
            int color = guessDupe ? 0xFF5555 : guessRough ? 0xFFFF55 : 0x55FF55;
            String label = guessDupe ? "§aGuess §c(Duplicate Location)" : guessRough ? "§eGuess (rough)" : "§aGuess";
            ESPRenderer.drawFilledBox(pose, new AABB(g), 0x55000000 | color, cam, true);
            WaypointSet.draw(pose, cam, g, color, label, true, true, true);
            if (c.hoppityEggGuessLine) ESPRenderer.drawTracerFromCamera(pose, Vec3.atCenterOf(g), 0xFF000000 | color, cam, true);
        }
        List<Spot> spots = drawSpots;
        if (!spots.isEmpty()) {
            List<BlockPos> found = c.hoppityEggSpotsHideFound ? List.of() : foundCache();
            for (int i = 0, n = spots.size(); i < n; i++) {
                Spot s = spots.get(i);
                boolean dupe = !found.isEmpty() && isFound(s.pos, found);
                if (dupe) {
                    ESPRenderer.drawFilledBox(pose, new AABB(s.pos), 0x66FF5555, cam, false);
                    WaypointSet.drawLabel(pose, cam, "§aEgg §c(Duplicate Location)", Vec3.atCenterOf(s.pos).add(0, 1, 0), 1.2f, true);
                } else {
                    ESPRenderer.drawFilledBox(pose, new AABB(s.pos), 0x6655FF55, cam, true);
                    WaypointSet.drawLabel(pose, cam, "§aEgg", Vec3.atCenterOf(s.pos).add(0, 1, 0), 1.2f, true);
                }
            }
        }
        List<BlockPos> dupes = drawDupes;
        for (int i = 0, n = dupes.size(); i < n; i++) {
            BlockPos p = dupes.get(i);
            ESPRenderer.drawFilledBox(pose, new AABB(p), 0x66FF5555, cam, false);
            WaypointSet.drawLabel(pose, cam, "§cDuplicate Location!", Vec3.atCenterOf(p).add(0, 1, 0), 1.2f, true);
        }
    }

    private static List<BlockPos> foundCacheList = List.of();
    private static int foundCacheTick = -1;

    private static List<BlockPos> foundCache() {
        if (foundCacheTick != timer) {
            foundCacheTick = timer;
            foundCacheList = foundSpots(SkyblockUtils.getArea());
        }
        return foundCacheList;
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§e[TeslaMaps] " + s));
    }

    private static void importFromApi() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String uuid = mc.player.getUUID().toString().replace("-", "");
        String profile = SbProfile.name();
        msg("§7Loading collected egg spots from the profile API...");
        HypixelApi.invalidateProfiles(uuid);
        HypixelApi.getProfiles(uuid).thenAccept(json -> mc.execute(() -> {
            JsonObject member = member(json, profile);
            JsonObject rabbits = member == null ? null : obj(obj(obj(member, "events"), "easter"), "rabbits");
            JsonObject locs = obj(rabbits, "collected_locations");
            if (locs == null) {
                msg("§cNo collected egg locations in the API (API off or no data).");
                return;
            }
            java.util.Set<String> names = new java.util.HashSet<>();
            for (Map.Entry<String, JsonElement> e : locs.entrySet()) {
                if (!e.getValue().isJsonArray()) continue;
                for (JsonElement el : (JsonArray) e.getValue()) if (el.isJsonPrimitive()) names.add(el.getAsString());
            }
            if (!loaded) load();
            int added = 0;
            for (Map.Entry<String, List<Spot>> island : SPOTS.entrySet()) {
                List<String> spots = cfg().hoppityUniqueEggs.computeIfAbsent(island.getKey(), k -> new ArrayList<>());
                for (Spot s : island.getValue()) {
                    if (!names.contains(s.name)) continue;
                    boolean have = false;
                    for (String raw : spots) {
                        BlockPos p = parse(raw, ",");
                        if (p != null && p.distSqr(s.pos) < 16) { have = true; break; }
                    }
                    if (have) continue;
                    spots.add(s.pos.getX() + "," + s.pos.getY() + "," + s.pos.getZ());
                    added++;
                }
            }
            TeslaMapsConfig.save();
            msg("§aImported " + added + " new egg spot" + (added == 1 ? "" : "s") + " §7(" + names.size() + " in the API).");
        }));
    }

    private static JsonObject member(JsonObject json, String profile) {
        JsonObject profiles = obj(json, "profiles");
        if (profiles == null) return null;
        JsonObject fallback = null;
        for (String cute : profiles.keySet()) {
            JsonObject p = obj(profiles, cute);
            if (p == null) continue;
            if (profile != null && cute.equalsIgnoreCase(profile)) return obj(p, "data");
            if (p.has("current") && p.get("current").getAsBoolean()) fallback = obj(p, "data");
        }
        return fallback;
    }

    private static JsonObject obj(JsonObject o, String key) {
        if (o == null || !o.has(key) || !o.get(key).isJsonObject()) return null;
        return o.getAsJsonObject(key);
    }
}
