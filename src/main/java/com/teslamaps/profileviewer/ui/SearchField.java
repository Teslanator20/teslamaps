package com.teslamaps.profileviewer.ui;

import com.teslamaps.profileviewer.screen.NeuStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small inline search box for the pv pages: click to focus, type to filter. */
public class SearchField {
    private String text = "";
    private boolean focused = false;

    private int x;
    private int y;
    private int width;

    public String text() {
        return text;
    }

    public boolean focused() { return focused; }

    public boolean isEmpty() {
        return text.isEmpty();
    }

    public boolean matches(String candidate) {
        return text.isEmpty() || candidate != null
                && candidate.toLowerCase(java.util.Locale.ROOT).contains(text.toLowerCase(java.util.Locale.ROOT));
    }

    public void clear() {
        text = "";
        focused = false;
    }

    public void draw(GuiGraphicsExtractor ctx, int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        Font font = Minecraft.getInstance().font;

        NeuStyle.slimBox(ctx, x, y, width, 14);
        String shown = text;
        while (!shown.isEmpty() && font.width(shown) > width - 10) shown = shown.substring(1);
        if (focused) {
            boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
            ctx.text(font, shown + (caret ? "_" : ""), x + 4, y + 3, NeuStyle.TEXT_YELLOW);
        } else if (shown.isEmpty()) {
            ctx.text(font, "Search...", x + 4, y + 3, NeuStyle.TEXT_DARK);
        } else {
            ctx.text(font, shown, x + 4, y + 3, NeuStyle.TEXT);
        }
    }

    /** True when the click landed in the box; focuses/unfocuses accordingly. */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean inside = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 14;
        focused = inside;
        if (inside && button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) text = "";
        return inside;
    }

    public boolean charTyped(char chr) {
        if (!focused) return false;
        if (chr >= 32 && text.length() < 40) text += chr;
        return true;
    }

    /** Backspace and escape while focused; escape only drops the focus, not the screen. */
    public boolean keyPressed(int key) {
        if (!focused) return false;
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE) {
            if (!text.isEmpty()) text = text.substring(0, text.length() - 1);
            return true;
        }
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE || key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || key == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
            focused = false;
            return true;
        }
        return false;
    }

    public boolean isFocused() {
        return focused;
    }
}
