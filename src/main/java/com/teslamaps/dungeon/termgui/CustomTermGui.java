package com.teslamaps.dungeon.termgui;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for custom terminal GUI rendering.
 * Base class for terminal GUI overlays.
 */
public abstract class CustomTermGui {
    protected final Map<Integer, Box> itemIndexMap = new HashMap<>();
    private static CustomTermGui currentGui = null;

    /**
     * Get the current solution for the terminal (slot indices to click).
     */
    protected abstract int[] getCurrentSolution();

    /**
     * Render the terminal GUI with the given number of slots.
     */
    public abstract void renderTerminal(DrawContext context, int slotCount);

    /**
     * Main render method called every frame when terminal is open.
     */
    public void render(DrawContext context) {
        setCurrentGui(this);
        itemIndexMap.clear();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            int slotCount = screen.getScreenHandler().slots.size() - 36; // Subtract player inventory
            renderTerminal(context, slotCount);
        }
    }

    /**
     * Handle mouse click on the custom GUI.
     * Returns true if the click was handled, false otherwise.
     */
    public boolean handleClick(double mouseX, double mouseY, int button) {
        Integer hoveredSlot = getHoveredSlot(mouseX, mouseY);
        if (hoveredSlot != null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.currentScreen instanceof GenericContainerScreen screen && mc.player != null) {
                // Perform the click on the slot
                mc.interactionManager.clickSlot(
                    screen.getScreenHandler().syncId,
                    hoveredSlot,
                    button,
                    net.minecraft.screen.slot.SlotActionType.PICKUP,
                    mc.player
                );
                return true;
            }
        }
        return false;
    }

    /**
     * Get the slot index under the mouse cursor, or null if none.
     */
    private Integer getHoveredSlot(double mouseX, double mouseY) {
        for (Map.Entry<Integer, Box> entry : itemIndexMap.entrySet()) {
            Box box = entry.getValue();
            if (mouseX >= box.x && mouseX <= box.x + box.w &&
                mouseY >= box.y && mouseY <= box.y + box.h) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Render the background box for the terminal.
     */
    protected void renderBackground(DrawContext context, int slotCount, int slotWidth, int rowOffset) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float slotSize = 55f * TeslaMapsConfig.get().terminalGuiSize;
        float gap = TeslaMapsConfig.get().terminalGuiGap;
        float totalSlotSpace = slotSize + gap;

        int windowWidth = mc.getWindow().getScaledWidth();
        int windowHeight = mc.getWindow().getScaledHeight();

        float backgroundStartX = windowWidth / 2f - (slotWidth / 2f) * totalSlotSpace - 7.5f;
        float backgroundStartY = windowHeight / 2f + ((-rowOffset + 0.5f) * totalSlotSpace) - 7.5f;
        float backgroundWidth = slotWidth * totalSlotSpace + 15f;
        float backgroundHeight = ((float) slotCount / 9) * totalSlotSpace + 15f;

        int backgroundColor = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiBackgroundColor);
        drawRoundedRect(context, (int) backgroundStartX, (int) backgroundStartY,
                       (int) backgroundWidth, (int) backgroundHeight,
                       backgroundColor, 12);
    }

    /**
     * Render a single slot at the given index with the specified color.
     * Returns the x, y coordinates of the rendered slot.
     */
    protected float[] renderSlot(DrawContext context, int index, int color) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float slotSize = 55f * TeslaMapsConfig.get().terminalGuiSize;
        float gap = TeslaMapsConfig.get().terminalGuiGap;
        float totalSlotSpace = slotSize + gap;

        int windowWidth = mc.getWindow().getScaledWidth();
        int windowHeight = mc.getWindow().getScaledHeight();

        float x = (index % 9 - 4) * totalSlotSpace + windowWidth / 2f - slotSize / 2;
        float y = (index / 9 - 2) * totalSlotSpace + windowHeight / 2f - slotSize / 2;

        itemIndexMap.put(index, new Box(x, y, slotSize, slotSize));

        drawRoundedRect(context, (int) x, (int) y, (int) slotSize, (int) slotSize,
                       color, TeslaMapsConfig.get().terminalGuiRoundness);

        return new float[]{x, y};
    }

    /**
     * Draw text centered on the slot.
     */
    protected void drawTextCentered(DrawContext context, String text, float x, float y, float slotSize, int color) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int textWidth = mc.textRenderer.getWidth(text);
        float scale = TeslaMapsConfig.get().terminalGuiSize;
        float fontSize = 30f * scale;

        // Simple centering - doesn't account for exact font metrics like NVG does
        float textX = x + (slotSize - textWidth) / 2f;
        float textY = y + (slotSize - 8) / 2f; // 8 is approximate text height

        context.drawText(mc.textRenderer, text, (int) textX, (int) textY, color, true);
    }

    /**
     * Draw a rounded rectangle (simplified - just draws a regular rect for now).
     * Full rounded rect would require custom rendering.
     */
    protected void drawRoundedRect(DrawContext context, int x, int y, int width, int height, int color, int roundness) {
        // For now, just draw a filled rect
        // TODO: Implement proper rounded corners if needed
        context.fill(x, y, x + width, y + height, color);
    }

    private static void setCurrentGui(CustomTermGui gui) {
        currentGui = gui;
    }

    public static CustomTermGui getCurrentGui() {
        return currentGui;
    }

    /**
     * Simple box for tracking slot positions.
     */
    protected static class Box {
        public final float x, y, w, h;

        public Box(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }
}
