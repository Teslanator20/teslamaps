package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.utils.Calculator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

public class Searchbar {

    private static final int BOX_W = 120, BOX_H = 12;
    public static final String[] MODES = {"Fill", "Outline"};
    private static String query = "";
    private static String lastTitle = "";
    private static long lastRenderMs = 0;
    private static boolean focused = false;
    private static boolean shown = false;

    // per-slot match cache, recomputed only when the query or the slot's stack changes
    private static ItemStack[] cachedStacks = new ItemStack[0];
    private static boolean[] cachedMatch = new boolean[0];
    private static String cachedQuery = "";
    private static boolean cachedLore = true;

    public static String query() {
        return TeslaMapsConfig.get().searchbar ? query : "";
    }

    private static boolean enabled() {
        if (!TeslaMapsConfig.get().searchbar) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc.gui.screen() instanceof AbstractContainerScreen<?>;
    }

    private static boolean visible() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return !(c.searchbarCtrlF && c.searchbarHidden) || shown || !query.isEmpty();
    }

    public static boolean handleClick(double mx, double my, int button) {
        if (!enabled() || !visible()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return false;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int bx = acc.getX(), by = acc.getY() - BOX_H - 1;
        boolean inside = mx >= bx && mx <= bx + BOX_W && my >= by && my <= by + BOX_H;
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
            if (inside) { query = ""; return true; }
            return false;
        }
        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
        focused = inside;
        return inside;
    }

    private static boolean ctrlDown() {
        long w = Minecraft.getInstance().getWindow().handle();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RCONTROL);
    }

    public static boolean onKeyPressed(int key, int scancode) {
        if (!enabled()) return false;
        if (TeslaMapsConfig.get().searchbarCtrlF && (key == com.mojang.blaze3d.platform.InputConstants.KEY_F || key == com.mojang.blaze3d.platform.InputConstants.KEY_K) && ctrlDown()) {
            shown = true;
            focused = true;
            return true;
        }
        if (!focused) return false;
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE) {
            if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
            return true;
        }
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            query = "";
            focused = false;
            return true;
        }
        // swallow everything else so typing cannot close the container or swap hotbar slots
        return true;
    }

    public static boolean charTyped(int codepoint) {
        if (!enabled() || !focused) return false;
        if (codepoint < 32 || codepoint == 127) return false;
        query += Character.toString(Character.toLowerCase(codepoint));
        return true;
    }

    public static boolean shouldRender() {
        if (!TeslaMapsConfig.get().searchbar) return false;
        return Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?>;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String title = com.teslamaps.utils.StringUtil.strip(cs.getTitle().getString());
        // keep the query across containers/pages; just drop focus when the container changes
        if (!title.equals(lastTitle)) { focused = false; lastTitle = title; }

        // page swaps re-render within a tick or two; a longer gap means the GUI was actually
        // closed -> drop the query so a stale search doesn't tint every future container
        long now = System.currentTimeMillis();
        if (now - lastRenderMs > 1500) { query = ""; shown = false; }
        lastRenderMs = now;
        if (!visible()) return;

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

        // a leading = turns the box into a calculator, so nothing gets searched or dimmed
        if (query.startsWith("=")) {
            Double value = Calculator.evalBasic(query.substring(1));
            String result = query.length() == 1 ? "§8= 4*(2+3)"
                    : value == null ? "§c= ?" : "§a= " + Calculator.format(value);
            int w = mc.font.width(result) + 6;
            ctx.fill(bx, by - BOX_H - 1, bx + w, by - 1, 0xC0202020);
            ctx.text(mc.font, result, bx + 3, by - BOX_H + 1, 0xFFFFFFFF);
            return;
        }

        if (query.isBlank()) return;
        List<Slot> slots = cs.getMenu().slots;
        refreshCache(slots.size(), !c.searchbarNameOnly);
        boolean outline = "Outline".equals(c.searchbarMode);
        int hit = TeslaMapsConfig.parseColor(c.colorSearchbarMatch);
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            int sx = left + slot.x, sy = top + slot.y;
            if (cachedStacks[i] != item) {
                cachedStacks[i] = item;
                cachedMatch[i] = matches(item, query, !c.searchbarNameOnly);
            }
            if (cachedMatch[i]) {
                if (outline) {
                    int o = hit | 0xFF000000;
                    ctx.fill(sx, sy, sx + 16, sy + 1, o);
                    ctx.fill(sx, sy + 15, sx + 16, sy + 16, o);
                    ctx.fill(sx, sy, sx + 1, sy + 16, o);
                    ctx.fill(sx + 15, sy, sx + 16, sy + 16, o);
                } else ctx.fill(sx, sy, sx + 16, sy + 16, hit);
            } else if (!outline) ctx.fill(sx, sy, sx + 16, sy + 16, 0xB0101010);
        }
    }

    private static void refreshCache(int n, boolean lore) {
        if (cachedStacks.length != n || !cachedQuery.equals(query) || cachedLore != lore) {
            cachedStacks = new ItemStack[n];
            cachedMatch = new boolean[n];
            cachedQuery = query;
            cachedLore = lore;
        }
    }

    private static boolean matches(ItemStack stack, String q, boolean withLore) {
        if (strip(stack.getHoverName().getString()).contains(q)) return true;
        if (!withLore) return false;
        for (String line : com.teslamaps.utils.ItemUtil.loreLines(stack)) if (line.toLowerCase().contains(q)) return true;
        return false;
    }

    private static String strip(String s) {
        if (s == null) return "";
        return com.teslamaps.utils.StringUtil.strip(s).toLowerCase();
    }
}
