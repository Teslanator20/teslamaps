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
package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class ClassFilterScreen extends Screen {
    private static final int PANEL_W = 300, PANEL_H = 230;
    private static final int ROW_H = 26;
    private static final String[] CLASSES = {"Mage", "Archer", "Berserk", "Healer", "Tank"};

    private final Screen parent;
    private final String featureLabel;
    private final TeslaMapsConfig.ClassFilter filter;
    private boolean wasMouseDown = false;

    private int panelX, panelY, listX, listY, listW;

    public ClassFilterScreen(Screen parent, String featureLabel, TeslaMapsConfig.ClassFilter filter) {
        super(Component.literal("Only as Class"));
        this.parent = parent;
        this.featureLabel = featureLabel;
        this.filter = filter;
    }

    private boolean get(int i) {
        return switch (i) {
            case 0 -> filter.mage;
            case 1 -> filter.archer;
            case 2 -> filter.berserk;
            case 3 -> filter.healer;
            default -> filter.tank;
        };
    }

    private void set(int i, boolean v) {
        switch (i) {
            case 0 -> filter.mage = v;
            case 1 -> filter.archer = v;
            case 2 -> filter.berserk = v;
            case 3 -> filter.healer = v;
            default -> filter.tank = v;
        }
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        listX = panelX + 16;
        listY = panelY + 56;
        listW = PANEL_W - 32;

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
            TeslaMapsConfig.save();
            minecraft.setScreen(parent);
        }).bounds(panelX + 16, panelY + PANEL_H - 30, PANEL_W - 32, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        boolean isMouseDown = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean clicked = isMouseDown && !wasMouseDown;

        ctx.fill(0, 0, width, height, 0xC0000000);
        roundRect(ctx, panelX, panelY, PANEL_W, PANEL_H, AppleColors.CARD_BACKGROUND);
        ctx.fill(panelX + 10, panelY, panelX + PANEL_W - 10, panelY + 3, AppleColors.ACCENT_GREEN);

        ctx.text(this.font, "Only as Class", panelX + 16, panelY + 16, AppleColors.TEXT_PRIMARY);
        ctx.text(this.font, featureLabel, panelX + 16, panelY + 30, AppleColors.TEXT_SECONDARY);

        int rowY = listY;
        for (int i = 0; i < CLASSES.length; i++) {
            boolean value = get(i);
            boolean rowHover = mouseX >= listX && mouseX < listX + listW && mouseY >= rowY && mouseY < rowY + ROW_H - 2;
            if (clicked && rowHover) { set(i, !value); value = !value; }

            ctx.fill(listX, rowY, listX + listW, rowY + ROW_H - 2, rowHover ? 0xFF3A3A3C : AppleColors.CARD_BACKGROUND);
            drawBorder(ctx, listX, rowY, listW, ROW_H - 2, AppleColors.INPUT_BORDER);
            ctx.text(this.font, CLASSES[i], listX + 8, rowY + 7, AppleColors.TEXT_PRIMARY);

            int toggleX = listX + listW - 44, toggleY = rowY + 4;
            ctx.fill(toggleX, toggleY, toggleX + 36, toggleY + 16, value ? AppleColors.TOGGLE_ON : AppleColors.TOGGLE_OFF);
            drawBorder(ctx, toggleX, toggleY, 36, 16, AppleColors.INPUT_BORDER);
            int knobX = value ? toggleX + 20 : toggleX + 2;
            ctx.fill(knobX, toggleY + 2, knobX + 14, toggleY + 14, AppleColors.TOGGLE_KNOB);

            rowY += ROW_H;
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
        wasMouseDown = isMouseDown;
    }

    private void roundRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 4, y, x + w - 4, y + h, color);
        ctx.fill(x, y + 4, x + 4, y + h - 4, color);
        ctx.fill(x + w - 4, y + 4, x + w, y + h - 4, color);
    }

    private void drawBorder(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
