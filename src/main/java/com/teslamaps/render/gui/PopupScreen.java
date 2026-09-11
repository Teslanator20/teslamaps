package com.teslamaps.render.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Bare-bones popup that keeps the screen underneath visible. Ported from Skyblocker. */
public class PopupScreen extends Screen {
    private static final Identifier BACKGROUND_TEXTURE = Identifier.withDefaultNamespace("popup/background");
    public final Screen backgroundScreen;

    protected PopupScreen(Component title, Screen backgroundScreen) {
        super(title);
        this.backgroundScreen = backgroundScreen;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.backgroundScreen);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.backgroundScreen.extractBackground(graphics, -1, -1, a);
        graphics.nextStratum();
        this.backgroundScreen.extractRenderState(graphics, -1, -1, a);
        graphics.nextStratum();
        this.extractTransparentBackground(graphics);
    }

    /** Inner position and size of the popup, not the outer frame. */
    public static void extractPopupBackground(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, x - 18, y - 18, width + 36, height + 36);
    }

    @Override
    protected void init() {
        super.init();
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.backgroundScreen.resize(this.width, this.height);
    }

    @Override
    public void added() {
        super.added();
        this.backgroundScreen.clearFocus();
    }
}
