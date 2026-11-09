package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** After clicking an item search hit, that item stays highlighted in every menu for a while. */
public class SearchHighlight {

    private static String id = "", name = null;
    private static long until = 0;
    private static final Map<ItemStack, Boolean> cache = new WeakHashMap<>();
    private static final Map<String, Boolean> pageCache = new java.util.HashMap<>();
    private static WeakReference<Screen> screen = new WeakReference<>(null);
    private static boolean storageHub, sackMenu;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SearchHighlight")
                .section("Inventory")
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Item Search Highlight");
                    b.toggle("Highlight Search Hit In Menus", () -> c.searchHighlight, v -> c.searchHighlight = v)
                            .desc("After clicking a hit in /tmap search the item is marked in every menu, storage icon and sack");
                    b.slider("  Duration (s)", 10f, 60f,
                            () -> (float) c.searchHighlightSeconds, v -> c.searchHighlightSeconds = Math.round(v), 0);
                    b.color("  Highlight Color", () -> c.colorSearchHighlight, v -> c.colorSearchHighlight = v);
                    b.button("  Clear Highlight", SearchHighlight::clear);
                });
    }

    public static void start(ItemStack stack, String itemName) {
        if (!cfg().searchHighlight || stack == null) return;
        id = ItemUtil.skyblockId(stack);
        name = StringUtil.stripTrim(itemName).toLowerCase(java.util.Locale.ROOT);
        until = System.currentTimeMillis() + Math.max(10, Math.min(60, cfg().searchHighlightSeconds)) * 1000L;
        cache.clear();
        pageCache.clear();
    }

    public static void clear() {
        name = null;
        cache.clear();
        pageCache.clear();
    }

    private static boolean active() {
        if (name == null) return false;
        if (System.currentTimeMillis() >= until) { clear(); return false; }
        return cfg().searchHighlight && cfg().section("Inventory");
    }

    private static boolean same(ItemStack st) {
        if (!id.isEmpty()) return id.equals(ItemUtil.skyblockId(st));
        return name.equals(StringUtil.stripTrim(st.getHoverName().getString()).toLowerCase(java.util.Locale.ROOT));
    }

    /** Storage page with a hit in its cached content. */
    public static boolean pageHit(String key) {
        if (key == null || !active()) return false;
        Boolean c = pageCache.get(key);
        if (c != null) return c;
        boolean hit = false;
        Map<Integer, ItemStack> items = StorageCache.items(key);
        if (items != null) for (ItemStack st : items.values()) if (!st.isEmpty() && same(st)) { hit = true; break; }
        pageCache.put(key, hit);
        return hit;
    }

    private static boolean compute(ItemStack st) {
        if (same(st)) return true;
        if (storageHub && pageHit(BackpackPreview.keyForIcon(st))) return true;
        if (sackMenu) {
            ItemLore lore = st.get(DataComponents.LORE);
            if (lore != null) for (Component line : lore.lines())
                if (StringUtil.strip(line.getString()).toLowerCase(java.util.Locale.ROOT).contains(name)) return true;
        }
        return false;
    }

    private static boolean hit(ItemStack st) {
        Boolean c = cache.get(st);
        if (c != null) return c;
        boolean h = compute(st);
        if (cache.size() > 2048) cache.clear();
        cache.put(st, h);
        return h;
    }

    public static void drawAt(GuiGraphicsExtractor ctx, int x, int y, ItemStack st) {
        if (st == null || st.isEmpty() || !active() || !hit(st)) return;
        ctx.fill(x, y, x + 16, y + 16, TeslaMapsConfig.parseColor(cfg().colorSearchHighlight));
    }

    /** Behind a container slot, from SlotBackgrounds. */
    public static void drawSlot(GuiGraphicsExtractor ctx, Slot slot) {
        if (!active()) return;
        Screen s = Minecraft.getInstance().gui.screen();
        if (s != screen.get()) {
            screen = new WeakReference<>(s);
            String t = s instanceof AbstractContainerScreen<?> ? StringUtil.stripTrim(s.getTitle().getString()) : "";
            storageHub = t.equals("Storage");
            sackMenu = t.contains("Sack");
            cache.clear();
        }
        drawAt(ctx, slot.x, slot.y, slot.getItem());
    }
}
