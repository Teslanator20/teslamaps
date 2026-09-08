package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The estimated item value breakdown for the hovered slot, drawn as the plain
 * line list SkyHanni shows.
 */
public class ItemValueOverlay {

    private static final int LINE_HEIGHT = 12;

    private static final Map<ItemStack, List<String>> cache = new IdentityHashMap<>();
    private static Screen lastScreen;
    private static ItemStack hovered;
    private static long hoverStart;
    private static boolean hoverDelayOver;

    public static boolean shouldRender() {
        if (!TeslaMapsConfig.get().itemValueGui) return false;
        return Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>;
    }

    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;

        if (lastScreen != mc.screen) {
            lastScreen = mc.screen;
            cache.clear();
            hovered = null;
            hoverDelayOver = false;
        }

        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        Slot slot = acc.getFocusedSlot();
        if (slot == null || slot.getItem().isEmpty()) {
            hovered = null;
            return;
        }

        ItemStack item = slot.getItem();
        if (item != hovered) {
            hovered = item;
            hoverStart = System.currentTimeMillis();
        }

        List<String> lines = cache.get(item);
        if (lines == null) {
            lines = EstimatedValue.display(item);
            cache.put(item, lines);
        }
        if (lines.isEmpty()) return;

        if (!hoverDelayOver) {
            int delay = TeslaMapsConfig.get().itemValueHoverDelay;
            if (delay > 0 && System.currentTimeMillis() - hoverStart < delay) return;
            hoverDelayOver = true;
        }

        draw(ctx, mc, lines, acc.getX(), acc.getY(), acc.getImageWidth());
    }

    // left of the inventory GUI, flipped to the right when there is no room
    private static void draw(GuiGraphicsExtractor ctx, Minecraft mc, List<String> lines, int guiX, int guiY, int guiW) {
        int width = 0;
        for (String line : lines) width = Math.max(width, mc.font.width(line));

        int x = guiX - width - 8;
        if (x < 2) x = guiX + guiW + 8;
        int y = guiY;
        int height = lines.size() * LINE_HEIGHT;
        int screenH = mc.getWindow().getGuiScaledHeight();
        if (y + height > screenH - 2) y = Math.max(2, screenH - 2 - height);

        for (String line : lines) {
            ctx.text(mc.font, line, x, y, 0xFFFFFFFF);
            y += LINE_HEIGHT;
        }
    }

    private ItemValueOverlay() {}
}
