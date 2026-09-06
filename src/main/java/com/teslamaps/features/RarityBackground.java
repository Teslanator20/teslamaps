package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public class RarityBackground {

    public static void draw(GuiGraphicsExtractor ctx, int x, int y, ItemStack stack) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.rarityBackgrounds) return;
        int rgb = rarityColor(stack);
        if (rgb == -1) return;

        int alpha = (int) (Math.max(0f, Math.min(1f, c.rarityBgOpacity)) * 255);
        int color = (alpha << 24) | (rgb & 0xFFFFFF);
        boolean outline = "Outline".equals(c.rarityBgStyle);
        int s = 16;

        if (!"Circle".equals(c.rarityBgShape)) {
            if (outline) {
                ctx.fill(x, y, x + s, y + 1, color);
                ctx.fill(x, y + s - 1, x + s, y + s, color);
                ctx.fill(x, y, x + 1, y + s, color);
                ctx.fill(x + s - 1, y, x + s, y + s, color);
            } else {
                ctx.fill(x, y, x + s, y + s, color);
            }
            return;
        }

        double cx = x + 8.0, cy = y + 8.0, r = 8.0;
        for (int row = 0; row < s; row++) {
            double dy = y + row + 0.5 - cy;
            double half = Math.sqrt(Math.max(0, r * r - dy * dy));
            int x0 = (int) Math.round(cx - half), x1 = (int) Math.round(cx + half);
            if (x1 <= x0) continue;
            if (outline) {
                ctx.fill(x0, y + row, Math.min(x0 + 1, x1), y + row + 1, color);
                ctx.fill(Math.max(x1 - 1, x0), y + row, x1, y + row + 1, color);
            } else {
                ctx.fill(x0, y + row, x1, y + row + 1, color);
            }
        }
    }

    // runs for every slot every frame, and the lore component of a stack is
    // immutable, so the answer only has to be computed once per lore instance
    private static final java.util.Map<ItemLore, Integer> colorCache = new java.util.IdentityHashMap<>();

    private static int rarityColor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return -1;
        Integer cached = colorCache.get(lore);
        if (cached != null) return cached;
        int color = computeRarityColor(lore);
        if (colorCache.size() > 1024) colorCache.clear();
        colorCache.put(lore, color);
        return color;
    }

    private static int computeRarityColor(ItemLore lore) {
        List<Component> lines = lore.lines();
        for (int i = lines.size() - 1; i >= 0; i--) {
            int c = matchRarity(lines.get(i).getString().toUpperCase());
            if (c != -1) return c;
        }
        return -1;
    }

    private static int matchRarity(String s) {
        if (s.contains("DIVINE")) return 0x55FFFF;
        if (s.contains("MYTHIC")) return 0xFF55FF;
        if (s.contains("LEGENDARY")) return 0xFFAA00;
        if (s.contains("EPIC")) return 0xAA00AA;
        if (s.contains("RARE")) return 0x5555FF;
        if (s.contains("UNCOMMON")) return 0x55FF55;
        if (s.contains("COMMON")) return 0xFFFFFF;
        if (s.contains("SPECIAL")) return 0xFF5555;     // SPECIAL / VERY SPECIAL
        if (s.contains("SUPREME") || s.contains("ULTIMATE") || s.contains("ADMIN")) return 0xAA0000;
        return -1;
    }
}
