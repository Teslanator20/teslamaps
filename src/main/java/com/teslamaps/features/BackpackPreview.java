package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    private static String hoveredKey() {
        if (!TeslaMapsConfig.get().backpackPreview) return null;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return null;
        Slot hovered = ((HandledScreenAccessor) cs).getFocusedSlot();
        if (hovered == null || hovered.getItem().isEmpty()) return null;
        return keyForStack(hovered.getItem());
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

    private static ClientTooltipComponent pendingGrid = null;
    private static int pendingIndex = -1;

    // called with the raw item tooltip lines; drops the "Obtained:" line and
    // reserves a slot for the item grid
    public static List<Component> prepareTooltip(List<Component> lines) {
        pendingGrid = null;
        pendingIndex = -1;

        String key = hoveredKey();
        if (key == null) return lines;
        Map<Integer, ItemStack> items = StorageCache.items(key);
        if (items == null || items.isEmpty()) return lines;

        int size = StorageCache.size(key);
        if (size <= 0) for (int s : items.keySet()) size = Math.max(size, s + 1);

        List<Component> out = new ArrayList<>();
        for (Component c : lines) {
            if (strip(c.getString()).startsWith("Obtained:")) continue;
            out.add(c);
        }
        while (!out.isEmpty() && strip(out.get(out.size() - 1).getString()).isEmpty()) out.remove(out.size() - 1);

        int at = -1;
        for (int i = 1; i < out.size(); i++) {
            if (strip(out.get(i).getString()).isEmpty()) { at = i; break; }
        }
        if (at < 0) { out.add(Component.empty()); at = out.size() - 1; }

        pendingIndex = at;
        pendingGrid = new Grid(items, size);
        return out;
    }

    public static void applyGrid(List<ClientTooltipComponent> components) {
        if (pendingGrid == null) return;
        if (pendingIndex < components.size()) components.set(pendingIndex, pendingGrid);
        else components.add(pendingGrid);
        pendingGrid = null;
        pendingIndex = -1;
    }

    private record Grid(Map<Integer, ItemStack> items, int size) implements ClientTooltipComponent {
        private static final int CELL = 18, GAP = 2, PAD = 3, COLS = 9;
        private static final int MARGIN_TOP = 4, MARGIN_BOTTOM = 8;

        private int rows() { return Math.max(1, (size + COLS - 1) / COLS); }

        private int boxHeight() { return PAD * 2 + rows() * CELL + (rows() - 1) * GAP; }

        @Override
        public int getWidth(Font font) { return PAD * 2 + COLS * CELL + (COLS - 1) * GAP; }

        @Override
        public int getHeight(Font font) { return MARGIN_TOP + boxHeight() + MARGIN_BOTTOM; }

        @Override
        public void extractImage(Font font, int x, int y, int tw, int th, GuiGraphicsExtractor ctx) {
            int rows = rows();
            int top = y + MARGIN_TOP;
            ctx.fill(x, top, x + getWidth(font), top + boxHeight(), 0xFF777777);
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < COLS; c++) {
                    int cx = x + PAD + c * (CELL + GAP);
                    int cy = top + PAD + r * (CELL + GAP);
                    ctx.fill(cx, cy, cx + CELL, cy + CELL, 0xFFA0A0A0);
                }
            }
            for (Map.Entry<Integer, ItemStack> e : items.entrySet()) {
                int s = e.getKey();
                if (s < 0 || s >= rows * COLS) continue;
                int cx = x + PAD + (s % COLS) * (CELL + GAP) + 1;
                int cy = top + PAD + (s / COLS) * (CELL + GAP) + 1;
                ctx.item(e.getValue(), cx, cy);
                ctx.itemDecorations(font, e.getValue(), cx, cy);
                EnchantAbbrev.draw(ctx, e.getValue(), cx, cy);
            }
        }
    }

    private static String strip(String s) {
        return com.teslamaps.utils.StringUtil.stripTrim(s);
    }
}
