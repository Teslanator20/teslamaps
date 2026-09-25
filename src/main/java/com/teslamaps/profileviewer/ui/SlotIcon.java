package com.teslamaps.profileviewer.ui;

import com.teslamaps.profileviewer.data.InventoryDecoder;
import com.teslamaps.profileviewer.screen.ItemGrid;
import com.teslamaps.utils.SkyblockItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * An item that resolves per draw: stacks come from the item repo asynchronously, so a stack
 * grabbed at build time would freeze the placeholder in. Both lookups below are cached.
 */
public class SlotIcon extends Ui {
    private final InventoryDecoder.Slot slot;
    private final String itemId;
    private final ItemStack fallback;
    private final int size;
    private final boolean framed;
    private final boolean tooltip;
    private String corner;
    private int cornerColor = 0xFFFFFFFF;

    private SlotIcon(InventoryDecoder.Slot slot, String itemId, ItemStack fallback, int size, boolean framed, boolean tooltip) {
        this.slot = slot;
        this.itemId = itemId;
        this.fallback = fallback == null ? ItemStack.EMPTY : fallback;
        this.size = size;
        this.framed = framed;
        this.tooltip = tooltip;
    }

    /** A decoded item in a vanilla slot, its real tooltip on hover. Null slot = empty slot. */
    public static SlotIcon of(InventoryDecoder.Slot slot, int size) {
        return new SlotIcon(slot, null, null, size, true, true);
    }

    /** A skyblock item by id, no tooltip (wrap it in a Tip). */
    public static SlotIcon ofId(String id, ItemStack fallback, int size, boolean framed) {
        return new SlotIcon(null, id, fallback, size, framed, false);
    }

    /** Small text in the lower right corner, like a stack size. */
    public SlotIcon corner(String text, int color) {
        this.corner = text;
        this.cornerColor = color;
        return this;
    }

    public int width() {
        return size;
    }

    public int height() {
        return size;
    }

    private ItemStack stack() {
        ItemStack stack = ItemStack.EMPTY;
        if (slot != null && !slot.isEmpty()) stack = ItemGrid.stackFor(slot);
        else if (itemId != null) stack = SkyblockItems.stackNow(itemId);
        return stack.isEmpty() ? fallback : stack;
    }

    public void render(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY) {
        if (framed) ItemGrid.slot(ctx, x, y, size);
        ItemStack stack = stack();
        if (!stack.isEmpty()) {
            if (framed) {
                ItemGrid.item(ctx, stack, x, y, size);
            } else {
                float factor = size / 16f;
                ctx.pose().pushMatrix();
                ctx.pose().translate(x, y);
                ctx.pose().scale(factor, factor);
                ctx.item(stack, 0, 0);
                ctx.pose().popMatrix();
            }
        }
        if (corner != null) ItemGrid.smallCount(ctx, corner, x, y, size, cornerColor);
        if (over(mouseX, mouseY, x, y, size, size)) {
            if (framed) ctx.fill(x + 1, y + 1, x + size - 2, y + size - 2, 0x40FFFFFF);
            if (tooltip && !stack.isEmpty()) hoveredStack = stack;
        }
    }
}
