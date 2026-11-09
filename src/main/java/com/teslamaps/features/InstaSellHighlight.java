package com.teslamaps.features;

// After SkyblockExtras' "Highlight Insta-Sell Items"
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.Map;

/** Tints inventory items the bazaar buys instantly, with their instant-sell value in the tooltip. */
public final class InstaSellHighlight {

    private static final Map<ItemStack, Double> cache = new IdentityHashMap<>();
    private static long cacheMarket = -1;
    private static Feature feature;

    private InstaSellHighlight() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("InstaSellHighlight").section("Inventory")
                .when(() -> cfg().instaSellHighlight)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Insta-Sell Items");
                    b.toggle("Highlight Insta-Sell Items", () -> c.instaSellHighlight, v -> c.instaSellHighlight = v)
                            .desc("Tints items in your inventory that the bazaar buys right away (Sell Inventory Now), tooltip shows the instant-sell value. Sack menus sorted by value: Tooltips & Value > Sack Table.")
                            .tags("bazaar", "sell inventory", "instasell", "sack");
                    b.color("  Highlight Color", () -> c.colorInstaSell, v -> c.colorInstaSell = v);
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature == null || !feature.active() || Minecraft.getInstance().gui.screen() == null) return;
            Double v = value(stack);
            if (v == null) return;
            lines.add(Component.literal("§aInsta-sell: §6" + EstimatedValue.fmt(v * stack.getCount())
                    + " coins §8(Sell Inventory Now)"));
        });
    }

    private static Double value(ItemStack st) {
        if (st.isEmpty() || !SkyblockUtils.inSkyblockCached()) return null;
        long market = PriceManager.bazaarUpdatedAt();
        if (market != cacheMarket || cache.size() > 512) {
            cache.clear();
            cacheMarket = market;
        }
        if (cache.containsKey(st)) return cache.get(st);
        String id = ItemUtil.skyblockId(st);
        Double v = id.isEmpty() ? null : PriceManager.bazaarSellOrNull(id);
        if (v != null && v <= 0) v = null;
        cache.put(st, v);
        return v;
    }

    /** From SlotBackgrounds, behind the icon. Player inventory slots only. */
    public static void drawSlot(GuiGraphicsExtractor g, Slot slot) {
        if (feature == null || !feature.active()) return;
        if (!(slot.container instanceof Inventory) || slot.getContainerSlot() >= 36) return;
        if (value(slot.getItem()) == null) return;
        int rgb = TeslaMapsConfig.parseColor(cfg().colorInstaSell) & 0x00FFFFFF;
        g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x50000000 | rgb);
    }
}
