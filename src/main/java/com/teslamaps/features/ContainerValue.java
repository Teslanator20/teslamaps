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
import com.teslamaps.features.croesus.CroesusProfit;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ContainerValue {

    private record Entry(String name, double value) {}

    public static boolean shouldRender() {
        if (!TeslaMapsConfig.get().containerValue) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) return false;
        return !CroesusProfit.shouldRender();
    }

    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        PriceManager.ensureFresh();
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;

        double total = 0;
        List<Entry> entries = new ArrayList<>();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            double v = EstimatedValue.compute(item) * item.getCount();
            if (v <= 0) continue;
            total += v;
            entries.add(new Entry(strip(item.getHoverName().getString()), v));
        }
        if (entries.isEmpty()) return;
        entries.sort((a, b) -> Double.compare(b.value, a.value));

        List<String> lines = new ArrayList<>();
        lines.add("§6§lContainer Value");
        lines.add("§7Total: §6" + EstimatedValue.fmt(total));
        if (!PriceManager.isLoaded()) lines.add("§7loading prices…");
        int top = Math.min(5, entries.size());
        lines.add("§7Top " + top + ":");
        for (int i = 0; i < top; i++) {
            Entry e = entries.get(i);
            lines.add(" §f" + e.name + " §7- §6" + EstimatedValue.fmt(e.value));
        }
        drawPanel(ctx, mc, acc, lines);
    }

    private static void drawPanel(GuiGraphicsExtractor ctx, Minecraft mc, HandledScreenAccessor acc, List<String> lines) {
        int panelX = acc.getX() + 176 + 6;
        int panelY = acc.getY();
        int w = 0;
        for (String l : lines) w = Math.max(w, mc.font.width(l));
        int pad = 4;
        ctx.fill(panelX - pad, panelY - pad, panelX + w + pad, panelY + lines.size() * 10 + pad, 0xCC101012);
        int y = panelY;
        for (String l : lines) { ctx.text(mc.font, l, panelX, y, 0xFFFFFFFF); y += 10; }
    }

    private static String strip(String s) {
        return s.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
    }
}
