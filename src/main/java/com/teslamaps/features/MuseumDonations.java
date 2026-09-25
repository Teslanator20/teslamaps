package com.teslamaps.features;

// Ported from Skyblocker's MuseumTooltip.java + MuseumItemCache.java
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.profileviewer.data.MuseumRepo;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Museum donation state as a tooltip line, plus an optional marker on undonated items in menus. */
public final class MuseumDonations {

    private static final long REFRESH_MS = 10 * 60_000L;

    private static Feature feature;
    private static volatile Set<String> donated;
    private static volatile boolean apiOff;
    private static volatile boolean fetching;
    private static String fetchedKey;
    private static long nextFetch;
    private static boolean museumOpen;
    private static int ticks;

    private static MuseumRepo.Data builtFrom;
    private static Map<String, String> category = Map.of();
    private static Map<String, String> pieceToSet = Map.of();
    private static Set<String> special = Set.of();

    private MuseumDonations() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("MuseumDonations")
                .when(() -> cfg().museumTooltip || cfg().museumSlotMarker || cfg().salvageMuseumGuard)
                .tick(MuseumDonations::tick)
                .config("Tooltips & Value", b -> {
                    b.label("Museum");
                    b.toggle("Museum Donation Tooltip", () -> cfg().museumTooltip, v -> cfg().museumTooltip = v)
                            .desc("Tooltip line: Museum (category): Donated / Not Donated, from the museum API");
                    b.toggle("  Mark Undonated Items In Menus", () -> cfg().museumSlotMarker, v -> cfg().museumSlotMarker = v);
                    b.color("  Marker Color", () -> cfg().colorMuseumMissing, v -> cfg().colorMuseumMissing = v);
                    b.info("Refreshes on profile change, every 10 min and after leaving the museum");
                });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (cfg().museumTooltip && feature.active()) onTooltip(stack, lines);
        });
    }

    private static void tick() {
        if (++ticks % 20 != 0 || !SkyblockUtils.isInSkyblock()) return;
        Minecraft mc = Minecraft.getInstance();
        boolean open = mc.screen != null && StringUtil.strip(mc.screen.getTitle().getString()).contains("Museum");
        long now = System.currentTimeMillis();
        if (museumOpen && !open) nextFetch = Math.min(nextFetch, now + 3000);
        museumOpen = open;
        String key = SbProfile.key();
        if (key == null || fetching || mc.player == null) return;
        if (key.equals(fetchedKey) && now < nextFetch) return;
        fetchedKey = key;
        nextFetch = now + REFRESH_MS;
        fetch(mc.player.getUUID().toString().replace("-", ""), SbProfile.name());
    }

    private static void fetch(String uuid, String profileName) {
        fetching = true;
        HypixelApi.invalidateProfiles(uuid);
        HypixelApi.getProfiles(uuid).thenCompose(json -> {
            String id = profileId(json, profileName);
            if (id == null) return java.util.concurrent.CompletableFuture.completedFuture(null);
            return HypixelApi.getMuseum(id);
        }).thenAccept(museum -> {
            if (museum != null) read(museum, uuid);
        }).whenComplete((v, e) -> fetching = false);
    }

    private static String profileId(JsonObject json, String name) {
        if (json == null || !json.has("profiles") || !json.get("profiles").isJsonObject()) return null;
        JsonObject profiles = json.getAsJsonObject("profiles");
        String fallback = null;
        for (String cute : profiles.keySet()) {
            JsonObject p = profiles.getAsJsonObject(cute);
            if (!p.has("profile_id")) continue;
            if (name != null && cute.equalsIgnoreCase(name)) return p.get("profile_id").getAsString();
            if (p.has("current") && p.get("current").getAsBoolean()) fallback = p.get("profile_id").getAsString();
        }
        return fallback;
    }

    private static void read(JsonObject museum, String uuid) {
        if (!museum.has("members") || !museum.get("members").isJsonObject()) return;
        JsonObject members = museum.getAsJsonObject("members");
        JsonObject member = null;
        for (String k : members.keySet()) {
            if (k.replace("-", "").equalsIgnoreCase(uuid)) member = members.getAsJsonObject(k);
        }
        if (member == null || !member.has("items") || !member.get("items").isJsonObject()) {
            apiOff = true;
            return;
        }
        MuseumRepo.Data data = MuseumRepo.get();
        Set<String> keys = new HashSet<>(member.getAsJsonObject("items").keySet());
        Set<String> out = data == null ? keys : MuseumRepo.covered(data, keys);
        if (data != null) {
            for (String set : List.copyOf(out)) {
                List<String> pieces = data.setsToItems().get(set);
                if (pieces != null) out.addAll(pieces);
            }
        }
        apiOff = false;
        donated = out;
    }

    private static void ensureTables() {
        MuseumRepo.Data data = MuseumRepo.get();
        if (data == null || data == builtFrom) return;
        Map<String, String> cat = new HashMap<>();
        for (Map.Entry<String, List<String>> e : data.items().entrySet()) {
            String name = StringUtil.titleCase(e.getKey());
            for (String id : e.getValue()) cat.put(id, name);
        }
        Map<String, String> pieces = new HashMap<>();
        for (Map.Entry<String, List<String>> e : data.setsToItems().entrySet()) {
            for (String piece : e.getValue()) pieces.put(piece, e.getKey());
        }
        category = cat;
        pieceToSet = pieces;
        special = new HashSet<>(data.special());
        builtFrom = data;
    }

    /** Museum key of a donatable item: its own id or its armor set, null otherwise. */
    private static String museumKey(String id) {
        if (category.containsKey(id)) return id;
        String set = pieceToSet.get(id);
        return set != null && category.containsKey(set) ? set : null;
    }

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        if (!SkyblockUtils.isInSkyblock()) return;
        String id = ItemUtil.skyblockId(stack);
        if (id.isEmpty()) return;
        ensureTables();
        String key = museumKey(id);
        if (key == null) {
            if (special.contains(id)) lines.add(Component.literal("§dMuseum: (Special)"));
            return;
        }
        String cat = category.get(key);
        Set<String> d = donated;
        if (d == null) {
            lines.add(Component.literal("§dMuseum (" + cat + "): §7" + (apiOff ? "API off" : "unknown")));
        } else if (d.contains(id) || d.contains(key)) {
            lines.add(Component.literal("§dMuseum (" + cat + "): §a§l✔§a Donated"));
        } else {
            lines.add(Component.literal("§dMuseum (" + cat + "): §c§l✖§c Not Donated"));
        }
    }

    /** Donated ids incl. set pieces, null while unknown. */
    public static Set<String> donatedIds() {
        return donated;
    }

    /** Donatable, not donated yet, and the museum data is loaded. */
    public static boolean notDonated(ItemStack stack) {
        Set<String> d = donated;
        if (d == null || stack == null || stack.isEmpty()) return false;
        String id = ItemUtil.skyblockId(stack);
        if (id.isEmpty()) return false;
        ensureTables();
        String key = museumKey(id);
        return key != null && !d.contains(id) && !d.contains(key);
    }

    public static void drawSlot(GuiGraphicsExtractor ctx, int x, int y, ItemStack stack) {
        TeslaMapsConfig c = cfg();
        if (!c.museumSlotMarker || stack == null || stack.isEmpty()) return;
        Set<String> d = donated;
        if (d == null || !SkyblockUtils.isInSkyblock()) return;
        String id = ItemUtil.skyblockId(stack);
        if (id.isEmpty()) return;
        ensureTables();
        String key = museumKey(id);
        if (key == null || d.contains(id) || d.contains(key)) return;
        int color = TeslaMapsConfig.parseColor(c.colorMuseumMissing);
        ctx.fill(x, y, x + 16, y + 1, color);
        ctx.fill(x, y + 15, x + 16, y + 16, color);
        ctx.fill(x, y + 1, x + 1, y + 15, color);
        ctx.fill(x + 15, y + 1, x + 16, y + 15, color);
    }
}
