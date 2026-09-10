package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import java.util.IdentityHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

// Bazaar order books list orders with a "By: PlayerName" lore line - highlight your own in green
public class BazaarOwnOrders {

    private static final IdentityHashMap<ItemStack, Boolean> cache = new IdentityHashMap<>();
    private static final IdentityHashMap<ItemStack, Boolean> filledCache = new IdentityHashMap<>();

    public static void render(GuiGraphicsExtractor ctx) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.bazaarOwnOrders && !config.bazaarOrderFilled) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || mc.player == null) return;

        String title = com.teslamaps.utils.StringUtil.strip(cs.getTitle().getString());
        if (!title.contains("Bazaar") && !title.contains("➜") && !title.contains("Orders")) return;

        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        String ownName = mc.player.getName().getString();

        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;

            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            if (config.bazaarOwnOrders && isOwnOrder(item, ownName)) {
                ctx.fill(sx, sy, sx + 16, sy + 16, 0x6655FF55);
                ctx.fill(sx, sy, sx + 16, sy + 1, 0xFF55FF55);
                ctx.fill(sx, sy + 15, sx + 16, sy + 16, 0xFF55FF55);
                ctx.fill(sx, sy, sx + 1, sy + 16, 0xFF55FF55);
                ctx.fill(sx + 15, sy, sx + 16, sy + 16, 0xFF55FF55);
            }
            if (config.bazaarOrderFilled && isFilled(item)) {
                ctx.text(mc.font, "✔", sx + 1, sy, 0xFF55FF55);
            }
        }
    }

    private static boolean isFilled(ItemStack item) {
        Boolean cached = filledCache.get(item);
        if (cached != null) return cached;
        if (filledCache.size() > 512) filledCache.clear();

        boolean filled = false;
        ItemLore lore = item.get(DataComponents.LORE);
        if (lore != null) {
            for (Component cmp : lore.lines()) {
                String l = com.teslamaps.utils.StringUtil.stripTrim(cmp.getString());
                if (l.startsWith("Filled:") && l.endsWith("100%!")) {
                    filled = true;
                    break;
                }
            }
        }
        filledCache.put(item, filled);
        return filled;
    }

    private static boolean isOwnOrder(ItemStack item, String ownName) {
        Boolean cached = cache.get(item);
        if (cached != null) return cached;
        if (cache.size() > 512) cache.clear();

        boolean own = false;
        ItemLore lore = item.get(DataComponents.LORE);
        if (lore != null) {
            for (Component cmp : lore.lines()) {
                String l = com.teslamaps.utils.StringUtil.stripTrim(cmp.getString());
                if (!l.startsWith("By: ")) continue;
                String name = l.substring(4).trim();
                int sp = name.lastIndexOf(' ');
                if (sp >= 0) name = name.substring(sp + 1); // drop the "[MVP+]" rank prefix
                if (name.equalsIgnoreCase(ownName) || name.equalsIgnoreCase("You")) own = true;
                break;
            }
        }
        cache.put(item, own);
        return own;
    }
}
