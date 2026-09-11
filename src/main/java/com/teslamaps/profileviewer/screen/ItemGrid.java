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
        if (base.isEmpty()) {
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

        built.put(slot, stack);
        return stack;
    }

    /**
     * Draws rows of nine and returns the stack under the cursor, or empty. The caller shows
     * the tooltip so it lands on top of everything.
     */
    public static ItemStack draw(GuiGraphicsExtractor ctx, List<InventoryDecoder.Slot> slots,
                                 int x, int y, int columns, int fromIndex, int count,
                                 int mouseX, int mouseY) {
        ItemStack hoveredStack = ItemStack.EMPTY;

        for (int i = 0; i < count; i++) {
            int index = fromIndex + i;
            int sx = x + (i % columns) * SLOT;
            int sy = y + (i / columns) * SLOT;

            ctx.fill(sx, sy, sx + SLOT - 1, sy + SLOT - 1, SLOT_BG);
            ctx.fill(sx, sy, sx + SLOT - 1, sy + 1, SLOT_EDGE);
            ctx.fill(sx, sy, sx + 1, sy + SLOT - 1, SLOT_EDGE);

            if (index < 0 || index >= slots.size()) continue;
            InventoryDecoder.Slot slot = slots.get(index);
            if (slot.isEmpty()) continue;

            ItemStack stack = stackFor(slot);
            if (!stack.isEmpty()) {
                ctx.item(stack, sx + 1, sy + 1);
                if (slot.count > 1) {
                    Font font = Minecraft.getInstance().font;
                    String amount = String.valueOf(slot.count);
                    ctx.text(font, amount, sx + 17 - font.width(amount), sy + 9, 0xFFFFFFFF);
                }
            }

            boolean hovered = mouseX >= sx && mouseX < sx + SLOT - 1 && mouseY >= sy && mouseY < sy + SLOT - 1;
            if (hovered) {
                ctx.fill(sx + 1, sy + 1, sx + SLOT - 2, sy + SLOT - 2, 0x40FFFFFF);
                hoveredStack = stack;
            }
        }

        return hoveredStack;
    }
}
