package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class DuplicateItems {

    private static final String UUID_TAG = "uuid:\"";

    private static Feature feature;
    private static int timer;
    private static long storedAt;
    private static Map<String, Set<String>> stored = Map.of();
    private static Set<Slot> marked = Set.of();
    private static Set<String> dupeUuids = Set.of();

    private DuplicateItems() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("DuplicateItems").section("Inventory").when(() -> cfg().duplicateItems)
                .tick(DuplicateItems::tick)
                .config("Inventory", b -> {
                    b.toggle("Mark Duplicate Items", () -> cfg().duplicateItems, v -> cfg().duplicateItems = v)
                            .desc("Highlights items whose Hypixel item UUID shows up more than once in your inventory (duped items).");
                    b.toggle("  Mark Everywhere", () -> cfg().duplicateItemsEverywhere, v -> cfg().duplicateItemsEverywhere = v)
                            .desc("Also in every GUI, and against the storage / item search snapshots of other containers.");
                    b.color("  Highlight Color", () -> cfg().colorDuplicateItem, v -> cfg().colorDuplicateItem = v);
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature.active() && !dupeUuids.isEmpty() && !lines.isEmpty()) {
                String u = ItemUtil.itemUuid(stack);
                if (u != null && dupeUuids.contains(u)) lines.add(1, Component.literal("§c§lDUPLICATE UUID"));
            }
        });
    }

    private static void tick() {
        if (timer-- > 0) return;
        timer = 10;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            marked = Set.of();
            dupeUuids = Set.of();
            return;
        }
        boolean everywhere = cfg().duplicateItemsEverywhere;
        if (everywhere) refreshStored();

        Map<String, Integer> counts = new HashMap<>();
        Map<Slot, String> uuids = new HashMap<>();
        for (Slot slot : cs.getMenu().slots) {
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            String u = ItemUtil.itemUuid(st);
            if (u == null) continue;
            uuids.put(slot, u);
            counts.merge(u, 1, Integer::sum);
        }
        String ownIndex = ItemIndex.openKey();
        String ownStorage = BackpackPreview.keyFromTitle(StringUtil.stripTrim(cs.getTitle().getString()));

        Set<Slot> out = new HashSet<>();
        Set<String> dupes = new HashSet<>();
        for (Map.Entry<Slot, String> e : uuids.entrySet()) {
            Slot slot = e.getKey();
            boolean player = slot.container instanceof Inventory;
            if (!player && !everywhere) continue;
            String u = e.getValue();
            boolean dupe = counts.get(u) > 1;
            if (!dupe && everywhere) {
                Set<String> keys = stored.get(u);
                if (keys != null) {
                    for (String k : keys) {
                        if (k.equals("i:" + ownIndex) || k.equals("s:" + ownStorage)) continue;
                        dupe = true;
                        break;
                    }
                }
            }
            if (dupe) {
                out.add(slot);
                dupes.add(u);
            }
        }
        marked = out;
        dupeUuids = dupes;
    }

    private static void refreshStored() {
        long now = System.currentTimeMillis();
        if (now - storedAt < 5000) return;
        storedAt = now;
        Map<String, Set<String>> m = new HashMap<>();
        ItemIndex.forEachStored((k, snbt) -> collect(m, "i:" + k, snbt));
        StorageCache.forEachStored((k, snbt) -> collect(m, "s:" + k, snbt));
        stored = m;
    }

    private static void collect(Map<String, Set<String>> m, String key, String snbt) {
        if (snbt == null) return;
        int i = snbt.indexOf(UUID_TAG);
        if (i < 0) return;
        int start = i + UUID_TAG.length();
        int end = snbt.indexOf('"', start);
        if (end <= start) return;
        m.computeIfAbsent(snbt.substring(start, end), x -> new HashSet<>(2)).add(key);
    }

    /** Behind the item, from SlotBackgrounds. */
    public static void drawHighlight(GuiGraphicsExtractor ctx, Slot slot) {
        if (marked.isEmpty() || !marked.contains(slot) || !feature.active()) return;
        ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, TeslaMapsConfig.parseColor(cfg().colorDuplicateItem));
    }
}
