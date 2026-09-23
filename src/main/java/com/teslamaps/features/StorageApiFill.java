package com.teslamaps.features;

import com.google.gson.JsonObject;
import com.teslamaps.TeslaMaps;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.profileviewer.data.InventoryDecoder;
import com.teslamaps.profileviewer.screen.ItemGrid;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SbProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Fills an empty storage cache from the SkyBlock API once per profile and session, like
 * NoammAddons does. Local snapshots always win: only keys the cache does not have are written.
 */
public final class StorageApiFill {

    private static final Set<String> requested = new HashSet<>();
    private static JsonObject pending;
    private static int tries, ticks;

    private StorageApiFill() {}

    public static void register() {
        Feature.of("StorageApiFill").section("Inventory")
                .when(() -> TeslaMapsConfig.get().storageApiFill)
                .tick(StorageApiFill::tick);
    }

    private static boolean cacheEmpty() {
        for (int i = 1; i <= 9; i++) if (StorageCache.has("epage_" + i)) return false;
        for (int i = 1; i <= 18; i++) if (StorageCache.has("backpack_" + i)) return false;
        return true;
    }

    private static void tick() {
        if (pending != null) {
            if (++ticks % 40 == 0) apply();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || !com.teslamaps.utils.StringUtil.stripTrim(mc.screen.getTitle().getString()).equals("Storage")) return;
        String key = SbProfile.key();
        String name = SbProfile.name();
        if (key == null || !requested.add(key) || !cacheEmpty()) return;
        String uuid = mc.getUser().getProfileId().toString().replace("-", "");
        HypixelApi.getProfiles(uuid).thenAccept(resp -> mc.execute(() -> {
            JsonObject inv = inventory(resp, name);
            if (inv == null || !key.equals(SbProfile.key())) return;
            pending = inv;
            tries = 0;
            ticks = 0;
        })).exceptionally(e -> null);
    }

    private static JsonObject inventory(JsonObject resp, String name) {
        if (resp == null || !resp.has("profiles") || !resp.get("profiles").isJsonObject()) return null;
        JsonObject pick = null;
        for (var e : resp.getAsJsonObject("profiles").entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject p = e.getValue().getAsJsonObject();
            String cute = p.has("cute_name") ? p.get("cute_name").getAsString().toLowerCase() : "";
            if (cute.equals(name)) { pick = p; break; }
        }
        if (pick == null || !pick.has("data") || !pick.get("data").isJsonObject()) return null;
        JsonObject data = pick.getAsJsonObject("data");
        return data.has("inventory") && data.get("inventory").isJsonObject() ? data.getAsJsonObject("inventory") : null;
    }

    private static void apply() {
        if (Minecraft.getInstance().level == null) return;
        // item bases resolve async; give them a few rounds before storing placeholders
        boolean last = ++tries >= 8;
        LinkedHashMap<String, LinkedHashMap<Integer, String>> pages = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> sizes = new LinkedHashMap<>();
        LinkedHashMap<String, String> icons = new LinkedHashMap<>();
        boolean missing = false;

        List<InventoryDecoder.Slot> ender = InventoryDecoder.decode(pending, "ender_chest_contents");
        for (int p = 0; p * 45 < ender.size() && p < 9; p++) {
            LinkedHashMap<Integer, String> slots = new LinkedHashMap<>();
            for (int i = 0; i < 45 && p * 45 + i < ender.size(); i++) {
                String snbt = encode(ender.get(p * 45 + i));
                if (snbt == null) continue;
                if (snbt.isEmpty()) { missing = true; continue; }
                slots.put(i, snbt);
            }
            pages.put("epage_" + (p + 1), slots);
            sizes.put("epage_" + (p + 1), 45);
        }

        if (pending.has("backpack_contents") && pending.get("backpack_contents").isJsonObject()) {
            JsonObject contents = pending.getAsJsonObject("backpack_contents");
            JsonObject iconJson = pending.has("backpack_icons") && pending.get("backpack_icons").isJsonObject()
                    ? pending.getAsJsonObject("backpack_icons") : new JsonObject();
            for (String k : contents.keySet()) {
                int idx;
                try { idx = Integer.parseInt(k); } catch (NumberFormatException e) { continue; }
                if (idx < 0 || idx >= 18) continue;
                List<InventoryDecoder.Slot> pack = InventoryDecoder.decode(contents, k);
                if (pack.isEmpty()) continue;
                LinkedHashMap<Integer, String> slots = new LinkedHashMap<>();
                for (int i = 0; i < pack.size(); i++) {
                    String snbt = encode(pack.get(i));
                    if (snbt == null) continue;
                    if (snbt.isEmpty()) { missing = true; continue; }
                    slots.put(i, snbt);
                }
                String key = "backpack_" + (idx + 1);
                pages.put(key, slots);
                sizes.put(key, pack.size());
                for (InventoryDecoder.Slot s : InventoryDecoder.decode(iconJson, k)) {
                    if (s.isEmpty()) continue;
                    String icon = encode(s);
                    if (icon != null && !icon.isEmpty()) icons.put(key, icon);
                    break;
                }
            }
        }

        if (missing && !last) return;
        int n = 0;
        for (var e : pages.entrySet()) {
            if (StorageCache.has(e.getKey())) continue;
            StorageCache.put(e.getKey(), sizes.get(e.getKey()), e.getValue());
            String icon = icons.get(e.getKey());
            if (icon != null) StorageCache.putIcon(e.getKey(), icon);
            n++;
        }
        StorageCache.save();
        pending = null;
        TeslaMaps.LOGGER.info("Storage prefilled {} pages from the API", n);
    }

    /** null = empty slot, "" = item base not resolved yet. */
    private static String encode(InventoryDecoder.Slot slot) {
        if (slot == null || slot.isEmpty()) return null;
        ItemStack st = ItemGrid.stackFor(slot);
        if (slot.skyblockId != null && st.is(Items.BARRIER) && tries < 8) return "";
        String s = StorageCache.encode(st);
        return s == null ? null : s;
    }
}
