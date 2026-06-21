/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.dungeon.termgui;

import com.teslamaps.config.TeslaMapsConfig;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;

public abstract class CustomTermGui {
    protected final Map<Integer, Box> itemIndexMap = new HashMap<>();
    private static CustomTermGui currentGui = null;

    protected abstract int[] getCurrentSolution();

    public abstract void renderTerminal(GuiGraphicsExtractor context, int slotCount);

    public void render(GuiGraphicsExtractor context) {
        setCurrentGui(this);
        itemIndexMap.clear();

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ContainerScreen screen) {
            int slotCount = screen.getMenu().slots.size() - 36; // Subtract player inventory
            renderTerminal(context, slotCount);
        }
    }

    public boolean handleClick(double mouseX, double mouseY, int button) {
        Integer hoveredSlot = getHoveredSlot(mouseX, mouseY);
        if (hoveredSlot != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ContainerScreen screen && mc.player != null) {
                mc.gameMode.handleContainerInput(
                    screen.getMenu().containerId,
                    hoveredSlot,
                    button,
                    net.minecraft.world.inventory.ContainerInput.PICKUP,
                    mc.player
                );
                return true;
            }
        }
        return false;
    }

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

    protected void renderBackground(GuiGraphicsExtractor context, int slotCount, int slotWidth, int rowOffset) {
        Minecraft mc = Minecraft.getInstance();
        float slotSize = 55f * TeslaMapsConfig.get().terminalGuiSize;
        float gap = TeslaMapsConfig.get().terminalGuiGap;
        float totalSlotSpace = slotSize + gap;

        int windowWidth = mc.getWindow().getGuiScaledWidth();
        int windowHeight = mc.getWindow().getGuiScaledHeight();

        float backgroundStartX = windowWidth / 2f - (slotWidth / 2f) * totalSlotSpace - 7.5f;
        float backgroundStartY = windowHeight / 2f + ((-rowOffset + 0.5f) * totalSlotSpace) - 7.5f;
        float backgroundWidth = slotWidth * totalSlotSpace + 15f;
        float backgroundHeight = ((float) slotCount / 9) * totalSlotSpace + 15f;

        int backgroundColor = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().terminalGuiBackgroundColor);
        drawRoundedRect(context, (int) backgroundStartX, (int) backgroundStartY,
                       (int) backgroundWidth, (int) backgroundHeight,
                       backgroundColor, 12);
    }

    protected float[] renderSlot(GuiGraphicsExtractor context, int index, int color) {
        Minecraft mc = Minecraft.getInstance();
        float slotSize = 55f * TeslaMapsConfig.get().terminalGuiSize;
        float gap = TeslaMapsConfig.get().terminalGuiGap;
        float totalSlotSpace = slotSize + gap;

        int windowWidth = mc.getWindow().getGuiScaledWidth();
        int windowHeight = mc.getWindow().getGuiScaledHeight();

        float x = (index % 9 - 4) * totalSlotSpace + windowWidth / 2f - slotSize / 2;
        float y = (index / 9 - 2) * totalSlotSpace + windowHeight / 2f - slotSize / 2;

        itemIndexMap.put(index, new Box(x, y, slotSize, slotSize));

        drawRoundedRect(context, (int) x, (int) y, (int) slotSize, (int) slotSize,
                       color, TeslaMapsConfig.get().terminalGuiRoundness);

        return new float[]{x, y};
    }

    protected void drawTextCentered(GuiGraphicsExtractor context, String text, float x, float y, float slotSize, int color) {
        Minecraft mc = Minecraft.getInstance();
        int textWidth = mc.font.width(text);
        float scale = TeslaMapsConfig.get().terminalGuiSize;
        float fontSize = 30f * scale;

        float textX = x + (slotSize - textWidth) / 2f;
        float textY = y + (slotSize - 8) / 2f; // 8 is approximate text height

        context.text(mc.font, text, (int) textX, (int) textY, color, true);
    }

    protected void drawRoundedRect(GuiGraphicsExtractor context, int x, int y, int width, int height, int color, int roundness) {
        context.fill(x, y, x + width, y + height, color);
    }

    private static void setCurrentGui(CustomTermGui gui) {
        currentGui = gui;
    }

    public static CustomTermGui getCurrentGui() {
        return currentGui;
    }

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
