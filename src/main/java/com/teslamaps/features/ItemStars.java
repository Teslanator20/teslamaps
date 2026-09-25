package com.teslamaps.features;

// Devonian's StarsStackSize.kt + SkyOcean's ItemStars.kt (colours, red master stars)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
                .when(() -> cfg().starsStackSize || cfg().redMasterStars);
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!cfg().redMasterStars || lines.isEmpty() || !feature.active()) return;
            Component fixed = masterStars(lines.get(0));
            if (fixed != null) lines.set(0, fixed);
        });
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

    // ✪✪✪✪✪➋ -> two red ✪ and three gold ones
    private static Component masterStars(Component name) {
        String s = name.getString();
        int at = s.indexOf("✪✪✪✪✪");
        if (at < 0) return null;
        int master = 0;
        for (int i = at + 5; i < s.length() && master == 0; i++) {
            char c = s.charAt(i);
            if (c >= '➊' && c <= '➎') master = c - '➊' + 1;
        }
        if (master == 0) return null;
        final int m = master;
        MutableComponent out = Component.empty();
        int[] seen = {0};
        name.visit((Style style, String seg) -> {
            StringBuilder plain = new StringBuilder();
            for (int i = 0; i < seg.length(); i++) {
                char c = seg.charAt(i);
                if (c >= '➊' && c <= '➎') continue;
                if (c == '✪' && seen[0] < 5) {
                    if (!plain.isEmpty()) { out.append(Component.literal(plain.toString()).withStyle(style)); plain.setLength(0); }
                    int col = seen[0] < m ? 0xFF5555 : 0xFFAA00;
                    out.append(Component.literal("✪").withStyle(style.withColor(TextColor.fromRgb(col))));
                    seen[0]++;
                    continue;
                }
                plain.append(c);
            }
            if (!plain.isEmpty()) out.append(Component.literal(plain.toString()).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }
}
