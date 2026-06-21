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

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;

public class AppleToggleWidget extends Button {
    private static final int TOGGLE_WIDTH = 40;
    private static final int TOGGLE_HEIGHT = 20;
    private static final float ANIMATION_SPEED = 0.15f;

    private final String label;
    private final Supplier<Boolean> getter;
    private final Consumer<Boolean> setter;
    private float animationProgress;

    public AppleToggleWidget(int x, int y, int width, int height, String label,
                             Supplier<Boolean> getter, Consumer<Boolean> setter) {
        super(x, y, width, height, net.minecraft.network.chat.Component.literal(label), button -> {
            boolean newValue = !getter.get();
            setter.accept(newValue);
        }, Button.DEFAULT_NARRATION);
        this.label = label;
        this.getter = getter;
        this.setter = setter;
        this.animationProgress = getter.get() ? 1.0f : 0.0f;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        boolean hovered = isHovered();
        boolean value = getter.get();

        float target = value ? 1.0f : 0.0f;
        if (animationProgress < target) {
            animationProgress = Math.min(target, animationProgress + ANIMATION_SPEED);
        } else if (animationProgress > target) {
            animationProgress = Math.max(target, animationProgress - ANIMATION_SPEED);
        }

        int bgColor = hovered ? AppleColors.CARD_BACKGROUND : AppleColors.BACKGROUND;
        context.fill(getX(), getY(), getX() + width, getY() + height, bgColor);

        context.fill(getX(), getY() + height - 1, getX() + width, getY() + height, AppleColors.SEPARATOR);

        context.text(mc.font, label, getX() + 12, getY() + (height - 8) / 2, AppleColors.TEXT_PRIMARY);

        int toggleX = getX() + width - TOGGLE_WIDTH - 12;
        int toggleY = getY() + (height - TOGGLE_HEIGHT) / 2;

        int trackColor = AppleColors.interpolate(AppleColors.TOGGLE_OFF, AppleColors.TOGGLE_ON, animationProgress);

        drawPill(context, toggleX, toggleY, TOGGLE_WIDTH, TOGGLE_HEIGHT, trackColor);

        int knobPadding = 2;
        int knobDiameter = TOGGLE_HEIGHT - (knobPadding * 2);
        int knobMinX = toggleX + knobPadding;
        int knobMaxX = toggleX + TOGGLE_WIDTH - knobDiameter - knobPadding;
        int knobX = (int) (knobMinX + (knobMaxX - knobMinX) * animationProgress);
        int knobY = toggleY + knobPadding;

        drawCircle(context, knobX + 1, knobY + 1, knobDiameter, 0x30000000);
        drawCircle(context, knobX, knobY, knobDiameter, AppleColors.TOGGLE_KNOB);
    }

    private void drawPill(GuiGraphicsExtractor context, int x, int y, int w, int h, int color) {
        int radius = h / 2;

        context.fill(x + radius, y, x + w - radius, y + h, color);

        drawHalfCircle(context, x + radius, y + radius, radius, color, true);

        drawHalfCircle(context, x + w - radius, y + radius, radius, color, false);
    }

    private void drawCircle(GuiGraphicsExtractor context, int x, int y, int diameter, int color) {
        int radius = diameter / 2;
        int centerX = x + radius;
        int centerY = y + radius;

        for (int dy = -radius; dy <= radius; dy++) {
            double rowWidth = Math.sqrt(radius * radius - dy * dy);
            int intWidth = (int) rowWidth;

            if (intWidth > 0) {
                context.fill(centerX - intWidth, centerY + dy, centerX + intWidth, centerY + dy + 1, color);
            }

            double fractional = rowWidth - intWidth;
            if (fractional > 0.1) {
                int edgeAlpha = (int) (((color >> 24) & 0xFF) * fractional);
                int edgeColor = (edgeAlpha << 24) | (color & 0x00FFFFFF);

                context.fill(centerX - intWidth - 1, centerY + dy, centerX - intWidth, centerY + dy + 1, edgeColor);
                context.fill(centerX + intWidth, centerY + dy, centerX + intWidth + 1, centerY + dy + 1, edgeColor);
            }
        }
    }

    private void drawHalfCircle(GuiGraphicsExtractor context, int centerX, int centerY, int radius, int color, boolean left) {
        for (int dy = -radius; dy <= radius; dy++) {
            double rowWidth = Math.sqrt(radius * radius - dy * dy);
            int intWidth = (int) rowWidth;

            if (intWidth > 0) {
                if (left) {
                    context.fill(centerX - intWidth, centerY + dy, centerX, centerY + dy + 1, color);
                } else {
                    context.fill(centerX, centerY + dy, centerX + intWidth, centerY + dy + 1, color);
                }
            }

            double fractional = rowWidth - intWidth;
            if (fractional > 0.1) {
                int edgeAlpha = (int) (((color >> 24) & 0xFF) * fractional);
                int edgeColor = (edgeAlpha << 24) | (color & 0x00FFFFFF);

                if (left) {
                    context.fill(centerX - intWidth - 1, centerY + dy, centerX - intWidth, centerY + dy + 1, edgeColor);
                } else {
                    context.fill(centerX + intWidth, centerY + dy, centerX + intWidth + 1, centerY + dy + 1, edgeColor);
                }
            }
        }
    }

    public boolean getValue() {
        return getter.get();
    }
}
