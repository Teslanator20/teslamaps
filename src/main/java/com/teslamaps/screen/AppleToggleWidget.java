package com.teslamaps.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A clean toggle switch widget with smooth rendering.
 * Ported from TeslaAddons with label support.
 */
public class AppleToggleWidget extends ButtonWidget {
    private static final int TOGGLE_WIDTH = 40;
    private static final int TOGGLE_HEIGHT = 20;
    private static final float ANIMATION_SPEED = 0.15f;

    private final String label;
    private final Supplier<Boolean> getter;
    private final Consumer<Boolean> setter;
    private float animationProgress;

    public AppleToggleWidget(int x, int y, int width, int height, String label,
                             Supplier<Boolean> getter, Consumer<Boolean> setter) {
        super(x, y, width, height, Text.literal(label), button -> {
            // Toggle the value when clicked
            boolean newValue = !getter.get();
            setter.accept(newValue);
        }, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
        this.label = label;
        this.getter = getter;
        this.setter = setter;
        this.animationProgress = getter.get() ? 1.0f : 0.0f;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean hovered = isHovered();
        boolean value = getter.get();

        // Smooth animation
        float target = value ? 1.0f : 0.0f;
        if (animationProgress < target) {
            animationProgress = Math.min(target, animationProgress + ANIMATION_SPEED);
        } else if (animationProgress > target) {
            animationProgress = Math.max(target, animationProgress - ANIMATION_SPEED);
        }

        // Background with hover effect
        int bgColor = hovered ? AppleColors.CARD_BACKGROUND : AppleColors.BACKGROUND;
        context.fill(getX(), getY(), getX() + width, getY() + height, bgColor);

        // Bottom border
        context.fill(getX(), getY() + height - 1, getX() + width, getY() + height, AppleColors.SEPARATOR);

        // Label
        context.drawTextWithShadow(mc.textRenderer, label, getX() + 12, getY() + (height - 8) / 2, AppleColors.TEXT_PRIMARY);

        // Toggle switch on the right
        int toggleX = getX() + width - TOGGLE_WIDTH - 12;
        int toggleY = getY() + (height - TOGGLE_HEIGHT) / 2;

        // Track background color with smooth transition
        int trackColor = AppleColors.interpolate(AppleColors.TOGGLE_OFF, AppleColors.TOGGLE_ON, animationProgress);

        // Draw track with pill shape
        drawPill(context, toggleX, toggleY, TOGGLE_WIDTH, TOGGLE_HEIGHT, trackColor);

        // Draw knob
        int knobPadding = 2;
        int knobDiameter = TOGGLE_HEIGHT - (knobPadding * 2);
        int knobMinX = toggleX + knobPadding;
        int knobMaxX = toggleX + TOGGLE_WIDTH - knobDiameter - knobPadding;
        int knobX = (int) (knobMinX + (knobMaxX - knobMinX) * animationProgress);
        int knobY = toggleY + knobPadding;

        // Knob shadow (subtle)
        drawCircle(context, knobX + 1, knobY + 1, knobDiameter, 0x30000000);
        // Knob
        drawCircle(context, knobX, knobY, knobDiameter, AppleColors.TOGGLE_KNOB);
    }

    /**
     * Draw a pill shape (rounded rectangle)
     */
    private void drawPill(DrawContext context, int x, int y, int w, int h, int color) {
        int radius = h / 2;

        // Draw center rectangle
        context.fill(x + radius, y, x + w - radius, y + h, color);

        // Draw left cap
        drawHalfCircle(context, x + radius, y + radius, radius, color, true);

        // Draw right cap
        drawHalfCircle(context, x + w - radius, y + radius, radius, color, false);
    }

    /**
     * Draw a circle
     */
    private void drawCircle(DrawContext context, int x, int y, int diameter, int color) {
        int radius = diameter / 2;
        int centerX = x + radius;
        int centerY = y + radius;

        for (int dy = -radius; dy <= radius; dy++) {
            double rowWidth = Math.sqrt(radius * radius - dy * dy);
            int intWidth = (int) rowWidth;

            if (intWidth > 0) {
                context.fill(centerX - intWidth, centerY + dy, centerX + intWidth, centerY + dy + 1, color);
            }

            // Anti-alias the edges
            double fractional = rowWidth - intWidth;
            if (fractional > 0.1) {
                int edgeAlpha = (int) (((color >> 24) & 0xFF) * fractional);
                int edgeColor = (edgeAlpha << 24) | (color & 0x00FFFFFF);

                context.fill(centerX - intWidth - 1, centerY + dy, centerX - intWidth, centerY + dy + 1, edgeColor);
                context.fill(centerX + intWidth, centerY + dy, centerX + intWidth + 1, centerY + dy + 1, edgeColor);
            }
        }
    }

    /**
     * Draw a half circle (for pill caps)
     */
    private void drawHalfCircle(DrawContext context, int centerX, int centerY, int radius, int color, boolean left) {
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

            // Anti-alias the curved edge
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
