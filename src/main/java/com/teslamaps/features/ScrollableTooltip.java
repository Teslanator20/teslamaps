package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.inventory.Slot;
import org.joml.Vector2i;
import org.joml.Vector2ic;

public class ScrollableTooltip {

    private static int offset = 0;
    private static int lastSlot = -1;

    private static int focusedSlot() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return -1;
        Slot s = ((HandledScreenAccessor) cs).getFocusedSlot();
        return (s == null || s.getItem().isEmpty()) ? -1 : s.index;
    }

    public static boolean onScroll(double scrollY) {
        if (!TeslaMapsConfig.get().scrollableTooltip) return false;
        int slot = focusedSlot();
        if (slot == -1) return false;
        if (slot != lastSlot) { offset = 0; lastSlot = slot; }
        offset += (int) (scrollY * 20);
        return true;
    }

    public static ClientTooltipPositioner wrap(ClientTooltipPositioner base) {
        if (!TeslaMapsConfig.get().scrollableTooltip) return base;
        int slot = focusedSlot();
        if (slot == -1) return base;
        if (slot != lastSlot) { offset = 0; lastSlot = slot; }

        return (screenWidth, screenHeight, mouseX, mouseY, width, height) -> {
            Vector2ic pos = base.positionTooltip(screenWidth, screenHeight, mouseX, mouseY, width, height);
            int y = pos.y();
            int min = 8 - y - height;
            int max = screenHeight - 8 - y;
            if (offset < min) offset = min;
            if (offset > max) offset = max;
            return new Vector2i(pos.x(), y + offset);
        };
    }
}
