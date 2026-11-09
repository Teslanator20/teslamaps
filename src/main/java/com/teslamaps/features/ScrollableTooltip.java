package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.inventory.Slot;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.lwjgl.glfw.GLFW;

public class ScrollableTooltip {

    private static int offset = 0;
    private static int offsetX = 0;
    private static int lastSlot = -1;
    private static int externalSlot = -1;

    /** Screens that are not containers (the profile viewer) hand in their own hover key. */
    public static void setExternalFocus(int key) {
        externalSlot = key;
    }

    private static int focusedSlot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> cs) {
            Slot s = ((HandledScreenAccessor) cs).getFocusedSlot();
            return (s == null || s.getItem().isEmpty()) ? -1 : s.index;
        }
        return externalSlot;
    }

    private static boolean down(int a, int b) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return false;
        long h = mc.getWindow().handle();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(a) || com.mojang.blaze3d.platform.InputConstants.isKeyDown(b);
    }

    private static void track(int slot) {
        if (slot == lastSlot) return;
        lastSlot = slot;
        if (!TeslaMapsConfig.get().tooltipScrollKeep) { offset = 0; offsetX = 0; }
    }

    public static boolean onScroll(double scrollY) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.scrollableTooltip) return false;
        int slot = focusedSlot();
        if (slot == -1) return false;
        track(slot);
        double dir = c.tooltipScrollInvert ? -scrollY : scrollY;
        if (c.tooltipScrollZoom && down(com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL, com.mojang.blaze3d.platform.InputConstants.KEY_RCONTROL)) {
            c.tooltipZoom = (float) Math.max(0.3, Math.min(3.0, c.tooltipZoom * (1 + 0.1 * Math.signum(scrollY))));
        } else if (c.tooltipScrollHorizontal && down(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT, com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT)) {
            offsetX += (int) (dir * c.tooltipScrollStep);
        } else {
            offset += (int) (dir * c.tooltipScrollStep);
        }
        return true;
    }

    /** Ctrl+scroll zoom, 1 while the option is off. */
    public static float zoom() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.scrollableTooltip || !c.tooltipScrollZoom) return 1f;
        return c.tooltipZoom;
    }

    public static void reset() {
        offset = 0;
        offsetX = 0;
        TeslaMapsConfig.get().tooltipZoom = 1f;
    }

    public static ClientTooltipPositioner wrap(ClientTooltipPositioner base) {
        if (!TeslaMapsConfig.get().scrollableTooltip) return base;
        int slot = focusedSlot();
        if (slot == -1) return base;
        track(slot);

        return (screenWidth, screenHeight, mouseX, mouseY, width, height) -> {
            Vector2ic pos = base.positionTooltip(screenWidth, screenHeight, mouseX, mouseY, width, height);
            int y = pos.y();
            int min = 8 - y - height;
            int max = screenHeight - 8 - y;
            if (offset < min) offset = min;
            if (offset > max) offset = max;
            int x = pos.x();
            int minX = 8 - x - width;
            int maxX = screenWidth - 8 - x;
            if (offsetX < minX) offsetX = minX;
            if (offsetX > maxX) offsetX = maxX;
            return new Vector2i(x + offsetX, y + offset);
        };
    }
}
