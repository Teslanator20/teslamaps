package com.teslamaps.profileviewer.screen;

import com.teslamaps.profileviewer.data.InventoryDecoder;
import com.teslamaps.profileviewer.data.LegacyText;
import com.teslamaps.utils.SkyblockItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws decoded hypixel containers as a vanilla style slot grid. The stacks carry the real
 * name and lore as components and the tooltip goes through the vanilla renderer, so other
 * mods (chroma enchants, tooltip tweaks, price tags) apply to them like to any other item.
 */
public class ItemGrid {
    public static final int SLOT = 18;

    private static final int SLOT_BG = 0x60000000;
    private static final int SLOT_EDGE = 0x40FFFFFF;

    private static final Map<String, ItemStack> bases = new HashMap<>();
    private static final Map<InventoryDecoder.Slot, ItemStack> built = new HashMap<>();

    /** The item as it should look in a menu: right material, hypixel name, hypixel lore. */
    public static ItemStack stackFor(InventoryDecoder.Slot slot) {
        ItemStack cached = built.get(slot);
        if (cached != null) return cached;

        ItemStack base = ItemStack.EMPTY;
        if (slot.skyblockId != null) {
            ItemStack known = bases.get(slot.skyblockId);
            if (known == null) {
                bases.put(slot.skyblockId, ItemStack.EMPTY);
                SkyblockItems.resolve(slot.skyblockId, stack -> {
                    bases.put(slot.skyblockId, stack);
                    built.remove(slot);
                });
            } else {
                base = known;
            }
        }

        // never hand back an empty stack for a filled slot, the tooltip would vanish with it
        boolean resolved = !base.isEmpty();
        if (!resolved) {
            base = new ItemStack(slot.skyblockId != null ? Items.BARRIER : Items.PAPER);
        }

        ItemStack stack = base.copy();
        stack.setCount(Math.max(1, slot.count));

        // other mods identify skyblock items through custom_data, so pass hypixel's tag on
        if (slot.extraAttributes != null) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(slot.extraAttributes.copy()));
        }
        if (slot.color >= 0) {
            stack.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(slot.color));
        }
        if (slot.enchanted) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        if (slot.name != null) {
            stack.set(DataComponents.CUSTOM_NAME, LegacyText.parse(slot.name));
        }
        if (!slot.lore.isEmpty()) {
            List<Component> lore = new ArrayList<>(slot.lore.size());
            for (String line : slot.lore) lore.add(LegacyText.parse(line));
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }

        // a placeholder must never be cached: the item table and the repo answer asynchronously,
        // and opening the viewer before they are done would freeze the barrier in for good
        if (resolved) built.put(slot, stack);
        return stack;
    }

    /** Biggest slot size that still fits the grid into the free space, never below vanilla. */
    public static int fitSlot(int availWidth, int availHeight, int columns, int rows) {
        if (columns <= 0 || rows <= 0) return SLOT;
        int size = Math.min(availWidth / columns, availHeight / rows);
        return Math.max(SLOT, Math.min(size, MAX_SLOT));
    }

    public static final int MAX_SLOT = 36;
    public static final int MAX_FREE_SLOT = 54;

    /**
     * Fits a free standing grid of <count> items into the area: the biggest slot size that gets
     * all of them on one page, columns chosen to match. Falls back to vanilla size plus paging
     * when even that is not enough. Returns {slot, columns, rows}.
     */
    public static int[] fitGrid(int availWidth, int availHeight, int count) {
        for (int size = MAX_FREE_SLOT; size >= SLOT; size--) {
            int columns = Math.max(1, availWidth / size);
            int rows = Math.max(1, (count + columns - 1) / columns);
            if (rows * size <= availHeight) return new int[]{size, columns, rows};
        }

        int columns = Math.max(1, availWidth / SLOT);
        return new int[]{SLOT, columns, Math.max(1, availHeight / SLOT)};
    }

    /** One empty slot of the given size, so pages that draw their own grid match this one. */
    public static void slot(GuiGraphicsExtractor ctx, int sx, int sy, int size) {
        ctx.fill(sx, sy, sx + size - 1, sy + size - 1, SLOT_BG);
        ctx.fill(sx, sy, sx + size - 1, sy + 1, SLOT_EDGE);
        ctx.fill(sx, sy, sx + 1, sy + size - 1, SLOT_EDGE);
    }

    /** The item filling a slot of the given size; 16px art scaled to the slot, rarity tint included. */
    public static void item(GuiGraphicsExtractor ctx, ItemStack stack, int sx, int sy, int size) {
        float factor = (size - 2) / 16f;
        ctx.pose().pushMatrix();
        ctx.pose().translate(sx + 1, sy + 1);
        ctx.pose().scale(factor, factor);
        com.teslamaps.features.RarityBackground.draw(ctx, 0, 0, stack);
        ctx.item(stack, 0, 0);
        ctx.pose().popMatrix();
    }

    /** Stack size in the lower right corner, scaled with the slot. */
    public static void count(GuiGraphicsExtractor ctx, String text, int sx, int sy, int size, int color) {
        Font font = Minecraft.getInstance().font;
        float factor = (size - 2) / 16f;
        ctx.pose().pushMatrix();
        ctx.pose().translate(sx + 1, sy + 1);
        ctx.pose().scale(factor, factor);
        ctx.text(font, text, 16 - font.width(text), 8, color);
        ctx.pose().popMatrix();
    }

    /**
     * Draws rows of nine and returns the stack under the cursor, or empty. The caller shows
     * the tooltip so it lands on top of everything.
     */
    public static ItemStack draw(GuiGraphicsExtractor ctx, List<InventoryDecoder.Slot> slots,
                                 int x, int y, int columns, int fromIndex, int count,
                                 int mouseX, int mouseY) {
        return draw(ctx, slots, x, y, columns, fromIndex, count, mouseX, mouseY, SLOT);
    }

    public static ItemStack draw(GuiGraphicsExtractor ctx, List<InventoryDecoder.Slot> slots,
                                 int x, int y, int columns, int fromIndex, int count,
                                 int mouseX, int mouseY, int size) {
        ItemStack hoveredStack = ItemStack.EMPTY;

        for (int i = 0; i < count; i++) {
            int index = fromIndex + i;
            int sx = x + (i % columns) * size;
            int sy = y + (i / columns) * size;

            slot(ctx, sx, sy, size);

            if (index < 0 || index >= slots.size()) continue;
            InventoryDecoder.Slot slot = slots.get(index);
            if (slot.isEmpty()) continue;

            ItemStack stack = stackFor(slot);
            if (!stack.isEmpty()) {
                item(ctx, stack, sx, sy, size);
                if (slot.count > 1) {
                    count(ctx, String.valueOf(slot.count), sx, sy, size, 0xFFFFFFFF);
                }
            }

            boolean hovered = mouseX >= sx && mouseX < sx + size - 1 && mouseY >= sy && mouseY < sy + size - 1;
            if (hovered) {
                ctx.fill(sx + 1, sy + 1, sx + size - 2, sy + size - 2, 0x40FFFFFF);
                hoveredStack = stack;
            }
        }

        return hoveredStack;
    }
}
