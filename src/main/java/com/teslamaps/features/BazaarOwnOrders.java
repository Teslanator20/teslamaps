package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import java.util.IdentityHashMap;
import java.util.regex.Pattern;
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

    private static final Pattern NAME = Pattern.compile("^(BUY|SELL) (.+)$");

    public static void render(GuiGraphicsExtractor ctx) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.bazaarOwnOrders && !config.bazaarOnlyMyOrders) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || mc.player == null) return;

        String title = com.teslamaps.utils.StringUtil.strip(cs.getTitle().getString());
        if (!title.contains("Bazaar") && !title.contains("➜") && !title.contains("Orders")) return;

        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        String ownName = mc.player.getName().getString();
        boolean onlyMine = config.bazaarOwnOrders && config.bazaarOnlyMyOrders && title.equals(COOP_TITLE);

        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;

            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            if (onlyMine && isForeignOrder(item, ownName)) {
                ctx.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
                continue;
            }
            if (config.bazaarOwnOrders && isOwnOrder(item, ownName)) {
                box(ctx, sx, sy, 0x6655FF55, 0xFF55FF55);
            }
        }
    }

    private static final String COOP_TITLE = "Co-op Bazaar Orders";

    private static boolean foreignInScreen(AbstractContainerScreen<?> screen, Slot slot) {
        if (slot == null || !TeslaMapsConfig.get().bazaarOnlyMyOrders || !TeslaMapsConfig.get().bazaarOwnOrders) return false;
        if (slot.container instanceof net.minecraft.world.entity.player.Inventory || slot.getItem().isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (!com.teslamaps.utils.StringUtil.stripTrim(screen.getTitle().getString()).equals(COOP_TITLE)) return false;
        return isForeignOrder(slot.getItem(), mc.player.getName().getString());
    }

    /** Only My Orders: clicks on a co-op member's order do nothing. */
    public static boolean blockClick(AbstractContainerScreen<?> screen, Slot slot) {
        return foreignInScreen(screen, slot);
    }

    public static boolean hideTooltip(AbstractContainerScreen<?> screen, Slot hovered) {
        return foreignInScreen(screen, hovered);
    }

    /** A BUY/SELL order whose "By:" line names someone else. */
    private static boolean isForeignOrder(ItemStack item, String ownName) {
        if (!NAME.matcher(com.teslamaps.utils.StringUtil.stripTrim(item.getHoverName().getString())).matches()) return false;
        ItemLore lore = item.get(DataComponents.LORE);
        if (lore == null) return false;
        for (Component cmp : lore.lines()) {
            if (com.teslamaps.utils.StringUtil.stripTrim(cmp.getString()).startsWith("By: ")) return !isOwnOrder(item, ownName);
        }
        return false;
    }

    private static void box(GuiGraphicsExtractor ctx, int sx, int sy, int fill, int outline) {
        ctx.fill(sx, sy, sx + 16, sy + 16, fill);
        ctx.fill(sx, sy, sx + 16, sy + 1, outline);
        ctx.fill(sx, sy + 15, sx + 16, sy + 16, outline);
        ctx.fill(sx, sy, sx + 1, sy + 16, outline);
        ctx.fill(sx + 15, sy, sx + 16, sy + 16, outline);
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
