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

    public static final String[] STYLES = {"Compact", "Chest"};
    private static final String CAKE = "cakebag";
    private static int openTint = 0;

    public static void tick() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.backpackPreview && !cfg.customStorageOverlay) { flush(); return; }
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) { flush(); return; }

        String title = strip(cs.getTitle().getString());
        openTint = 0;
        if (title.equals("New Year Cake Bag")) {
            if (cfg.backpackPreview && cfg.backpackPreviewCakeBag) captureCakeBag(cs);
            return;
        }
        if (title.equals("Storage")) {
            for (Slot slot : cs.getMenu().slots) {
                if (slot.container instanceof Inventory || slot.getItem().isEmpty()) continue;
                String k = keyForStack(slot.getItem());
                if (k != null) StorageCache.putIcon(k, StorageCache.encode(slot.getItem()));
            }
            if (cfg.storageShowUnopened) scanUnlocked(cs);
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
        if (cfg.backpackPreviewColors && key.startsWith("backpack_")) openTint = dyeColor(StorageCache.icon(key));
    }

    /** Waits for all 27 hub icons, then records which pages exist (not "Empty Backpack Slot", not locked). */
    private static void scanUnlocked(AbstractContainerScreen<?> cs) {
        ItemStack[] ec = new ItemStack[9], bp = new ItemStack[18];
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            int i = slot.getContainerSlot();
            if (i >= 9 && i <= 17) ec[i - 9] = slot.getItem();
            else if (i >= 27 && i <= 44) bp[i - 27] = slot.getItem();
        }
        for (ItemStack st : ec) if (st == null || st.isEmpty()) return;
        for (ItemStack st : bp) if (st == null || st.isEmpty()) return;
        for (int i = 0; i < 9; i++)
            StorageCache.setUnlocked("epage_" + (i + 1), strip(ec[i].getHoverName().getString()).startsWith("Ender Chest Page"));
        for (int i = 0; i < 18; i++) {
            String n = strip(bp[i].getHoverName().getString());
            StorageCache.setUnlocked("backpack_" + (i + 1), n.startsWith("Backpack Slot"));
        }
    }

    private static void captureCakeBag(AbstractContainerScreen<?> cs) {
        LinkedHashMap<Integer, String> slots = new LinkedHashMap<>();
        int n = 0;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty() || !"NEW_YEAR_CAKE".equals(com.teslamaps.utils.ItemUtil.skyblockId(st))) continue;
            String snbt = StorageCache.encode(st);
            if (snbt != null) slots.put(n++, snbt);
        }
        if (n == 0) return;
        StorageCache.put(CAKE, Math.max(9, (n + 8) / 9 * 9), slots);
        lastKey = CAKE;
    }

    private static final java.util.Map<ItemStack, Integer> dyeCache = new java.util.WeakHashMap<>();

    /** Hypixel's backpack_color as RGB, 0 when undyed. */
    public static int dyeColor(ItemStack st) {
        if (st == null || st.isEmpty()) return 0;
        Integer c = dyeCache.get(st);
        if (c != null) return c;
        int rgb = 0;
        net.minecraft.world.item.component.CustomData d = st.get(DataComponents.CUSTOM_DATA);
        String name = d == null ? "" : d.copyTag().getString("backpack_color").orElse("");
        if (!name.isEmpty()) {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            net.minecraft.world.item.DyeColor dye = net.minecraft.world.item.DyeColor.byName(lower, null);
            if (dye != null) rgb = dye.getTextureDiffuseColor() & 0xFFFFFF;
            else {
                net.minecraft.ChatFormatting f = net.minecraft.ChatFormatting.getByName(lower);
                if (f != null && f.getColor() != null) rgb = f.getColor();
            }
        }
        dyeCache.put(st, rgb);
        return rgb;
    }

    private static int tintFor(String key) {
        if (!TeslaMapsConfig.get().backpackPreviewColors || key == null || !key.startsWith("backpack_")) return 0;
        return dyeColor(StorageCache.icon(key));
    }

    /** Behind each slot of an opened, dyed backpack. */
    public static void drawSlotTint(GuiGraphicsExtractor ctx, Slot slot) {
        if (openTint == 0 || slot.container instanceof Inventory || slot.getContainerSlot() < 9) return;
        if (!TeslaMapsConfig.get().backpackPreviewColors) return;
        ctx.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y + 17, 0x60000000 | openTint);
    }

    private static void flush() {
        openTint = 0;
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

    public static String keyForIcon(ItemStack stack) { return keyForStack(stack); }

    private static String keyForStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if ("NEW_YEAR_CAKE_BAG".equals(com.teslamaps.utils.ItemUtil.skyblockId(stack)))
            return TeslaMapsConfig.get().backpackPreviewCakeBag ? CAKE : null;
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
        Map<Integer, ItemStack> items = key == null ? null : StorageCache.items(key);
        int size;
        if (items != null && !items.isEmpty()) {
            size = StorageCache.size(key);
        } else {
            key = null;
            items = dataItems();
            if (items == null) return lines;
            size = dataSize;
        }
        if (size <= 0) for (int s : items.keySet()) size = Math.max(size, s + 1);

        List<Component> out = new ArrayList<>();
        for (Component c : lines) {
            if (strip(c.getString()).startsWith("Obtained:")) continue;
            out.add(c);
        }
        while (!out.isEmpty() && strip(out.get(out.size() - 1).getString()).isEmpty()) out.remove(out.size() - 1);
        String custom = key == null ? null : TeslaMapsConfig.get().storagePageNames.get(key);
        if (custom != null && !custom.isEmpty() && !out.isEmpty()) out.add(1, Component.literal("§7» " + custom.replace('&', '§')));

        int at = -1;
        for (int i = 1; i < out.size(); i++) {
            if (strip(out.get(i).getString()).isEmpty()) { at = i; break; }
        }
        if (at < 0) { out.add(Component.empty()); at = out.size() - 1; }

        pendingIndex = at;
        pendingGrid = new Grid(items, size, tintFor(key), "Chest".equals(TeslaMapsConfig.get().backpackPreviewStyle));
        return out;
    }

    // SkyblockAddons' ContainerPreviewManager / Skysoft's StoragePreviews: contents packed into the item
    private static ItemStack dataStack = null;
    private static List<com.teslamaps.profileviewer.data.InventoryDecoder.Slot> dataSlots = List.of();
    private static int dataSize = 0;

    private static Map<Integer, ItemStack> dataItems() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.itemDataPreview || !c.backpackPreview) return null;
        Minecraft mc = Minecraft.getInstance();
        if (c.backpackPreviewShift && !mc.hasShiftDown()) return null;
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return null;
        if (!c.itemDataPreviewInAh && strip(cs.getTitle().getString()).contains("Auction")) return null;
        Slot hovered = ((HandledScreenAccessor) cs).getFocusedSlot();
        if (hovered == null || hovered.getItem().isEmpty()) return null;
        ItemStack stack = hovered.getItem();
        if (stack != dataStack) {
            dataStack = stack;
            dataSlots = decodeData(stack, c);
            dataSize = dataSlots.isEmpty() ? 0 : ((dataSlots.size() + 8) / 9) * 9;
        }
        if (dataSlots.isEmpty()) return null;
        Map<Integer, ItemStack> out = new LinkedHashMap<>();
        for (int i = 0; i < dataSlots.size(); i++) {
            com.teslamaps.profileviewer.data.InventoryDecoder.Slot s = dataSlots.get(i);
            if (s.isEmpty()) continue;
            ItemStack st = com.teslamaps.profileviewer.screen.ItemGrid.stackFor(s);
            if (!st.isEmpty()) out.put(i, st);
        }
        return out.isEmpty() ? null : out;
    }

    private static List<com.teslamaps.profileviewer.data.InventoryDecoder.Slot> decodeData(ItemStack stack, TeslaMapsConfig c) {
        net.minecraft.world.item.component.CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return List.of();
        net.minecraft.nbt.CompoundTag tag = data.copyTag();
        String key = null;
        if (tag.contains("new_year_cake_bag_data")) {
            if (c.itemDataPreviewCakeBag) key = "new_year_cake_bag_data";
        } else if (tag.contains("builder's_wand_data")) {
            key = "builder's_wand_data";
        } else {
            for (String k : tag.keySet()) if (k.endsWith("backpack_data")) { key = k; break; }
        }
        if (key == null) return List.of();
        byte[] raw = tag.getByteArray(key).orElse(null);
        if (raw == null || raw.length > 2_000_000) return List.of();
        List<com.teslamaps.profileviewer.data.InventoryDecoder.Slot> slots =
                com.teslamaps.profileviewer.data.InventoryDecoder.decodeBytes(raw);
        return slots.size() > 54 ? slots.subList(0, 54) : slots;
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
        int tint = tintFor(frozen);
        if (tint != 0) ctx.fill(x, y, x + r[2], y + r[3], 0x50000000 | tint);
        ctx.fill(x, y, x + r[2], y + 1, 0xFF7755FF);
        ctx.fill(x, y + r[3] - 1, x + r[2], y + r[3], 0xFF7755FF);
        ctx.text(mc.font, "§d" + StorageOverlay.pageName(frozen, label(frozen)) + " §8(frozen)", x + F_PAD, y + F_PAD, 0xFFFFFFFF);
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
        if (key.equals(CAKE)) return "New Year Cake Bag";
        return key;
    }

    private record Grid(Map<Integer, ItemStack> items, int size, int tint, boolean chest) implements ClientTooltipComponent {
        private static final int CELL = 18, PAD = 3, COLS = 9;
        private static final int MARGIN_TOP = 4, MARGIN_BOTTOM = 8;

        private int gap() { return chest ? 0 : 2; }

        private int rows() { return Math.max(1, (size + COLS - 1) / COLS); }

        private int boxHeight() { return PAD * 2 + rows() * CELL + (rows() - 1) * gap(); }

        @Override
        public int getWidth(Font font) { return PAD * 2 + COLS * CELL + (COLS - 1) * gap(); }

        @Override
        public int getHeight(Font font) { return MARGIN_TOP + boxHeight() + MARGIN_BOTTOM; }

        @Override
        public void extractImage(Font font, int x, int y, int tw, int th, GuiGraphicsExtractor ctx) {
            int rows = rows();
            int top = y + MARGIN_TOP;
            int w = getWidth(font), h = boxHeight(), gap = gap();
            if (chest) {
                ctx.fill(x, top, x + w, top + h, 0xFFC6C6C6);
                ctx.fill(x, top, x + w, top + 1, 0xFFFFFFFF);
                ctx.fill(x, top, x + 1, top + h, 0xFFFFFFFF);
                ctx.fill(x, top + h - 1, x + w, top + h, 0xFF555555);
                ctx.fill(x + w - 1, top, x + w, top + h, 0xFF555555);
            } else {
                ctx.fill(x, top, x + w, top + h, 0xFF777777);
            }
            if (tint != 0) ctx.fill(x, top, x + w, top + h, 0x70000000 | tint);
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < COLS; c++) {
                    int cx = x + PAD + c * (CELL + gap);
                    int cy = top + PAD + r * (CELL + gap);
                    if (chest) {
                        ctx.fill(cx, cy, cx + CELL, cy + CELL, 0xFFFFFFFF);
                        ctx.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0xFF373737);
                        ctx.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, 0xFF8B8B8B);
                    } else {
                        ctx.fill(cx, cy, cx + CELL, cy + CELL, 0xFFA0A0A0);
                    }
                }
            }
            for (Map.Entry<Integer, ItemStack> e : items.entrySet()) {
                int s = e.getKey();
                if (s < 0 || s >= rows * COLS) continue;
                int cx = x + PAD + (s % COLS) * (CELL + gap) + 1;
                int cy = top + PAD + (s / COLS) * (CELL + gap) + 1;
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
