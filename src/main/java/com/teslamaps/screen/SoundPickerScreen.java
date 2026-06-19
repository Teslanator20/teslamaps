package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SoundOptions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Full-screen sound picker: scroll a list, click any entry to hear it, then Apply (keep) or
 * Nevermind (revert). Opened from the sound options in the config screen.
 */
public class SoundPickerScreen extends Screen {
    private static final int PANEL_W = 320, PANEL_H = 300;
    private static final int ROW_H = 20, VISIBLE = 9;

    private final Screen parent;
    private final String[] sounds;
    private final Consumer<String> onApply;
    private final String original;
    private String selected;
    private int scroll = 0;
    private boolean wasMouseDown = false;

    private int panelX, panelY, listX, listY, listW;

    public SoundPickerScreen(Screen parent, String label, String[] sounds, String current, Consumer<String> onApply) {
        super(Component.literal(label));
        this.parent = parent;
        this.sounds = sounds;
        this.onApply = onApply;
        this.original = current;
        this.selected = current;
        int idx = indexOf(current);
        if (idx >= 0) scroll = Math.max(0, Math.min(idx - VISIBLE / 2, Math.max(0, sounds.length - VISIBLE)));
    }

    private int indexOf(String s) {
        for (int i = 0; i < sounds.length; i++) if (sounds[i].equals(s)) return i;
        return -1;
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        listX = panelX + 16;
        listY = panelY + 50;
        listW = PANEL_W - 32;

        int btnW = (PANEL_W - 40) / 2;
        addRenderableWidget(Button.builder(Component.literal("§a§lApply"), b -> {
            onApply.accept(selected);
            TeslaMapsConfig.save();
            minecraft.setScreen(parent);
        }).bounds(panelX + 16, panelY + PANEL_H - 30, btnW, 20).build());

        addRenderableWidget(Button.builder(Component.literal("§c§lNevermind"), b -> minecraft.setScreen(parent))
                .bounds(panelX + 24 + btnW, panelY + PANEL_H - 30, btnW, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, sounds.length - VISIBLE);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(sy)));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        boolean isMouseDown = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean clicked = isMouseDown && !wasMouseDown;

        ctx.fill(0, 0, width, height, 0xC0000000);
        roundRect(ctx, panelX, panelY, PANEL_W, PANEL_H, AppleColors.CARD_BACKGROUND);
        ctx.fill(panelX + 10, panelY, panelX + PANEL_W - 10, panelY + 3, AppleColors.ACCENT_BLUE);

        ctx.text(this.font, "§b♪ §f" + getTitle().getString(), panelX + 16, panelY + 16, AppleColors.TEXT_PRIMARY);
        ctx.text(this.font, "§7Click a sound to preview · " + selected, panelX + 16, panelY + 30, AppleColors.TEXT_SECONDARY);

        int rowY = listY;
        for (int i = scroll; i < Math.min(scroll + VISIBLE, sounds.length); i++) {
            String s = sounds[i];
            boolean rowHover = mouseX >= listX && mouseX < listX + listW && mouseY >= rowY && mouseY < rowY + ROW_H - 2;
            if (clicked && rowHover) {
                selected = s;
                LoudSound.play(SoundOptions.resolve(s), 1.0f, 1.0f);
            }
            int bg = s.equals(selected) ? 0xFF2E7D32 : (rowHover ? 0xFF3A3A3C : 0xFF2C2C2E);
            ctx.fill(listX, rowY, listX + listW, rowY + ROW_H - 2, bg);
            drawBorder(ctx, listX, rowY, listW, ROW_H - 2, s.equals(selected) ? 0xFF30D158 : 0xFF48484A);
            ctx.text(this.font, s, listX + 6, rowY + 5, 0xFFFFFFFF);
            rowY += ROW_H;
        }

        if (sounds.length > VISIBLE) {
            ctx.text(this.font, "§8" + (scroll + 1) + "-" + Math.min(scroll + VISIBLE, sounds.length) + " / " + sounds.length + " · scroll",
                    panelX + 16, panelY + PANEL_H - 46, AppleColors.TEXT_TERTIARY);
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
