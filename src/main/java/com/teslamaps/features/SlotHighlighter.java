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
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.Set;

public class SlotHighlighter {

    private static final int YELLOW = 0x80FFFF00;
    private static final Set<String> SELLABLE_IDS = Set.of(
            "DEFUSE_KIT", "TRAINING_WEIGHTS", "DUNGEON_LORE_PAPER", "REVIVE_STONE");

    public static boolean shouldRender() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.salvageHelper && !c.sellableHighlighter && !c.showSelectedPet) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return false;
        String t = strip(cs.getTitle().getString());
        return (c.salvageHelper && t.startsWith("Salvage Items"))
                || (c.sellableHighlighter && (t.equals("Ophelia") || t.equals("Booster Cookie")))
                || (c.showSelectedPet && isPetsMenu(t));
    }

    private static boolean isPetsMenu(String t) { return t.equals("Pets") || t.matches("\\(\\d+/\\d+\\) Pets"); }

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        String t = strip(cs.getTitle().getString());
        TeslaMapsConfig c = TeslaMapsConfig.get();

        if (c.salvageHelper && t.startsWith("Salvage Items")) {
            PriceManager.ensureFresh();
            for (Slot slot : cs.getMenu().slots) {
                if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
                if (isSalvageable(slot.getItem())) fillSlot(ctx, acc, slot);
            }
        } else if (c.sellableHighlighter && (t.equals("Ophelia") || t.equals("Booster Cookie"))) {
            for (Slot slot : cs.getMenu().slots) {
                if (!(slot.container instanceof net.minecraft.world.entity.player.Inventory)) continue;
                if (isSellable(slot.getItem())) fillSlot(ctx, acc, slot);
            }
        } else if (c.showSelectedPet && isPetsMenu(t)) {
            for (Slot slot : cs.getMenu().slots) {
                if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
                if (isSpawnedPet(slot.getItem())) { fillCyan(ctx, acc, slot); break; }
            }
        }
    }

    private static boolean isSpawnedPet(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;
        for (Component line : lore.lines()) if (strip(line.getString()).equals("Click to despawn!")) return true;
        return false;
    }

    private static void fillCyan(GuiGraphicsExtractor ctx, HandledScreenAccessor acc, Slot slot) {
        int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
        ctx.fill(sx, sy, sx + 16, sy + 16, 0x8000FFFF);
    }

    private static boolean isSalvageable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;
        boolean dungeon = false;
        for (Component line : lore.lines()) {
            String l = strip(line.getString());
            if (l.contains("DUNGEON") && !l.contains("DUNGEON ITEM")) { dungeon = true; break; }
        }
        if (!dungeon) return false;
        double price = PriceManager.getPrice(skyblockId(stack));
        return price > 0 && price < 100_000;
    }

    private static boolean isSellable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = skyblockId(stack);
        if (id.equals("POTION")) return strip(stack.getHoverName().getString()).contains("Healing VIII");
        return SELLABLE_IDS.contains(id);
    }

    private static void fillSlot(GuiGraphicsExtractor ctx, HandledScreenAccessor acc, Slot slot) {
        int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
        ctx.fill(sx, sy, sx + 16, sy + 16, YELLOW);
    }

    private static String skyblockId(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null) return "";
        return d.copyTag().getString("id").orElse("");
    }

    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
    }
}
