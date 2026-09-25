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

// Highlights finished commissions in the Commissions menu
public class CommissionHighlight {

    private static final IdentityHashMap<ItemStack, Boolean> cache = new IdentityHashMap<>();

    public static void render(GuiGraphicsExtractor ctx) {
        if (!TeslaMapsConfig.get().commissionHighlight) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        if (!cs.getTitle().getString().contains("Commissions")) return;

        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int col = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().colorCommissionDone) & 0x00FFFFFF;

        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            if (!isDone(item)) continue;

            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            ctx.fill(sx, sy, sx + 16, sy + 16, 0x66000000 | col);
            ctx.fill(sx, sy, sx + 16, sy + 1, 0xFF000000 | col);
            ctx.fill(sx, sy + 15, sx + 16, sy + 16, 0xFF000000 | col);
            ctx.fill(sx, sy, sx + 1, sy + 16, 0xFF000000 | col);
            ctx.fill(sx + 15, sy, sx + 16, sy + 16, 0xFF000000 | col);
        }
    }

    private static final IdentityHashMap<ItemStack, ItemStack> icons = new IdentityHashMap<>();
    private static Object lastScreen;
    private static boolean inMenu;

    /** Written book at 0%, knowledge book when claimable, glint off. SkyOcean's CommissionHighlighter. */
    public static ItemStack icon(Slot slot, ItemStack stack) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.commissionStatusIcons || !c.commissionHighlight || !stack.is(net.minecraft.world.item.Items.WRITABLE_BOOK)) return stack;
        if (slot.container instanceof net.minecraft.world.entity.player.Inventory) return stack;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != lastScreen) {
            lastScreen = mc.screen;
            inMenu = mc.screen instanceof AbstractContainerScreen<?> cs && cs.getTitle().getString().contains("Commissions")
                    && c.section("Inventory");
        }
        if (!inMenu) return stack;
        ItemStack cached = icons.get(stack);
        if (cached != null) return cached;
        if (icons.size() > 256) icons.clear();
        String last = "";
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            last = com.teslamaps.utils.StringUtil.stripTrim(lore.lines().get(lore.lines().size() - 1).getString());
        }
        net.minecraft.world.item.Item item = last.startsWith("Click to claim") ? net.minecraft.world.item.Items.KNOWLEDGE_BOOK
                : last.equals("0%") || last.endsWith(" 0%") ? net.minecraft.world.item.Items.WRITTEN_BOOK
                : net.minecraft.world.item.Items.WRITABLE_BOOK;
        ItemStack out = stack.transmuteCopy(item);
        out.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        icons.put(stack, out);
        return out;
    }

    private static boolean isDone(ItemStack item) {
        Boolean cached = cache.get(item);
        if (cached != null) return cached;
        if (cache.size() > 512) cache.clear();

        boolean done = false;
        ItemLore lore = item.get(DataComponents.LORE);
        if (lore != null) {
            for (Component cmp : lore.lines()) {
                String l = cmp.getString().replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
                if (l.isEmpty()) continue;
                if (l.contains("COMPLETED") || l.startsWith("Click to claim") || l.endsWith("100%")) {
                    done = true;
                    break;
                }
            }
        }
        cache.put(item, done);
        return done;
    }
}
