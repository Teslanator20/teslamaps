package com.teslamaps.profileviewer.screen;

import com.teslamaps.profileviewer.data.InventoryDecoder;
import com.teslamaps.utils.SkyblockItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Draws decoded hypixel containers as a vanilla style slot grid. */
public class ItemGrid {
    public static final int SLOT = 18;

    private static final int SLOT_BG = 0x60000000;
    private static final int SLOT_EDGE = 0x40FFFFFF;

    private static final Map<String, ItemStack> resolved = new HashMap<>();

    private static ItemStack stackFor(InventoryDecoder.Slot slot) {
        if (slot.skyblockId == null) return ItemStack.EMPTY;
        ItemStack cached = resolved.get(slot.skyblockId);
        if (cached != null) return cached;
        // unresolved ids stay blank, the tooltip still names them
        resolved.put(slot.skyblockId, ItemStack.EMPTY);
        SkyblockItems.resolve(slot.skyblockId, stack -> resolved.put(slot.skyblockId, stack));
        return ItemStack.EMPTY;
    }

    /**
     * Draws rows of nine. Returns the tooltip lines for whatever the mouse sits on, or null.
     */
    public static List<String> draw(GuiGraphicsExtractor ctx, List<InventoryDecoder.Slot> slots,
                                    int x, int y, int columns, int fromIndex, int count,
                                    int mouseX, int mouseY) {
        List<String> tooltip = null;

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
            } else if (slot.name != null) {
                // still loading or unknown: show the first letters so the slot is not blank
                Font font = Minecraft.getInstance().font;
                String short_ = com.teslamaps.utils.StringUtil.strip(slot.name);
                if (short_.length() > 2) short_ = short_.substring(0, 2);
                ctx.text(font, short_, sx + 4, sy + 5, 0xFF909090);
            }
            if (slot.count > 1) {
                Font font = Minecraft.getInstance().font;
                String amount = String.valueOf(slot.count);
                ctx.text(font, amount, sx + 17 - font.width(amount), sy + 9, 0xFFFFFFFF);
            }

            boolean hovered = mouseX >= sx && mouseX < sx + SLOT - 1 && mouseY >= sy && mouseY < sy + SLOT - 1;
            if (hovered) {
                ctx.fill(sx + 1, sy + 1, sx + SLOT - 2, sy + SLOT - 2, 0x40FFFFFF);
                tooltip = tooltipFor(slot);
            }
        }

        return tooltip;
    }

    private static List<String> tooltipFor(InventoryDecoder.Slot slot) {
        List<String> lines = new ArrayList<>();
        if (slot.name != null) lines.add(slot.name);
        else if (slot.skyblockId != null) lines.add(slot.skyblockId);
        lines.addAll(slot.lore);
        return lines;
    }

    /** Tooltip in the NEU window style, clamped to the panel. */
    public static void drawTooltip(GuiGraphicsExtractor ctx, List<String> lines, int mouseX, int mouseY) {
        if (lines == null || lines.isEmpty()) return;
        Font font = Minecraft.getInstance().font;

        int width = 0;
        for (String line : lines) width = Math.max(width, font.width(Component.literal(line)));
        int height = lines.size() * 10 + 4;

        int x = Math.min(mouseX + 8, NeuStyle.PANEL_WIDTH - width - 10);
        int y = Math.max(2, Math.min(mouseY - height / 2, NeuStyle.PANEL_HEIGHT - height - 2));

        NeuStyle.window(ctx, x - 3, y - 2, width + 8, height);
        for (int i = 0; i < lines.size(); i++) {
            ctx.text(font, Component.literal(lines.get(i)), x, y + 2 + i * 10, 0xFFFFFFFF);
        }
    }
}
