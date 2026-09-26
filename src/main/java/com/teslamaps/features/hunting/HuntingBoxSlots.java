package com.teslamaps.features.hunting;

// Ported from GanKura's HuntingBoxCountMixin.java and Skyblocker's HuntingBoxPriceTooltip.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.EstimatedValue;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Hunting Box: owned amount in the slot corner, bazaar sell value in the tooltip. */
public final class HuntingBoxSlots {

    private static final int COLOR = 0xFFFFFF55;

    private static Map<Integer, String> counts = Map.of();
    private static Object lastEntries;
    private static Feature feature;

    private HuntingBoxSlots() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("HuntingBoxSlots").section("Shards")
                .when(() -> cfg().huntingBoxSlotCount || cfg().huntingBoxPriceTooltip)
                .tick(HuntingBoxSlots::tick)
                .config("Shards", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Hunting Box Slots");
                    b.toggle("Owned Amount In Slot", () -> c.huntingBoxSlotCount, v -> c.huntingBoxSlotCount = v)
                            .desc("Hunting Box: how many of each shard you hold, in the slot corner (10k+ shortened)")
                            .tags("hunting box", "shard", "count", "owned");
                    b.toggle("Sell Price In Tooltip", () -> c.huntingBoxPriceTooltip, v -> c.huntingBoxPriceTooltip = v)
                            .desc("Hunting Box tooltip: bazaar instant-sell value of the stack, hold Shift for the price per shard")
                            .tags("hunting box", "shard", "price", "tooltip");
                });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature == null || !cfg().huntingBoxPriceTooltip || !HuntingBox.inBoxScreen() || !feature.active()) return;
            com.teslamaps.debug.Perf.begin("HuntingBoxSlots");
            try {
                addPrice(lines);
            } finally {
                com.teslamaps.debug.Perf.end();
            }
        });
    }

    private static void tick() {
        if (!HuntingBox.inBoxScreen()) {
            counts = Map.of();
            lastEntries = null;
            return;
        }
        var entries = HuntingBox.boxEntries();
        if (entries == lastEntries) return;
        lastEntries = entries;
        PriceManager.ensureFresh();
        Map<Integer, String> out = new HashMap<>();
        for (HuntingBox.BoxEntry e : entries) {
            if (e.amount() <= 0) continue;
            out.put(e.slot(), e.amount() < 10000 ? String.valueOf(e.amount()) : e.amount() / 1000 + "k");
        }
        counts = out;
    }

    /** From LoreSlotTextMixin. */
    public static void drawSlot(GuiGraphicsExtractor ctx, Slot slot) {
        if (counts.isEmpty() || !cfg().huntingBoxSlotCount || slot.container instanceof Inventory) return;
        String text = counts.get(slot.index);
        if (text == null || !HuntingBox.inBoxScreen()) return;
        var font = Minecraft.getInstance().font;
        ctx.text(font, text, slot.x + 17 - font.width(text), slot.y + 9, COLOR, true);
    }

    private static void addPrice(java.util.List<Component> lines) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        Slot hovered = ((com.teslamaps.mixin.HandledScreenAccessor) cs).getFocusedSlot();
        if (hovered == null || hovered.container instanceof Inventory) return;
        for (HuntingBox.BoxEntry e : HuntingBox.boxEntries()) {
            if (e.slot() != hovered.index) continue;
            int count = Math.max(1, e.amount());
            Double sell = PriceManager.bazaarSellOrNull(e.shard().bazaarId());
            String label = count > 1 ? "Shards" : "Shard";
            if (sell == null) {
                lines.add(Component.literal("§6" + label + " Sell Price: §cNo data"));
            } else if (mc.hasShiftDown() && count > 1) {
                lines.add(Component.literal("§6" + label + " Sell Price: §e" + EstimatedValue.fmt(sell * count)
                        + " §7(" + EstimatedValue.fmt(sell) + " each)"));
            } else {
                lines.add(Component.literal("§6" + label + " Sell Price: §e" + EstimatedValue.fmt(sell * count)));
            }
            return;
        }
    }
}
