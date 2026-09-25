package com.teslamaps.features;

// Ported from SkyHanni's HighlightVisitorsOutsideOfGarden.kt, visitor list from SkyHanni-REPO constants/Garden.json
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.properties.Property;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.HypixelModApi;
import com.teslamaps.utils.RepoCache;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Garden visitor NPCs standing on other islands: dark red glow, clicks optionally blocked. */
public final class VisitorsOutsideGarden {

    public static final String[] BLOCK_MODES = {"Don't", "Always", "Only On Bingo"};
    private static final String URL = "https://raw.githubusercontent.com/hannibal002/SkyHanni-REPO/main/constants/Garden.json";

    private record Visitor(BlockPos pos, String skinOrType) {}

    private static Feature feature;
    private static volatile Map<String, List<Visitor>> byMode = Map.of();
    private static boolean loading;
    private static long lastTry;
    private static List<Entity> found = List.of();
    private static boolean bingo;
    private static int ticks;
    private static long lastMessage;

    private VisitorsOutsideGarden() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("VisitorsOutsideGarden").section("Garden")
                .when(() -> cfg().visitorsOutsideHighlight || !"Don't".equals(cfg().visitorsOutsideBlock))
                .tick(VisitorsOutsideGarden::tick)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Visitors Elsewhere");
                    b.toggle("Highlight Visitors Outside Garden", () -> c.visitorsOutsideHighlight, v -> c.visitorsOutsideHighlight = v)
                            .desc("Garden visitor NPCs on other islands (Hub, Park, Mines ...) glow dark red.")
                            .tags("visitor", "npc", "bingo");
                    b.dropdown("  Block Clicking Them", BLOCK_MODES, () -> c.visitorsOutsideBlock, v -> c.visitorsOutsideBlock = v)
                            .desc("Stops you from talking to a visitor by accident. Sneak to click anyway.");
                });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                level.isClientSide() && block(entity) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                level.isClientSide() && block(entity) ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    private static void load() {
        long now = System.currentTimeMillis();
        if (loading || !byMode.isEmpty() || now - lastTry < 120_000L) return;
        lastTry = now;
        loading = true;
        CompletableFuture.runAsync(() -> {
            try {
                JsonElement json = RepoCache.fetch("SkyHanniGarden", URL, 20);
                JsonObject visitors = json.getAsJsonObject().getAsJsonObject("visitors");
                Map<String, List<Visitor>> out = new HashMap<>();
                for (var e : visitors.entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    if (!o.has("mode") || o.get("mode").isJsonNull()) continue;
                    String skin = o.has("skinOrType") ? o.get("skinOrType").getAsString()
                            : o.has("skin_or_type") ? o.get("skin_or_type").getAsString() : null;
                    if (skin == null) continue;
                    skin = skin.replace("\\n", "").replace("\n", "");
                    BlockPos pos = null;
                    if (o.has("position") && !o.get("position").isJsonNull()) {
                        String[] p = o.get("position").getAsString().split(":");
                        if (p.length == 3) pos = new BlockPos((int) Double.parseDouble(p[0]),
                                (int) Double.parseDouble(p[1]), (int) Double.parseDouble(p[2]));
                    }
                    out.computeIfAbsent(o.get("mode").getAsString(), k -> new ArrayList<>()).add(new Visitor(pos, skin));
                }
                byMode = out;
            } catch (Exception e) {
                com.teslamaps.TeslaMaps.LOGGER.warn("[VisitorsOutsideGarden] Garden.json failed: {}", e.toString());
            } finally {
                loading = false;
            }
        });
    }

    private static void tick() {
        load();
        if (ticks++ % 20 == 0) {
            bingo = detectBingo();
            List<Entity> out = new ArrayList<>();
            if (!SkyblockUtils.isInGarden() && candidates() != null) {
                for (Entity e : EntityCache.all()) {
                    if (e instanceof LivingEntity && !(e instanceof ArmorStand) && isVisitor(e)) out.add(e);
                }
            }
            found = out;
        }
        if (!cfg().visitorsOutsideHighlight) return;
        for (Entity e : found) if (e.isAlive()) EntityGlow.mark(e, 0xAA0000);
    }

    private static boolean detectBingo() {
        for (String l : TabListUtils.getTabListLines()) {
            String s = StringUtil.strip(l);
            if (s.contains("Profile:") && s.contains("Ⓑ")) return true;
        }
        for (String l : ScoreboardUtils.getScoreboardLines()) if (l.contains("Ⓑ")) return true;
        return false;
    }

    private static List<Visitor> candidates() {
        String mode = HypixelModApi.mode();
        return mode == null ? null : byMode.get(mode);
    }

    private static String skinOrType(Entity e) {
        if (e instanceof Player p) {
            Collection<Property> props = p.getGameProfile().properties().get("textures");
            if (props.isEmpty()) return "no skin";
            return props.iterator().next().value().replace("\n", "");
        }
        return "Entity" + e.getClass().getSimpleName();
    }

    private static boolean isVisitor(Entity e) {
        List<Visitor> list = candidates();
        if (list == null) return false;
        String skin = null;
        BlockPos bp = e.blockPosition();
        for (Visitor v : list) {
            if (v.pos != null && v.pos.distSqr(bp) >= 1) continue;
            if (skin == null) skin = skinOrType(e);
            if (v.skinOrType.equals(skin)) return true;
        }
        return false;
    }

    private static boolean block(Entity entity) {
        if (feature == null || !feature.active()) return false;
        String mode = cfg().visitorsOutsideBlock;
        if ("Don't".equals(mode) || ("Only On Bingo".equals(mode) && !bingo)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isShiftKeyDown() || SkyblockUtils.isInGarden()) return false;
        boolean hit = isVisitor(entity);
        if (!hit && entity instanceof ArmorStand) {
            for (Entity e : found) {
                if (e.distanceToSqr(entity) < 4) { hit = true; break; }
            }
        }
        if (!hit) return false;
        long now = System.currentTimeMillis();
        if (now - lastMessage > 2000) {
            lastMessage = now;
            mc.player.sendSystemMessage(Component.literal(
                    "§b[TeslaMaps] §eBlocked you from interacting with a visitor. Sneak to bypass (Garden > Visitors Elsewhere)."));
        }
        return true;
    }
}
