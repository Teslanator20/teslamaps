package com.teslamaps.screen;

import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.render.gui.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** One text field; Enter submits, Esc cancels. */
public class TextPromptScreen extends Screen {
    private static final int W = 240, H = 64;
    private final String prompt;
    private final Consumer<String> callback;
    private EditBox box;
    private int px, py;

    public TextPromptScreen(String prompt, Consumer<String> callback) {
        super(Component.literal(prompt));
        this.prompt = prompt;
        this.callback = callback;
    }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = (height - H) / 2;
        box = new EditBox(font, px + 12, py + 30, W - 24, 16, Component.literal(prompt));
        box.setMaxLength(64);
        addRenderableWidget(box);
        setInitialFocus(box);
        ScreenFit.clamp(this);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
            String text = box.getValue().trim();
            onClose();
            if (!text.isEmpty()) callback.accept(text);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        HudStyle.screenPanel(ctx, px, py, W, H, 8);
        ctx.text(font, "§f" + prompt + " §8(Enter)", px + 12, py + 12, GuiStyle.text());
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
