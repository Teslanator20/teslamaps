package com.teslamaps.features;

// NEU's InventoryStorageSelector, slimmed
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BackpackSlot {

    private static final int CELL = 18;

    private static boolean selected;
    private static int hotbarWhenSelected = -1;
    private static List<String> keys = List.of();
    private static String key;
    private static ItemStack icon;
    private static Map<Integer, ItemStack> items;
    private static int rows = 1;
    private static String name = "";
    private static int ticks;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static final Feature FEATURE = Feature.of("BackpackSlot").section("Inventory").when(() -> cfg().backpackSlot);

    public static void register() {
        FEATURE.tick(BackpackSlot::tick)
                .hudHiddenInInventory("backpackslot", BackpackSlot::render);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (selected && (mc.player == null || mc.player.getInventory().getSelectedSlot() != hotbarWhenSelected)) selected = false;
        if (++ticks % 10 != 0 && key != null) return;
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= 9; i++) if (StorageCache.has("epage_" + i)) out.add("epage_" + i);
        for (int i = 1; i <= 18; i++) if (StorageCache.has("backpack_" + i)) out.add("backpack_" + i);
        keys = out;
        if (out.isEmpty()) {
            key = null;
            items = null;
            icon = null;
            return;
        }
        int idx = Math.floorMod(cfg().backpackSlotIndex, out.size());
        key = out.get(idx);
        items = StorageCache.items(key);
        int max = StorageCache.size(key) - 1;
        if (items != null) for (int k : items.keySet()) if (k > max) max = k;
        rows = Math.max(1, (max + 9) / 9);
        name = label(key) + "  §7(← → switch, Enter opens)";
        icon = StorageCache.icon(key);
        if (icon == null) icon = new ItemStack(key.startsWith("epage_")
                ? net.minecraft.world.item.Items.ENDER_CHEST : net.minecraft.world.item.Items.CHEST);
    }

    private static String label(String k) {
        return k.startsWith("epage_") ? "Ender Chest " + k.substring(6) : "Backpack " + k.substring(9);
    }

    private static String command(String k) {
        return k.startsWith("epage_") ? "ec " + k.substring(6) : "bp " + k.substring(9);
    }

    private static void select(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        selected = on;
        hotbarWhenSelected = mc.player.getInventory().getSelectedSlot();
    }

    public static boolean onScroll(double dy) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null || mc.player == null || dy == 0 || !cfg().backpackSlotScroll || !FEATURE.active()) return false;
        var inv = mc.player.getInventory();
        if (selected) {
            selected = false;
            inv.setSelectedSlot(dy < 0 ? 0 : 8);
            return true;
        }
        int cur = inv.getSelectedSlot();
        boolean left = cfg().backpackSlotLeft;
        if ((!left && cur == 8 && dy < 0) || (left && cur == 0 && dy > 0)) {
            select(true);
            return true;
        }
        return false;
    }

    public static boolean handleKey(long window, int action, int k) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null || mc.player == null || !FEATURE.active()) return false;
        if (mc.getWindow() == null || window != mc.getWindow().handle()) return false;
        TeslaMapsConfig c = cfg();
        if (c.backpackSlotKey != -1 && k == c.backpackSlotKey) {
            if (action == com.mojang.blaze3d.platform.InputConstants.PRESS) select(!selected);
            return true;
        }
        if (!selected) return false;
        if (k == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT || k == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT) {
            if (action != com.mojang.blaze3d.platform.InputConstants.RELEASE && !keys.isEmpty()) {
                c.backpackSlotIndex = Math.floorMod(c.backpackSlotIndex + (k == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT ? -1 : 1), keys.size());
                key = null;
                tick();
            }
            return true;
        }
        if (k == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || k == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
            if (action == com.mojang.blaze3d.platform.InputConstants.PRESS && key != null && mc.getConnection() != null) {
                mc.getConnection().sendCommand(command(key));
                selected = false;
                TeslaMapsConfig.save();
            }
            return true;
        }
        return false;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        TeslaMapsConfig c = cfg();
        int w = g.guiWidth(), h = g.guiHeight();
        int x = c.backpackSlotLeft ? w / 2 - 91 - 29 - (mc.player.getOffhandItem().isEmpty() ? 0 : 29) : w / 2 + 91 + 6;
        int y = h - 22;
        g.fill(x, y, x + 22, y + 22, 0xA0000000);
        int border = selected ? 0xFFFFFFFF : 0xFF555555;
        g.fill(x, y, x + 22, y + 1, border);
        g.fill(x, y + 21, x + 22, y + 22, border);
        g.fill(x, y, x + 1, y + 22, border);
        g.fill(x + 21, y, x + 22, y + 22, border);
        if (icon != null) g.item(icon, x + 3, y + 3);
        if (key == null) {
            g.centeredText(mc.font, "?", x + 11, y + 7, 0xFFAAAAAA);
            return;
        }
        if (!selected) return;

        if (!c.backpackSlotPreview || items == null) {
            g.centeredText(mc.font, name, w / 2, y - 12, 0xFFFFFFFF);
            return;
        }
        int pw = 9 * CELL + 6, ph = rows * CELL + 6;
        int px = w / 2 - pw / 2, py = y - 30 - ph;
        int alpha = Math.round(Math.max(0.1f, Math.min(1f, c.backpackSlotOpacity)) * 255) << 24;
        g.fill(px, py, px + pw, py + ph, alpha | 0x101010);
        g.centeredText(mc.font, name, w / 2, py - 11, 0xFFFFFFFF);
        for (Map.Entry<Integer, ItemStack> e : items.entrySet()) {
            ItemStack s = e.getValue();
            if (s == null || s.isEmpty()) continue;
            int i = e.getKey();
            int ix = px + 3 + (i % 9) * CELL + 1, iy = py + 3 + (i / 9) * CELL + 1;
            g.item(s, ix, iy);
            g.itemDecorations(mc.font, s, ix, iy);
        }
    }
}
