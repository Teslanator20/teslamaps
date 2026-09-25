package com.teslamaps.features.customize;

// Skyblocker's SkyblockItemModels#updateItemModels
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class ItemModelMigration {

    private static final String URL =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/resource_pack.json";
    private static final String NAMESPACE = "hypixel_skyblock";

    private static boolean started;

    private ItemModelMigration() {}

    public static void register() {
        Feature.of("ItemModelMigration").when(() -> TeslaMapsConfig.get().migrateItemModels
                        && !TeslaMapsConfig.get().customItemModel.isEmpty())
                .tickAlways(ItemModelMigration::tick);
        com.teslamaps.utils.RepoCache.onReload("ItemModelMigration", () -> started = false);
    }

    private static void tick() {
        if (started) return;
        started = true;
        CompletableFuture.runAsync(() -> {
            try {
                JsonElement e = com.teslamaps.utils.RepoCache.fetch("neu-resource_pack", URL, 30);
                JsonObject models = e.getAsJsonObject().getAsJsonObject("item_models");
                Set<String> removed = new HashSet<>();
                Map<String, String> renamed = new HashMap<>();
                if (models.has("removed")) for (JsonElement r : models.getAsJsonArray("removed")) removed.add(r.getAsString());
                if (models.has("renamed"))
                    for (var r : models.getAsJsonObject("renamed").entrySet()) renamed.put(r.getKey(), r.getValue().getAsString());
                Minecraft.getInstance().execute(() -> apply(removed, renamed));
            } catch (Exception ex) {
                TeslaMaps.LOGGER.warn("[ItemModelMigration] failed to load resource_pack.json: {}", ex.toString());
            }
        });
    }

    private static void apply(Set<String> removed, Map<String, String> renamed) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        int changed = 0;
        Iterator<Map.Entry<String, Identifier>> it = c.customItemModel.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Identifier> entry = it.next();
            Identifier model = entry.getValue();
            if (model == null || !model.getNamespace().equals(NAMESPACE)) continue;
            String cur = model.toString();
            if (removed.contains(cur)) {
                it.remove();
                changed++;
                continue;
            }
            Set<String> visited = new HashSet<>();
            visited.add(cur);
            String next = renamed.get(cur);
            while (next != null && visited.add(next)) {
                cur = next;
                next = renamed.get(cur);
            }
            if (!cur.equals(model.toString())) {
                Identifier id = Identifier.tryParse(cur);
                if (id != null) {
                    entry.setValue(id);
                    changed++;
                }
            }
        }
        if (changed > 0) {
            TeslaMaps.LOGGER.info("[ItemModelMigration] updated {} item model override(s)", changed);
            Customize.refresh();
            TeslaMapsConfig.save();
        }
    }
}
