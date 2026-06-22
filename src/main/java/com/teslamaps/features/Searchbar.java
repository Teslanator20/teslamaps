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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

public class Searchbar {

    private static final int BOX_W = 120, BOX_H = 12;
    private static String query = "";
    private static String lastTitle = "";
    private static boolean focused = false;

    private static boolean enabled() {
        if (!TeslaMapsConfig.get().searchbar) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc.screen instanceof AbstractContainerScreen<?>;
    }

    public static boolean handleClick(double mx, double my, int button) {
        if (!enabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return false;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int bx = acc.getX(), by = acc.getY() - BOX_H - 1;
        boolean inside = mx >= bx && mx <= bx + BOX_W && my >= by && my <= by + BOX_H;
        if (button == 1) {
            if (inside) { query = ""; return true; }
            return false;
        }
        if (button != 0) return false;
        focused = inside;
        return inside;
    }

    public static boolean onKeyPressed(int key) {
        if (!enabled() || !focused) return false;
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            query = "";
            focused = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE) { query += " "; return true; }
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            query += (char) ('a' + (key - GLFW.GLFW_KEY_A));
            return true;
        }
        return false;
    }

    public static boolean shouldRender() {
        if (!TeslaMapsConfig.get().searchbar) return false;
        return Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        String title = cs.getTitle().getString().replaceAll("(?i)§[0-9A-FK-OR]", "");
        if (!title.equals(lastTitle)) { query = ""; focused = false; lastTitle = title; }

        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int left = acc.getX(), top = acc.getY();
        int bx = left, by = top - BOX_H - 1;
        ctx.fill(bx, by, bx + BOX_W, by + BOX_H, focused ? 0xFF202020 : 0xC0202020);
        int border = focused ? 0xFFFFD040 : 0xFF606060;
        ctx.fill(bx, by, bx + BOX_W, by + 1, border);
        ctx.fill(bx, by + BOX_H - 1, bx + BOX_W, by + BOX_H, border);
        ctx.fill(bx, by, bx + 1, by + BOX_H, border);
        ctx.fill(bx + BOX_W - 1, by, bx + BOX_W, by + BOX_H, border);
        String label = query.isEmpty()
                ? (focused ? "§7type to search…" : "§8click to search")
                : "§f" + query + (focused ? "§e_" : "");
        ctx.text(mc.font, label, bx + 3, by + 2, 0xFFFFFFFF);

        if (query.isEmpty()) return;
        for (Slot slot : cs.getMenu().slots) {
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            int sx = left + slot.x, sy = top + slot.y;
            if (matches(item, query)) ctx.fill(sx, sy, sx + 16, sy + 16, 0x6655FF55);
            else ctx.fill(sx, sy, sx + 16, sy + 16, 0xB0101010);
        }
    }

    private static boolean matches(ItemStack stack, String q) {
        if (strip(stack.getHoverName().getString()).contains(q)) return true;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) for (Component line : lore.lines()) if (strip(line.getString()).contains(q)) return true;
        return false;
    }

    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("(?i)§[0-9A-FK-OR]", "").toLowerCase();
    }
}
