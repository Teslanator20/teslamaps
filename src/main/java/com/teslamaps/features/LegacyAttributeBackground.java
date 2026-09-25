package com.teslamaps.features;

// Ported from Skyblocker's LegacyAttributeBackground.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.IdentityHashMap;
import java.util.Map;

/** Red slot behind items that still carry old Kuudra attributes. */
public final class LegacyAttributeBackground {

    // custom data is immutable per instance, copyTag() allocates
    private static final Map<CustomData, Boolean> cache = new IdentityHashMap<>();

    private LegacyAttributeBackground() {}

    public static void draw(GuiGraphicsExtractor ctx, int x, int y, ItemStack stack) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.legacyAttributeBg || !c.section("Inventory") || !has(stack)) return;
        ctx.fill(x, y, x + 16, y + 16, TeslaMapsConfig.parseColor(c.colorLegacyAttributeBg));
    }

    private static boolean has(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        Boolean cached = cache.get(data);
        if (cached != null) return cached;
        boolean has = data.copyTag().contains("attributes") && !"ATTRIBUTE_SHARD".equals(ItemUtil.skyblockId(stack));
        if (cache.size() > 2048) cache.clear();
        cache.put(data, has);
        return has;
    }
}
