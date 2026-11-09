package com.teslamaps.features;

// Ported from SkyOcean's ItemHighlightKeybind / ItemSearch (search on hover)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ItemHighlight {

    private static final long DURATION_MS = 30_000;
    private static String id = "", name = "";
    private static long until = 0;
    private static ItemStack[] cachedStacks = new ItemStack[0];
    private static boolean[] cachedMatch = new boolean[0];

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static boolean onKey(int key, Slot hovered) {
        if (key < 0) return false;
        TeslaMapsConfig c = cfg();
        if (c.itemHighlightKey != -1 && key == c.itemHighlightKey) {
            if (hovered == null || hovered.getItem().isEmpty()) {
                until = 0;
                return true;
            }
            set(hovered.getItem());
            return true;
        }
        if (c.itemSearchFromHover && c.itemSearchKey != -1 && key == c.itemSearchKey
                && hovered != null && !hovered.getItem().isEmpty()) {
            String q = StringUtil.strip(hovered.getItem().getHoverName().getString()).trim();
            com.teslamaps.screen.ItemSearchScreen s = new com.teslamaps.screen.ItemSearchScreen(q);
            s.swallowNextChar = true;
            Minecraft.getInstance().gui.setScreen(s);
            return true;
        }
        return false;
    }

    private static void set(ItemStack stack) {
        String newId = ItemUtil.skyblockId(stack);
        String newName = StringUtil.strip(stack.getHoverName().getString()).trim();
        if (until > System.currentTimeMillis() && newId.equals(id) && newName.equals(name)) {
            until = 0;
            return;
        }
        id = newId;
        name = newName;
        until = System.currentTimeMillis() + DURATION_MS;
        cachedStacks = new ItemStack[0];
        for (ItemIndex.Hit hit : ItemIndex.search(name, ItemIndex.CHESTS)) {
            if (hit.pos() != null) SearchWaypoint.add(hit.pos(), name);
        }
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (until == 0) return;
        if (System.currentTimeMillis() > until) { until = 0; return; }
        if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int left = acc.getX(), top = acc.getY();
        List<Slot> slots = cs.getMenu().slots;
        if (cachedStacks.length != slots.size()) {
            cachedStacks = new ItemStack[slots.size()];
            cachedMatch = new boolean[slots.size()];
        }
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            if (cachedStacks[i] != item) {
                cachedStacks[i] = item;
                cachedMatch[i] = matches(item);
            }
            if (!cachedMatch[i]) continue;
            int sx = left + slot.x, sy = top + slot.y;
            ctx.fill(sx, sy, sx + 16, sy + 16, 0x6000FF00);
            ctx.fill(sx, sy, sx + 16, sy + 1, 0xFF00FF00);
            ctx.fill(sx, sy + 15, sx + 16, sy + 16, 0xFF00FF00);
            ctx.fill(sx, sy, sx + 1, sy + 16, 0xFF00FF00);
            ctx.fill(sx + 15, sy, sx + 16, sy + 16, 0xFF00FF00);
        }
    }

    private static boolean matches(ItemStack stack) {
        if (!id.isEmpty()) return id.equals(ItemUtil.skyblockId(stack));
        return name.equals(StringUtil.strip(stack.getHoverName().getString()).trim());
    }
}
