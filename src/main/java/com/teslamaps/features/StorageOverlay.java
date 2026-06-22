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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StorageOverlay {

    private static final float SCALE = 1.5f;
    private static final float INV_SCALE = 2.0f;
    private static final int COLS = 3, CELL = 18, GRID_COLS = 9, HEADER = 10;
    private static final int BOX_W = GRID_COLS * CELL + 6;
    private static final int GAP = 8;

    private static int topPad() { return TeslaMapsConfig.get().storageShowNames ? HEADER : 2; }
    private static int boxH() { return topPad() + 5 * CELL + 2; }
    private static final int HEADER_PX = 34;

    private record Box(String key, String label, boolean ender, int num) {}

    private static String search = "";
    private static boolean searchFocused = false;
    private static float scroll = 0;
    private static boolean scrollDragging = false;

    public static boolean active() {
        if (!TeslaMapsConfig.get().customStorageOverlay) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return false;
        String t = strip(cs.getTitle().getString());
        return t.equals("Storage") || BackpackPreview.keyFromTitle(t) != null;
    }

    private static int globalIndex(String key) {
        if (key == null) return -1;
        try {
            if (key.startsWith("epage_")) return Integer.parseInt(key.substring(6)) - 1;
            if (key.startsWith("backpack_")) return 9 + Integer.parseInt(key.substring(9)) - 1;
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    private static boolean sameCategory(int a, int b) { return a >= 0 && b >= 0 && (a < 9) == (b < 9); }

    private static String commandForGlobal(int gi) {
        if (gi < 0 || gi > 26) return null;
        return gi < 9 ? "ec " + (gi + 1) : "bp " + (gi - 9 + 1);
    }

    private static void navigateDir(boolean next) {
        int gi = globalIndex(liveKey());
        if (gi < 0) return;
        int tgi = next ? gi + 1 : gi - 1;
        if (tgi < 0 || tgi > 26) return;
        if (sameCategory(gi, tgi)) {
            clickPageArrow(next ? "Next Page" : "Previous Page");
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) mc.getConnection().sendCommand(commandForGlobal(tgi));
        }
    }

    private static void navigateRow(int delta) {
        int gi = globalIndex(liveKey());
        if (gi < 0) return;
        int tgi = gi + delta;
        if (tgi < 0 || tgi > 26) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand(commandForGlobal(tgi));
    }

    private static String liveKey() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return null;
        return BackpackPreview.keyFromTitle(strip(cs.getTitle().getString()));
    }

    private static Map<Integer, ItemStack> liveContent() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return Map.of();
        AbstractContainerMenu menu = mc.player.containerMenu;
        LinkedHashMap<Integer, ItemStack> out = new LinkedHashMap<>();
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory) continue;
            int cs = s.getContainerSlot();
            if (cs < 9 || s.getItem().isEmpty()) continue;
            out.put(cs - 9, s.getItem());
        }
        return out;
    }

    private static List<Box> visible() {
        List<Box> out = new ArrayList<>();
        String live = liveKey();
        for (int i = 1; i <= 9; i++) addIfVisible(out, "epage_" + i, "Ender Page " + i, true, i, live);
        for (int i = 1; i <= 18; i++) addIfVisible(out, "backpack_" + i, "Backpack " + i, false, i, live);
        return out;
    }

    private static void addIfVisible(List<Box> out, String key, String label, boolean ender, int num, String live) {
        boolean isLive = key.equals(live);
        if (StorageCache.size(key) <= 0 && !isLive) return;
        Map<Integer, ItemStack> items = StorageCache.items(key);
        if (!search.isEmpty() && !isLive) {
            boolean match = false;
            if (items != null) for (ItemStack st : items.values()) if (matches(st, search)) { match = true; break; }
            if (!match) return;
        }
        out.add(new Box(key, label, ender, num));
    }

    private static int startX(int screenW) {
        int vw = (int) (screenW / SCALE);
        return (vw - (COLS * BOX_W + (COLS - 1) * GAP)) / 2;
    }

    private static int startY() { return (int) (HEADER_PX / SCALE) + 4; }

    private static int invVW() { return (int) (Minecraft.getInstance().screen.width / INV_SCALE); }
    private static int invVH() { return (int) (Minecraft.getInstance().screen.height / INV_SCALE); }
    private static int invX() { return (invVW() - 9 * 18) / 2; }
    private static int hotbarY() { return invVH() - 22; }
    private static int mainY() { return hotbarY() - 58; }

    private static int[] sbRect() {
        int x = (int) (invX() * INV_SCALE);
        int w = (int) (9 * 18 * INV_SCALE);
        int y = (int) ((mainY() - 13) * INV_SCALE) + 6;
        return new int[]{x, y, w, 14};
    }

    private static int[] invSlotPos(int cs) {
        if (cs < 9) return new int[]{invX() + cs * 18, hotbarY()};
        int idx = cs - 9;
        return new int[]{invX() + (idx % 9) * 18, mainY() + (idx / 9) * 18};
    }

    private static final int SEL_CELL = 24, SEL_COLS = 9;
    private static final float SEL_ICON = 1.35f;
    private static int selX() { return (int) (invX() * INV_SCALE) - SEL_COLS * SEL_CELL - 30; }
    private static int selY() { return (int) (mainY() * INV_SCALE) + 14; }
    private static int[] selIconPos(int i) { return new int[]{selX() + (i % SEL_COLS) * SEL_CELL, selY() + (i / SEL_COLS) * SEL_CELL}; }
    private static String selKey(int i) { return i < 9 ? "epage_" + (i + 1) : "backpack_" + (i - 8); }

    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.screen.width, sh = mc.screen.height;
        AbstractContainerMenu menu = mc.player != null ? mc.player.containerMenu : null;
        ctx.fill(0, 0, sw, sh, 0xE8101010);
        List<Box> vis = visible();
        boolean showNames = TeslaMapsConfig.get().storageShowNames;
        ctx.text(mc.font, "§6§lStorage §7(" + vis.size() + ")", 8, 6, 0xFFFFFFFF);
        drawButton(ctx, mc, sw - 96, 6, 88, "§cVanilla View ✕");

        String live = liveKey();
        boolean searching = !search.isEmpty();
        float vmx = mouseX / SCALE, vmy = mouseY / SCALE;
        int sx = startX(sw), sy = startY();
        int wallBottom = (int) ((mainY() - 13) * INV_SCALE) - 2;

        ItemStack hoverNonLive = null;
        ctx.enableScissor(0, HEADER_PX, sw, Math.max(HEADER_PX, wallBottom));
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.scale(SCALE, SCALE);
        for (int i = 0; i < vis.size(); i++) {
            Box b = vis.get(i);
            int bx = sx + (i % COLS) * (BOX_W + GAP);
            int by = (int) (sy + (i / COLS) * (boxH() + GAP) - scroll);
            if (by + boxH() < HEADER_PX / SCALE || by > sh / SCALE) continue;
            boolean isLive = b.key.equals(live);

            boolean hoverBox = vmx >= bx && vmx <= bx + BOX_W && vmy >= by && vmy <= by + boxH();
            ctx.fill(bx, by, bx + BOX_W, by + boxH(), hoverBox ? 0xFF2A2A2A : 0xF01E1E1E);
            if (isLive) {
                int c = b.ender ? 0xFF55FFFF : 0xFFFFAA00;
                ctx.fill(bx - 1, by - 1, bx + BOX_W + 1, by + 1, c);
                ctx.fill(bx - 1, by + boxH() - 1, bx + BOX_W + 1, by + boxH() + 1, c);
                ctx.fill(bx - 1, by - 1, bx + 1, by + boxH() + 1, c);
                ctx.fill(bx + BOX_W - 1, by - 1, bx + BOX_W + 1, by + boxH() + 1, c);
            }
            if (showNames) ctx.text(mc.font, b.label + (isLive ? " §a●" : ""), bx + 4, by + 2, 0xFFFFFFFF);

            int size = StorageCache.size(b.key);
            Map<Integer, ItemStack> items = isLive ? liveContent() : StorageCache.items(b.key);
            int gx0 = bx + 4, gy0 = by + topPad();
            int emptyCol = searching ? 0xFF252525 : 0xFF555555;
            for (int s = 0; s < size; s++) {
                int gx = gx0 + (s % GRID_COLS) * CELL, gy = gy0 + (s / GRID_COLS) * CELL;
                ctx.fill(gx, gy, gx + 16, gy + 16, emptyCol);
            }
            if (items != null) for (Map.Entry<Integer, ItemStack> e : items.entrySet()) {
                int gx = gx0 + (e.getKey() % GRID_COLS) * CELL, gy = gy0 + (e.getKey() / GRID_COLS) * CELL;
                ItemStack st = e.getValue();
                boolean match = !searching || matches(st, search);
                if (searching && match) ctx.fill(gx, gy, gx + 16, gy + 16, 0xFF2E8B2E); // bright green bg = hit
                ctx.item(st, gx, gy);
                ctx.itemDecorations(mc.font, st, gx, gy);
                if (searching && !match) ctx.fill(gx, gy, gx + 16, gy + 16, 0xC0000000); // dim non-hit
                if (!isLive && vmx >= gx && vmx < gx + 16 && vmy >= gy && vmy < gy + 16 && mouseY < wallBottom) hoverNonLive = st;
            }
        }
        pose.popMatrix();
        ctx.disableScissor();

        int rows = (vis.size() + COLS - 1) / COLS;
        float contentVirt = rows * (boxH() + GAP) + startY() + 10;
        float visVirt = wallBottom / SCALE - startY();
        float maxScroll = Math.max(0, contentVirt - wallBottom / SCALE);
        if (maxScroll > 0) {
            int trackW = 10, trackX = sw - trackW - 3, trackTop = HEADER_PX, trackH = wallBottom - HEADER_PX;
            int thumbH = Math.max(24, (int) (trackH * Math.min(1f, visVirt / (contentVirt - startY()))));
            if (scrollDragging) {
                long w = mc.getWindow().handle();
                if (GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) scrollDragging = false;
                else {
                    float frac = (mouseY - trackTop - thumbH / 2f) / Math.max(1, trackH - thumbH);
                    scroll = Math.max(0, Math.min(maxScroll, frac * maxScroll));
                }
            }
            int thumbY = trackTop + (int) ((trackH - thumbH) * (scroll / maxScroll));
            ctx.fill(trackX, trackTop, trackX + trackW, trackTop + trackH, 0x80000000);
            boolean hoverBar = mouseX >= trackX && mouseX <= trackX + trackW && mouseY >= trackTop && mouseY <= trackTop + trackH;
            ctx.fill(trackX + 1, thumbY, trackX + trackW - 1, thumbY + thumbH, (hoverBar || scrollDragging) ? 0xFFE0E0E0 : 0xFFAAAAAA);
        } else scrollDragging = false;

        if (menu != null) {
            pose.pushMatrix();
            pose.scale(INV_SCALE, INV_SCALE);
            int px0 = invX() - 5, py0 = mainY() - 13, px1 = invX() + 9 * 18 + 5, py1 = hotbarY() + 18 + 5;
            ctx.fill(px0, py0, px1, py1, 0xFF1A1A1A);
            ctx.fill(px0, py0, px1, py0 + 1, 0xFF3A3A3A);
            ctx.fill(px0, py1 - 1, px1, py1, 0xFF000000);
            for (Slot s : menu.slots) {
                if (!(s.container instanceof Inventory)) continue;
                int[] p = invSlotPos(s.getContainerSlot());
                ctx.fill(p[0] - 1, p[1] - 1, p[0] + 17, p[1] + 17, 0xFF373737);
                ctx.fill(p[0] - 1, p[1] - 1, p[0] + 16, p[1], 0xFF8B8B8B);
                ctx.fill(p[0] - 1, p[1] - 1, p[0], p[1] + 16, 0xFF8B8B8B);
                ctx.fill(p[0], p[1], p[0] + 16, p[1] + 16, 0xFF565656);
                if (!s.getItem().isEmpty()) {
                    ctx.item(s.getItem(), p[0], p[1]);
                    ctx.itemDecorations(mc.font, s.getItem(), p[0], p[1]);
                }
            }
            pose.popMatrix();
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty()) {
                ctx.item(carried, mouseX - 8, mouseY - 8);
                ctx.itemDecorations(mc.font, carried, mouseX - 8, mouseY - 8);
            }
        }

        int[] sb = sbRect();
        ctx.fill(sb[0], sb[1], sb[0] + sb[2], sb[1] + sb[3], 0xFF202020);
        int sbc = searchFocused ? 0xFFFFD040 : 0xFF606060;
        ctx.fill(sb[0], sb[1], sb[0] + sb[2], sb[1] + 1, sbc);
        ctx.fill(sb[0], sb[1] + sb[3] - 1, sb[0] + sb[2], sb[1] + sb[3], sbc);
        ctx.fill(sb[0], sb[1], sb[0] + 1, sb[1] + sb[3], sbc);
        ctx.fill(sb[0] + sb[2] - 1, sb[1], sb[0] + sb[2], sb[1] + sb[3], sbc);
        ctx.text(mc.font, search.isEmpty() ? (searchFocused ? "§7type to search… (name + tooltip)" : "§8click to search")
                : "§f" + search + (searchFocused ? "§e_" : ""), sb[0] + 4, sb[1] + 3, 0xFFFFFFFF);

        String selHover = renderSelector(ctx, mc, mouseX, mouseY);

        if (hoverNonLive != null && (menu == null || menu.getCarried().isEmpty())) {
            ctx.setTooltipForNextFrame(mc.font, hoverNonLive, mouseX, mouseY);
        }
        if (selHover != null) gridPopup(ctx, mc, selHover, mouseX, mouseY);
    }

    private static String renderSelector(GuiGraphicsExtractor ctx, Minecraft mc, int mouseX, int mouseY) {
        String live = liveKey();
        int sx = selX(), sy = selY();
        ctx.fill(sx - 3, sy - 3, sx + SEL_COLS * SEL_CELL + 3, sy + 3 * SEL_CELL + 3, 0xF01A1A1A);
        String hover = null;
        int box = SEL_CELL - 2; // slot face size
        for (int i = 0; i < 27; i++) {
            String key = selKey(i);
            boolean known = StorageCache.has(key);
            int num = i < 9 ? i + 1 : i - 8;
            int[] p = selIconPos(i);
            boolean isLive = key.equals(live);
            boolean hovered = mouseX >= p[0] && mouseX < p[0] + box && mouseY >= p[1] && mouseY < p[1] + box && known;
            int border = isLive ? (i < 9 ? 0xFF55FFFF : 0xFFFFAA00) : hovered ? 0xFFFFFFFF : 0xFF373737;
            ctx.fill(p[0] - 1, p[1] - 1, p[0] + box + 1, p[1] + box + 1, border);
            ctx.fill(p[0], p[1], p[0] + box, p[1] + box, hovered ? 0xFF6E6E6E : 0xFF565656);
            ItemStack icon = StorageCache.icon(key);
            if (icon == null || icon.isEmpty()) icon = new ItemStack(i < 9 ? Items.ENDER_CHEST : Items.PLAYER_HEAD);
            float off = (box - 16 * SEL_ICON) / 2f;
            var pose = ctx.pose();
            pose.pushMatrix();
            pose.translate(p[0] + off, p[1] + off);
            pose.scale(SEL_ICON, SEL_ICON);
            ctx.item(icon, 0, 0);
            pose.popMatrix();
            if (!known) ctx.fill(p[0], p[1], p[0] + box, p[1] + box, 0xA0000000);
            ctx.text(mc.font, "§f" + num, p[0] + box - (num < 10 ? 7 : 12), p[1] + box - 8, 0xFFFFFFFF);
            if (hovered) hover = key;
        }
        return hover;
    }

    private static void gridPopup(GuiGraphicsExtractor ctx, Minecraft mc, String key, int mx, int my) {
        Map<Integer, ItemStack> items = StorageCache.items(key);
        if (items == null) return;
        int size = StorageCache.size(key), cols = 9, rows = Math.max(1, (size + cols - 1) / cols), cell = 18;
        int w = cols * cell + 8, h = rows * cell + 8;
        int px = mx + 12, py = my - 12;
        if (px + w > mc.screen.width) px = mx - w - 12;
        if (py + h > mc.screen.height) py = mc.screen.height - h;
        if (py < 0) py = 0;
        ctx.fill(px, py, px + w, py + h, 0xF0202020);
        ctx.fill(px, py, px + w, py + 1, 0xFF7755FF);
        ctx.fill(px, py + h - 1, px + w, py + h, 0xFF7755FF);
        for (int s = 0; s < size; s++) {
            int gx = px + 4 + (s % cols) * cell, gy = py + 4 + (s / cols) * cell;
            ctx.fill(gx, gy, gx + 16, gy + 16, 0xFF555555);
        }
        for (Map.Entry<Integer, ItemStack> e : items.entrySet()) {
            int gx = px + 4 + (e.getKey() % cols) * cell, gy = py + 4 + (e.getKey() / cols) * cell;
            ctx.item(e.getValue(), gx, gy);
            ctx.itemDecorations(mc.font, e.getValue(), gx, gy);
        }
    }

    private static void drawButton(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, int w, String label) {
        ctx.fill(x, y, x + w, y + 14, 0xFF303030);
        ctx.fill(x, y, x + w, y + 1, 0xFF606060);
        ctx.fill(x, y + 13, x + w, y + 14, 0xFF000000);
        ctx.text(mc.font, label, x + 5, y + 3, 0xFFFFFFFF);
    }

    public static void renderEnableButton(GuiGraphicsExtractor ctx) {
        if (TeslaMapsConfig.get().customStorageOverlay) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        if (!strip(cs.getTitle().getString()).equals("Storage")) return;
        drawButton(ctx, mc, mc.screen.width - 110, 6, 102, "§aCustom Storage ▶");
    }

    public static boolean handleEnableClick(double mx, double my) {
        if (TeslaMapsConfig.get().customStorageOverlay) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return false;
        if (!strip(cs.getTitle().getString()).equals("Storage")) return false;
        int x = mc.screen.width - 110, y = 6;
        if (mx >= x && mx <= x + 102 && my >= y && my <= y + 14) {
            TeslaMapsConfig.get().customStorageOverlay = true;
            TeslaMapsConfig.save();
            return true;
        }
        return false;
    }

    public static boolean isOverSlot(Slot slot, double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return false;
        if (slot.container instanceof Inventory) {
            int[] p = invSlotPos(slot.getContainerSlot());
            float imx = (float) mx / INV_SCALE, imy = (float) my / INV_SCALE;
            return imx >= p[0] && imx < p[0] + 16 && imy >= p[1] && imy < p[1] + 16;
        }
        String live = liveKey();
        if (live == null) return false;
        int cell = slot.getContainerSlot() - 9;
        if (cell < 0) return false;
        List<Box> vis = visible();
        int sx = startX(cs.width), sy = startY();
        for (int i = 0; i < vis.size(); i++) {
            if (!vis.get(i).key.equals(live)) continue;
            int bx = sx + (i % COLS) * (BOX_W + GAP);
            int by = (int) (sy + (i / COLS) * (boxH() + GAP) - scroll);
            int gx = bx + 4 + (cell % GRID_COLS) * CELL, gy = by + topPad() + (cell / GRID_COLS) * CELL;
            float vmx = (float) mx / SCALE, vmy = (float) my / SCALE;
            return vmx >= gx && vmx < gx + 16 && vmy >= gy && vmy < gy + 16;
        }
        return false;
    }

    public static Slot slotAt(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || mc.player == null) return null;
        AbstractContainerMenu menu = mc.player.containerMenu;

        float imx = (float) mx / INV_SCALE, imy = (float) my / INV_SCALE;
        for (Slot s : menu.slots) {
            if (!(s.container instanceof Inventory)) continue;
            int[] p = invSlotPos(s.getContainerSlot());
            if (imx >= p[0] && imx < p[0] + 16 && imy >= p[1] && imy < p[1] + 16) return s;
        }
        if (my < HEADER_PX) return null;

        String live = liveKey();
        if (live == null) return null;
        float vmx = (float) mx / SCALE, vmy = (float) my / SCALE;
        int sx = startX(cs.width), sy = startY();
        List<Box> vis = visible();
        for (int i = 0; i < vis.size(); i++) {
            Box b = vis.get(i);
            if (!b.key.equals(live)) continue;
            int bx = sx + (i % COLS) * (BOX_W + GAP);
            int by = (int) (sy + (i / COLS) * (boxH() + GAP) - scroll);
            if (!(vmx >= bx && vmx <= bx + BOX_W && vmy >= by && vmy <= by + boxH())) continue;
            int gx0 = bx + 4, gy0 = by + topPad();
            int cx = (int) ((vmx - gx0) / CELL), cy = (int) ((vmy - gy0) / CELL);
            if (cx < 0 || cx >= GRID_COLS || cy < 0) return null;
            int target = cy * GRID_COLS + cx + 9;
            for (Slot s : menu.slots)
                if (!(s.container instanceof Inventory) && s.getContainerSlot() == target) return s;
            return null;
        }
        return null;
    }

    public static boolean handleClick(double mx, double my, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || mc.player == null) return false;

        int dbX = cs.width - 96;
        if (mx >= dbX && mx <= dbX + 88 && my >= 6 && my <= 20) {
            TeslaMapsConfig.get().customStorageOverlay = false;
            TeslaMapsConfig.save();
            return true;
        }
        for (int i = 0; i < 27; i++) { // left selector column
            String key = selKey(i);
            if (!StorageCache.has(key)) continue;
            int[] p = selIconPos(i);
            if (mx >= p[0] && mx < p[0] + SEL_CELL - 2 && my >= p[1] && my < p[1] + SEL_CELL - 2) {
                if (mc.getConnection() != null) mc.getConnection().sendCommand((i < 9 ? "ec " : "bp ") + (i < 9 ? i + 1 : i - 8));
                return true;
            }
        }

        int wallBottom = (int) ((mainY() - 13) * INV_SCALE) - 2;
        int trackX = cs.width - 13;
        if (button == 0 && mx >= trackX && my >= HEADER_PX && my <= wallBottom) { // scrollbar drag
            scrollDragging = true;
            searchFocused = false;
            return true;
        }
        if (inSearchBar(mx, my)) {
            if (button == 1) search = ""; else searchFocused = true;
            return true;
        }
        searchFocused = false;

        if (slotAt(mx, my) != null) return false;

        if (my < HEADER_PX) return true;
        float vmx = (float) mx / SCALE, vmy = (float) my / SCALE;
        int sx = startX(cs.width), sy = startY();
        List<Box> vis = visible();
        for (int i = 0; i < vis.size(); i++) {
            Box b = vis.get(i);
            int bx = sx + (i % COLS) * (BOX_W + GAP);
            int by = (int) (sy + (i / COLS) * (boxH() + GAP) - scroll);
            if (vmx >= bx && vmx <= bx + BOX_W && vmy >= by && vmy <= by + boxH()) {
                int liveGi = globalIndex(liveKey()), clickedGi = globalIndex(b.key);
                if (sameCategory(liveGi, clickedGi) && clickedGi == liveGi + 1) clickPageArrow("Next Page");
                else if (sameCategory(liveGi, clickedGi) && clickedGi == liveGi - 1) clickPageArrow("Previous Page");
                else if (mc.getConnection() != null) mc.getConnection().sendCommand((b.ender ? "ec " : "bp ") + b.num);
                return true;
            }
        }
        return true;
    }

    public static boolean keyPressed(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) return false;
        if (!searchFocused) {
            if (key == GLFW.GLFW_KEY_A) { navigateDir(false); return true; }
            if (key == GLFW.GLFW_KEY_D) { navigateDir(true); return true; }
            if (key == GLFW.GLFW_KEY_W) { navigateRow(-COLS); return true; }
            if (key == GLFW.GLFW_KEY_S) { navigateRow(COLS); return true; }
            return false;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) { if (!search.isEmpty()) search = search.substring(0, search.length() - 1); return true; }
        if (key == GLFW.GLFW_KEY_SPACE) { search += " "; return true; }
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) { search += (char) ('a' + (key - GLFW.GLFW_KEY_A)); return true; }
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) { search += (char) ('0' + (key - GLFW.GLFW_KEY_0)); return true; }
        return true;
    }

    private static void clickPageArrow(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory || s.getItem().isEmpty()) continue;
            if (strip(s.getItem().getHoverName().getString()).contains(name)) {
                mc.gameMode.handleContainerInput(menu.containerId, s.index, 0, ContainerInput.PICKUP, mc.player);
                return;
            }
        }
    }

    public static boolean mouseScrolled(double sy) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return false;
        int rows = (visible().size() + COLS - 1) / COLS;
        int wallBottom = (int) ((mainY() - 13) * INV_SCALE) - 2;
        float maxScroll = Math.max(0, rows * (boxH() + GAP) + startY() + 10 - wallBottom / SCALE);
        boolean ctrl = GLFW.glfwGetKey(mc.getWindow().handle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(mc.getWindow().handle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (float) (sy * (ctrl ? 90 : 20))));
        return true;
    }

    private static boolean inSearchBar(double x, double y) {
        int[] sb = sbRect();
        return x >= sb[0] && x <= sb[0] + sb[2] && y >= sb[1] && y <= sb[1] + sb[3];
    }

    private static boolean matches(ItemStack st, String q) {
        if (st.getHoverName().getString().replaceAll("(?i)§[0-9A-FK-OR]", "").toLowerCase().contains(q)) return true;
        ItemLore lore = st.get(DataComponents.LORE);
        if (lore != null) for (Component line : lore.lines())
            if (line.getString().replaceAll("(?i)§[0-9A-FK-OR]", "").toLowerCase().contains(q)) return true;
        return false;
    }

    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
    }
}
