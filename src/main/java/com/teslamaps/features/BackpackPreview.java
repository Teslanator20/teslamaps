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
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.backpackPreview) return null;
        Minecraft mc = Minecraft.getInstance();
        if (c.backpackPreviewShift && !mc.hasShiftDown()) return null;
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
        if (frozen != null) return lines;

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

    private static String frozen = null;
    private static int frozenX, frozenY;
    private static java.lang.ref.WeakReference<net.minecraft.client.gui.screens.Screen> frozenScreen = new java.lang.ref.WeakReference<>(null);
    private static final int F_CELL = 18, F_PAD = 4, F_TITLE = 11;

    /** Freeze key over a backpack/ender page: pins its preview so single items can be hovered. */
    public static boolean onKey(int key) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.backpackPreview || c.backpackPreviewFreezeKey == -1 || key != c.backpackPreviewFreezeKey) return false;
        if (frozen != null) { frozen = null; return true; }
        String k = hoveredKey();
        if (k == null) return false;
        Map<Integer, ItemStack> items = StorageCache.items(k);
        if (items == null || items.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        frozen = k;
        frozenScreen = new java.lang.ref.WeakReference<>(mc.screen);
        double sx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double sy = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        frozenX = (int) sx + 12;
        frozenY = (int) sy - 12;
        return true;
    }

    private static int frozenSize() {
        int size = StorageCache.size(frozen);
        if (size <= 0) {
            Map<Integer, ItemStack> items = StorageCache.items(frozen);
            if (items != null) for (int s : items.keySet()) size = Math.max(size, s + 1);
        }
        return Math.max(9, size);
    }

    private static int[] frozenRect(int sw, int sh) {
        int rows = (frozenSize() + 8) / 9;
        int w = F_PAD * 2 + 9 * F_CELL, h = F_PAD * 2 + F_TITLE + rows * F_CELL;
        int x = frozenX, y = frozenY;
        if (x + w > sw) x = sw - w;
        if (y + h > sh) y = sh - h;
        return new int[]{Math.max(0, x), Math.max(0, y), w, h};
    }

    private static boolean frozenLive() {
        if (frozen == null) return false;
        if (Minecraft.getInstance().screen != frozenScreen.get() || !TeslaMapsConfig.get().backpackPreview) {
            frozen = null;
            return false;
        }
        return true;
    }

    public static void renderFrozen(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        if (!frozenLive()) return;
        Minecraft mc = Minecraft.getInstance();
        Map<Integer, ItemStack> items = StorageCache.items(frozen);
        if (items == null) { frozen = null; return; }
        int[] r = frozenRect(mc.screen.width, mc.screen.height);
        int x = r[0], y = r[1];
        ctx.fill(x, y, x + r[2], y + r[3], 0xF0100010);
        ctx.fill(x, y, x + r[2], y + 1, 0xFF7755FF);
        ctx.fill(x, y + r[3] - 1, x + r[2], y + r[3], 0xFF7755FF);
        ctx.text(mc.font, "§d" + label(frozen) + " §8(frozen)", x + F_PAD, y + F_PAD, 0xFFFFFFFF);
        int gx0 = x + F_PAD, gy0 = y + F_PAD + F_TITLE;
        int size = frozenSize();
        for (int s = 0; s < size; s++) {
            int gx = gx0 + (s % 9) * F_CELL, gy = gy0 + (s / 9) * F_CELL;
            ctx.fill(gx, gy, gx + 16, gy + 16, 0xFF3A3A3A);
        }
        ItemStack hover = null;
        for (Map.Entry<Integer, ItemStack> e : items.entrySet()) {
            int s = e.getKey();
            if (s < 0 || s >= size) continue;
            int gx = gx0 + (s % 9) * F_CELL, gy = gy0 + (s / 9) * F_CELL;
            ItemStack st = e.getValue();
            RarityBackground.draw(ctx, gx, gy, st);
            ctx.item(st, gx, gy);
            ctx.itemDecorations(mc.font, st, gx, gy);
            EnchantAbbrev.draw(ctx, st, gx, gy);
            if (mouseX >= gx && mouseX < gx + 16 && mouseY >= gy && mouseY < gy + 16) {
                ctx.fill(gx, gy, gx + 16, gy + 16, 0x60FFFFFF);
                hover = st;
            }
        }
        if (hover != null && !hover.isEmpty()) ctx.setTooltipForNextFrame(mc.font, hover, mouseX, mouseY);
    }

    /** Clicks on the frozen panel never reach the slots below it. */
    public static boolean handleClick(double mx, double my, int button) {
        if (!frozenLive()) return false;
        Minecraft mc = Minecraft.getInstance();
        int[] r = frozenRect(mc.screen.width, mc.screen.height);
        boolean inside = mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
        if (!inside) return false;
        if (button == 1) frozen = null;
        return true;
    }

    private static String label(String key) {
        if (key.startsWith("epage_")) return "Ender Chest " + key.substring(6);
        if (key.startsWith("backpack_")) return "Backpack " + key.substring(9);
        return key;
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
