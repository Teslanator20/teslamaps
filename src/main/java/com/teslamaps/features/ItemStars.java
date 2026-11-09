package com.teslamaps.features;

// Devonian's StarsStackSize.kt + SkyOcean's ItemStars.kt (colours)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class ItemStars {

    private record Entry(String text, int width, boolean dungeon, int stars) {}
    private static final Entry NONE = new Entry("", 0, false, 0);

    private static final int[] DUNGEON = {0xFFFFE0, 0xFFFFE0, 0xFFFF00, 0xFFA500, 0xFF8C00,
            0xFF4500, 0xFF4500, 0xFF0000, 0xCD5C5C, 0x8B0000};
    private static final int[] OTHER = {0xFFFFE0, 0xFFFFE0, 0xFFFF00, 0xFFA500, 0xFF8C00,
            0xFFB6C1, 0xFFB6C1, 0xFF69B4, 0xFF1493, 0xFFC0CB, 0xE0FFFF, 0x00FFFF, 0x87CEFA, 0x6495ED, 0x4682B4};

    private static final Map<CustomData, Entry> cache = new IdentityHashMap<>();
    private static Feature feature;

    private ItemStars() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("ItemStars").section("Inventory")
                .when(() -> cfg().starsStackSize);
    }

    private static Entry get(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !cfg().starsStackSize || !feature.active()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        Entry e = cache.get(data);
        if (e == null) {
            if (cache.size() > 2048) cache.clear();
            e = build(stack, data);
            cache.put(data, e);
        }
        return e == NONE ? null : e;
    }

    private static Entry build(ItemStack stack, CustomData data) {
        CompoundTag tag = data.copyTag();
        int stars = tag.getIntOr("upgrade_level", -1);
        if (stars < 0) stars = tag.getIntOr("dungeon_item_level", -1);
        if (stars <= 0) return NONE;
        boolean dungeon = tag.contains("dungeon_item_level");
        if (!dungeon) {
            List<String> lore = ItemUtil.loreLines(stack);
            for (int i = lore.size() - 1; i >= 0 && i >= lore.size() - 3; i--) {
                if (StringUtil.strip(lore.get(i)).contains("DUNGEON")) { dungeon = true; break; }
            }
        }
        String text = String.valueOf(stars);
        return new Entry(text, Minecraft.getInstance().font.width(text), dungeon, stars);
    }

    private static int color(Entry e) {
        if (!cfg().starsStackSizeColors) return 0xFFFFFFFF;
        int[] table = e.dungeon ? DUNGEON : OTHER;
        return 0xFF000000 | table[Math.min(e.stars, table.length) - 1];
    }

    public static void draw(GuiGraphicsExtractor ctx, ItemStack stack, int x, int y) {
        Entry e = get(stack);
        if (e == null) return;
        Font font = Minecraft.getInstance().font;
        ctx.text(font, e.text, x + 17 - e.width, y + 9, color(e), true);
    }
}
