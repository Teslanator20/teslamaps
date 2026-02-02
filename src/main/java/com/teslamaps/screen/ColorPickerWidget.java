package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * A lightweight color picker popup with HSV selection.
 * Uses chunked rendering for better performance.
 */
public class ColorPickerWidget extends Screen {
    private static final int PICKER_SIZE = 128; // Power of 2 for better chunking
    private static final int HUE_WIDTH = 16;
    private static final int PADDING = 10;
    private static final int CHUNK_SIZE = 8; // Render in 8x8 chunks

    private final Screen parent;
    private final Consumer<String> onColorSelected;

    private int popupX, popupY;
    private int popupWidth, popupHeight;

    // HSV values (0-1 range)
    private float hue = 0f;
    private float saturation = 1f;
    private float brightness = 1f;

    private TextFieldWidget hexField;
    private boolean draggingPicker = false;
    private boolean draggingHue = false;
    private boolean wasMouseDown = false;

    public ColorPickerWidget(Screen parent, String initialColor, Consumer<String> onColorSelected) {
        super(Text.literal("Color Picker"));
        this.parent = parent;
        this.onColorSelected = onColorSelected;

        // Parse initial color to HSV
        int color = TeslaMapsConfig.parseColor(initialColor);
        float[] hsv = rgbToHsv((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF);
        this.hue = hsv[0];
        this.saturation = hsv[1];
        this.brightness = hsv[2];
    }

    @Override
    protected void init() {
        popupWidth = PICKER_SIZE + HUE_WIDTH + PADDING * 4 + 10;
        popupHeight = PICKER_SIZE + 70;
        popupX = (this.width - popupWidth) / 2;
        popupY = (this.height - popupHeight) / 2;

        // Hex input field
        hexField = new TextFieldWidget(textRenderer, popupX + PADDING, popupY + PICKER_SIZE + PADDING + 5, 80, 16, Text.literal("Hex"));
        hexField.setText(getCurrentHex());
        hexField.setMaxLength(8);
        hexField.setChangedListener(this::onHexChanged);
        addDrawableChild(hexField);
    }

    private void onHexChanged(String hex) {
        String cleaned = hex.replaceAll("[^0-9A-Fa-f]", "");
        if (cleaned.length() >= 6) {
            try {
                int color = TeslaMapsConfig.parseColor(cleaned);
                float[] hsv = rgbToHsv((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF);
                this.hue = hsv[0];
                this.saturation = hsv[1];
                this.brightness = hsv[2];
            } catch (Exception ignored) {}
        }
    }

    private String getCurrentHex() {
        int[] rgb = hsvToRgb(hue, saturation, brightness);
        return String.format("%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean isMouseDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        int pickerX = popupX + PADDING;
        int pickerY = popupY + 25;
        int hueX = pickerX + PICKER_SIZE + PADDING;

        // Handle mouse
        if (isMouseDown && !wasMouseDown) {
            if (mouseX >= pickerX && mouseX < pickerX + PICKER_SIZE &&
                mouseY >= pickerY && mouseY < pickerY + PICKER_SIZE) {
                draggingPicker = true;
            } else if (mouseX >= hueX && mouseX < hueX + HUE_WIDTH &&
                       mouseY >= pickerY && mouseY < pickerY + PICKER_SIZE) {
                draggingHue = true;
            }
            // Done button area
            else if (mouseX >= popupX + popupWidth - 60 && mouseX < popupX + popupWidth - 10 &&
                     mouseY >= popupY + popupHeight - 28 && mouseY < popupY + popupHeight - 8) {
                onColorSelected.accept(getCurrentHex());
                close();
                return;
            }
            // Cancel button area
            else if (mouseX >= popupX + popupWidth - 120 && mouseX < popupX + popupWidth - 70 &&
                     mouseY >= popupY + popupHeight - 28 && mouseY < popupY + popupHeight - 8) {
                close();
                return;
            }
        }

        if (!isMouseDown && wasMouseDown) {
            draggingPicker = false;
            draggingHue = false;
        }
        wasMouseDown = isMouseDown;

        if (draggingPicker) {
            saturation = Math.max(0, Math.min(1, (float)(mouseX - pickerX) / PICKER_SIZE));
            brightness = 1.0f - Math.max(0, Math.min(1, (float)(mouseY - pickerY) / PICKER_SIZE));
            hexField.setText(getCurrentHex());
        }
        if (draggingHue) {
            hue = Math.max(0, Math.min(1, (float)(mouseY - pickerY) / PICKER_SIZE));
            hexField.setText(getCurrentHex());
        }

        // Darken background
        context.fill(0, 0, this.width, this.height, 0x80000000);

        // Popup background
        context.fill(popupX - 1, popupY - 1, popupX + popupWidth + 1, popupY + popupHeight + 1, 0xFF000000);
        context.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, 0xFF2C2C2E);

        // Title
        context.drawCenteredTextWithShadow(textRenderer, "Pick a Color", popupX + popupWidth / 2, popupY + 8, 0xFFFFFFFF);

        // Draw chunked saturation/brightness picker
        drawSatBrightPickerChunked(context, pickerX, pickerY);

        // Draw chunked hue slider
        drawHueSliderChunked(context, hueX, pickerY);

        // Color preview
        int previewX = popupX + PADDING + 90;
        int previewY = popupY + PICKER_SIZE + PADDING + 5;
        int[] rgb = hsvToRgb(hue, saturation, brightness);
        int previewColor = 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
        context.fill(previewX, previewY, previewX + 30, previewY + 16, previewColor);
        drawBorder(context, previewX, previewY, 30, 16, 0xFF555555);

        // Picker cursor
        int cursorX = pickerX + (int)(saturation * PICKER_SIZE);
        int cursorY = pickerY + (int)((1 - brightness) * PICKER_SIZE);
        context.fill(cursorX - 4, cursorY - 1, cursorX + 4, cursorY + 2, 0xFF000000);
        context.fill(cursorX - 1, cursorY - 4, cursorX + 2, cursorY + 5, 0xFF000000);
        context.fill(cursorX - 3, cursorY, cursorX + 3, cursorY + 1, 0xFFFFFFFF);
        context.fill(cursorX, cursorY - 3, cursorX + 1, cursorY + 4, 0xFFFFFFFF);

        // Hue cursor
        int hueCursorY = pickerY + (int)(hue * PICKER_SIZE);
        context.fill(hueX - 2, hueCursorY - 2, hueX + HUE_WIDTH + 2, hueCursorY + 3, 0xFFFFFFFF);

        // Custom buttons
        drawStyledButton(context, popupX + popupWidth - 120, popupY + popupHeight - 28, 45, 20, "Cancel", mouseX, mouseY);
        drawStyledButton(context, popupX + popupWidth - 60, popupY + popupHeight - 28, 50, 20, "Done", mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawSatBrightPickerChunked(DrawContext context, int x, int y) {
        for (int cy = 0; cy < PICKER_SIZE; cy += CHUNK_SIZE) {
            for (int cx = 0; cx < PICKER_SIZE; cx += CHUNK_SIZE) {
                float sat = (cx + CHUNK_SIZE / 2f) / PICKER_SIZE;
                float bright = 1.0f - (cy + CHUNK_SIZE / 2f) / PICKER_SIZE;
                int[] rgb = hsvToRgb(hue, sat, bright);
                int color = 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
                context.fill(x + cx, y + cy, x + cx + CHUNK_SIZE, y + cy + CHUNK_SIZE, color);
            }
        }
    }

    private void drawHueSliderChunked(DrawContext context, int x, int y) {
        for (int cy = 0; cy < PICKER_SIZE; cy += CHUNK_SIZE) {
            float h = (cy + CHUNK_SIZE / 2f) / PICKER_SIZE;
            int[] rgb = hsvToRgb(h, 1f, 1f);
            int color = 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
            context.fill(x, y + cy, x + HUE_WIDTH, y + cy + CHUNK_SIZE, color);
        }
    }

    private void drawStyledButton(DrawContext context, int x, int y, int w, int h, String text, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int bg = hovered ? 0xFF3A3A3C : 0xFF2C2C2E;
        int border = hovered ? 0xFF5A5A5C : 0xFF48484A;

        context.fill(x, y, x + w, y + h, bg);
        drawBorder(context, x, y, w, h, border);
        context.drawCenteredTextWithShadow(textRenderer, text, x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    private static int[] hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h * 6) % 2 - 1));
        float m = v - c;
        float r, g, b;
        if (h < 1f/6f) { r = c; g = x; b = 0; }
        else if (h < 2f/6f) { r = x; g = c; b = 0; }
        else if (h < 3f/6f) { r = 0; g = c; b = x; }
        else if (h < 4f/6f) { r = 0; g = x; b = c; }
        else if (h < 5f/6f) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return new int[]{ (int)((r + m) * 255), (int)((g + m) * 255), (int)((b + m) * 255) };
    }

    private static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;
        float h = 0;
        if (delta != 0) {
            if (max == rf) h = ((gf - bf) / delta) % 6;
            else if (max == gf) h = (bf - rf) / delta + 2;
            else h = (rf - gf) / delta + 4;
            h /= 6;
            if (h < 0) h += 1;
        }
        float s = max == 0 ? 0 : delta / max;
        return new float[]{h, s, max};
    }
}
