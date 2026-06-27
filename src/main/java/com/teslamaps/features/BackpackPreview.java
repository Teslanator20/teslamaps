/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BackpackPreview {

    private static final Pattern[] PATTERNS = {
        Pattern.compile("Ender Chest \\((\\d+)/\\d+\\)"),
        Pattern.compile("Ender Chest Page (\\d+)"),
        Pattern.compile("Backpack \\(Slot #(\\d+)\\)"),
        Pattern.compile("Slot #(\\d+)"),
        Pattern.compile("Backpack Slot (\\d+)"),
    };
    private static final boolean[] IS_ENDER = {true, true, false, false, false};

    private static String lastKey = null;

    public static void tick() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.backpackPreview && !cfg.customStorageOverlay) { flush(); return; }
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) { flush(); return; }

        String title = strip(cs.getTitle().getString());
        if (title.equals("Storage")) {
            for (Slot slot : cs.getMenu().slots) {
                if (slot.container instanceof Inventory || slot.getItem().isEmpty()) continue;
                String k = keyForStack(slot.getItem());
                if (k != null) StorageCache.putIcon(k, StorageCache.encode(slot.getItem()));
            }
            lastKey = "Storage";
            return;
        }
        String key = keyFor(title);
        if (key == null) { flush(); return; }

        int topSlots = 0;
        LinkedHashMap<Integer, String> slots = new LinkedHashMap<>();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            topSlots++;
            int cs0 = slot.getContainerSlot();
            if (cs0 < 9) continue;
            if (slot.getItem().isEmpty()) continue;
            String snbt = StorageCache.encode(slot.getItem());
            if (snbt != null) slots.put(cs0 - 9, snbt);
        }
        int size = Math.max(0, topSlots - 9);
        if (size > 0) StorageCache.put(key, size, slots);
        lastKey = key;
    }

    private static void flush() {
        if (lastKey != null) { StorageCache.save(); lastKey = null; }
    }

    public static String keyFromTitle(String title) { return keyFor(strip(title)); }

    private static String keyFor(String text) {
        for (int i = 0; i < PATTERNS.length; i++) {
            Matcher m = PATTERNS[i].matcher(text);
            if (!m.find()) continue;
            if (IS_ENDER[i]) return "epage_" + m.group(1);
            // backpack: require an explicit "Backpack" so generic "Slot #N" text
            // (e.g. "Exp Share Slot #1" in the pet menu) doesn't false-match
            if (!text.toLowerCase().contains("backpack")) continue;
            return "backpack_" + m.group(1);
        }
        return null;
    }

    private static String keyForStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(strip(stack.getHoverName().getString()));
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) for (Component line : lore.lines()) sb.append('\n').append(strip(line.getString()));
        return keyFor(sb.toString());
    }

    private static Map<Integer, ItemStack> hoveredItems() {
        if (!TeslaMapsConfig.get().backpackPreview) return null;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return null;
        Slot hovered = ((HandledScreenAccessor) cs).getFocusedSlot();
        if (hovered == null || hovered.getItem().isEmpty()) return null;
        String key = keyForStack(hovered.getItem());
        if (key == null) return null;
        Map<Integer, ItemStack> items = StorageCache.items(key);
        return (items == null || items.isEmpty()) ? null : items;
    }

    public static boolean suppressTooltip() {
        return hoveredItems() != null;
    }

    public static void renderOpenPageBorder(GuiGraphicsExtractor ctx) {
        if (!TeslaMapsConfig.get().customStorageOverlay) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        String key = keyFor(strip(cs.getTitle().getString()));
        if (key == null) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int x = acc.getX() - 4, y = acc.getY() - 4;
        int w = acc.getImageWidth() + 8, h = acc.getImageHeight() + 8;
        int col = key.startsWith("epage_") ? 0xFF55FFFF : 0xFFFFAA00;
        int t = 3;
        ctx.fill(x, y, x + w, y + t, col);
        ctx.fill(x, y + h - t, x + w, y + h, col);
        ctx.fill(x, y, x + t, y + h, col);
        ctx.fill(x + w - t, y, x + w, y + h, col);
    }

    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Map<Integer, ItemStack> items = hoveredItems();
        if (items == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;

        int cols = 9;
        int size = StorageCache.size(keyForStack(((HandledScreenAccessor) cs).getFocusedSlot().getItem()));
        if (size <= 0) for (int s : items.keySet()) size = Math.max(size, s + 1);
        int rows = (size + cols - 1) / cols;
        int cell = 18;
        int w = cols * cell + 8;
        int h = rows * cell + 8;

        int px = mouseX + 12;
        int py = mouseY - 12;
        if (px + w > cs.width) px = mouseX - w - 12;
        if (py + h > cs.height) py = cs.height - h;
        if (py < 0) py = 0;

        ctx.fill(px, py, px + w, py + h, 0xF0202020);
        ctx.fill(px, py, px + w, py + 1, 0xFF7755FF);
        ctx.fill(px, py + h - 1, px + w, py + h, 0xFF7755FF);

        for (int s = 0; s < rows * cols; s++) {
            int gx = px + 4 + (s % cols) * cell;
            int gy = py + 4 + (s / cols) * cell;
            ctx.fill(gx - 1, gy - 1, gx + 17, gy + 17, 0xFF555555);
        }
        for (Map.Entry<Integer, ItemStack> e : items.entrySet()) {
            int gx = px + 4 + (e.getKey() % cols) * cell;
            int gy = py + 4 + (e.getKey() / cols) * cell;
            ctx.item(e.getValue(), gx, gy);
            ctx.itemDecorations(mc.font, e.getValue(), gx, gy);
        }
    }

    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
    }
}
