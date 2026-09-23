package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public class RarityBackground {

    public static final String[] SHAPES = {"Square", "Rounded", "Circle", "Diamond", "Corners", "Underline"};
    public static final String[] STYLES = {"Filled", "Outline", "Gradient"};

    private static final int S = 16;

    public static void draw(GuiGraphicsExtractor ctx, int x, int y, ItemStack stack) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.rarityBackgrounds) return;
        int rgb = rarityColor(stack);
        if (rgb == -1) return;

        int alpha = (int) (Math.max(0f, Math.min(1f, c.rarityBgOpacity)) * 255);
        if (alpha <= 0) return;
        rgb &= 0xFFFFFF;

        String shape = c.rarityBgShape;
        boolean outline = "Outline".equals(c.rarityBgStyle);
        boolean gradient = "Gradient".equals(c.rarityBgStyle);

        if ("Corners".equals(shape)) {
            drawCorners(ctx, x, y, (alpha << 24) | rgb);
            return;
        }
        if ("Underline".equals(shape)) {
            int h = outline ? 1 : 2;
            if (gradient) {
                h = 4;
                for (int row = 0; row < h; row++) {
                    int a = alpha * (row + 1) / h;
                    ctx.fill(x, y + S - h + row, x + S, y + S - h + row + 1, (a << 24) | rgb);
                }
            } else {
                ctx.fill(x, y + S - h, x + S, y + S, (alpha << 24) | rgb);
            }
            return;
        }
        if ("Square".equals(shape) && !outline && !gradient) {
            ctx.fill(x, y, x + S, y + S, (alpha << 24) | rgb);
            return;
        }

        int[] prev = new int[2], cur = new int[2], next = new int[2];
        span(shape, -1, prev);
        span(shape, 0, cur);
        for (int row = 0; row < S; row++) {
            span(shape, row + 1, next);
            int x0 = cur[0], x1 = cur[1];
            if (x1 > x0) {
                int a = gradient ? alpha * (row + 1) / S : alpha;
                int color = (a << 24) | rgb;
                if (outline) {
                    ctx.fill(x + x0, y + row, x + Math.min(x0 + 1, x1), y + row + 1, color);
                    ctx.fill(x + Math.max(x1 - 1, x0), y + row, x + x1, y + row + 1, color);
                    cap(ctx, x, y + row, x0, x1, prev, color);
                    cap(ctx, x, y + row, x0, x1, next, color);
                } else {
                    ctx.fill(x + x0, y + row, x + x1, y + row + 1, color);
                }
            }
            int[] t = prev;
            prev = cur;
            cur = next;
            next = t;
        }
    }

    // the part of [x0,x1) not covered by the neighbouring row, so an outline
    // gets a top/bottom cap wherever the shape widens
    private static void cap(GuiGraphicsExtractor ctx, int x, int y, int x0, int x1, int[] other, int color) {
        int o0 = other[0], o1 = other[1];
        if (o1 <= o0) {
            ctx.fill(x + x0, y, x + x1, y + 1, color);
            return;
        }
        if (o0 > x0) ctx.fill(x + x0, y, x + Math.min(o0, x1), y + 1, color);
        if (o1 < x1) ctx.fill(x + Math.max(o1, x0), y, x + x1, y + 1, color);
    }

    private static void span(String shape, int row, int[] out) {
        out[0] = 0;
        out[1] = 0;
        if (row < 0 || row >= S) return;
        double dy = row + 0.5 - 8.0;
        switch (shape) {
            case "Circle" -> {
                double half = Math.sqrt(Math.max(0, 64.0 - dy * dy));
                out[0] = (int) Math.round(8.0 - half);
                out[1] = (int) Math.round(8.0 + half);
            }
            case "Diamond" -> {
                double half = 8.0 - Math.abs(dy);
                out[0] = (int) Math.round(8.0 - half);
                out[1] = (int) Math.round(8.0 + half);
            }
            case "Rounded" -> {
                double r = 5.0;
                double edge = 8.0 - Math.abs(dy);   // distance to nearest horizontal edge
                int inset = 0;
                if (edge < r) {
                    double d = r - edge;
                    inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - d * d)));
                }
                out[0] = inset;
                out[1] = S - inset;
            }
            default -> {
                out[0] = 0;
                out[1] = S;
            }
        }
        if (out[1] <= out[0]) {
            out[0] = 0;
            out[1] = 0;
        }
    }

    private static void drawCorners(GuiGraphicsExtractor ctx, int x, int y, int color) {
        int l = 5;
        ctx.fill(x, y, x + l, y + 1, color);
        ctx.fill(x, y, x + 1, y + l, color);
        ctx.fill(x + S - l, y, x + S, y + 1, color);
        ctx.fill(x + S - 1, y, x + S, y + l, color);
        ctx.fill(x, y + S - 1, x + l, y + S, color);
        ctx.fill(x, y + S - l, x + 1, y + S, color);
        ctx.fill(x + S - l, y + S - 1, x + S, y + S, color);
        ctx.fill(x + S - 1, y + S - l, x + S, y + S, color);
    }

    // runs for every slot every frame, and the lore component of a stack is
    // immutable, so the answer only has to be computed once per lore instance
    private static final java.util.Map<ItemLore, Integer> colorCache = new java.util.IdentityHashMap<>();

    private static int rarityColor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        // hypixel tags skyblock items with a tooltip style named after the rarity
        net.minecraft.resources.Identifier style = stack.get(DataComponents.TOOLTIP_STYLE);
        if (style != null && "hypixel_skyblock".equals(style.getNamespace())) {
            int c = styleColor(style.getPath());
            if (c != -1) return c;
        }
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return -1;
        Integer cached = colorCache.get(lore);
        if (cached != null) return cached;
        int color = computeRarityColor(lore);
        if (colorCache.size() > 1024) colorCache.clear();
        colorCache.put(lore, color);
        return color;
    }

    private static final int[] RANK_COLORS = {0xFFFFFF, 0x55FF55, 0x5555FF, 0xAA00AA, 0xFFAA00, 0xFF55FF, 0x55FFFF, 0xFF5555, 0xAA0000};

    /** 0 = common ... higher = rarer, -1 = unknown. */
    public static int rarityRank(ItemStack stack) {
        int c = rarityColor(stack);
        if (c == -1) return -1;
        for (int i = 0; i < RANK_COLORS.length; i++) if (RANK_COLORS[i] == c) return i;
        return -1;
    }

    private static int computeRarityColor(ItemLore lore) {
        List<Component> lines = lore.lines();
        for (int i = lines.size() - 1; i >= 0; i--) {
            int c = matchRarity(com.teslamaps.utils.StringUtil.stripTrim(lines.get(i).getString()));
            if (c != -1) return c;
        }
        return -1;
    }

    private static int styleColor(String path) {
        return switch (path) {
            case "common" -> 0xFFFFFF;
            case "uncommon" -> 0x55FF55;
            case "rare" -> 0x5555FF;
            case "epic" -> 0xAA00AA;
            case "legendary" -> 0xFFAA00;
            case "mythic" -> 0xFF55FF;
            case "divine" -> 0x55FFFF;
            case "special", "very_special" -> 0xFF5555;
            case "supreme", "ultimate", "admin" -> 0xAA0000;
            default -> -1;
        };
    }

    // case sensitive on purpose: Hypixel's rarity line is upper case, pet perk text is
    // not, so uppercasing the line first is what made every pet mentioning "find rare"
    // or "legendary treasure" pick up a colour. UNCOMMON has to be tested before COMMON.
    private static int matchRarity(String s) {
        if (s.isEmpty()) return -1;
        if (s.contains("DIVINE")) return 0x55FFFF;
        if (s.contains("MYTHIC")) return 0xFF55FF;
        if (s.contains("LEGENDARY")) return 0xFFAA00;
        if (s.contains("EPIC")) return 0xAA00AA;
        if (s.contains("UNCOMMON")) return 0x55FF55;
        if (s.contains("RARE")) return 0x5555FF;
        if (s.contains("COMMON")) return 0xFFFFFF;
        if (s.contains("SPECIAL")) return 0xFF5555;     // SPECIAL / VERY SPECIAL
        if (s.contains("SUPREME") || s.contains("ULTIMATE") || s.contains("ADMIN")) return 0xAA0000;
        return -1;
    }
}
