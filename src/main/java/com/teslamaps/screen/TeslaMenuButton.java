package com.teslamaps.screen;

import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.utils.AnimClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Translucent glass button for the custom main menu: drop shadow, two rounded
 * fills, a top highlight gradient and a hairline outline. The low alphas only
 * read as glass on top of a moving background.
 */
public class TeslaMenuButton extends AbstractWidget {

    private static final float RADIUS = 4.0f;
    private static final float BORDER = 1.0f;
    private static final float SHADOW_OFFSET_Y = 2.0f;
    private static final float SHADOW_BLUR = 6.0f;
    private static final float HIGHLIGHT_FRACTION = 0.35f;
    private static final float HIGHLIGHT_MIN = 6.0f;

    private final Runnable onPress;
    private float hover;

    public TeslaMenuButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        float target = isHoveredOrFocused() && active ? 1.0f : 0.0f;
        hover += (target - hover) * AnimClock.lerpFactor(0.35f);

        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();

        int bodyAlpha = active ? (int) (15 + hover * 8) : 10;
        int borderAlpha = active ? 30 : 18;
        int shadowAlpha = active ? 14 : 9;
        int highlightAlpha = active ? 10 : 8;

        TeslaGui.shadow(ctx, x, y + SHADOW_OFFSET_Y, w, h, RADIUS,
                SHADOW_BLUR, TeslaGui.withAlpha(0x000000, shadowAlpha));

        TeslaGui.roundedRect(ctx, x, y, w, h, RADIUS,
                TeslaGui.withAlpha(0x141A26, active ? 28 : 20));
        TeslaGui.roundedRect(ctx, x, y, w, h, RADIUS,
                TeslaGui.withAlpha(0xFFFFFF, bodyAlpha));

        float highlightHeight = Math.max(HIGHLIGHT_MIN, h * HIGHLIGHT_FRACTION);
        TeslaGui.roundedRectGradient(ctx, x + BORDER, y + BORDER,
                w - BORDER * 2.0f, highlightHeight, Math.max(RADIUS - BORDER, 0.0f),
                TeslaGui.withAlpha(0xFFFFFF, highlightAlpha),
                TeslaGui.withAlpha(0xFFFFFF, 0));

        TeslaGui.roundedOutline(ctx, x + BORDER * 0.5f, y + BORDER * 0.5f,
                w - BORDER, h - BORDER, RADIUS, BORDER,
                TeslaGui.withAlpha(0xFFFFFF, borderAlpha));

        int textColor = active ? 0xFFF0F3FA : 0xBFCED4E0;
        ctx.centeredText(Minecraft.getInstance().font, getMessage(),
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, textColor);
    }

    @Override
    public void onClick(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        if (!active || !visible) return;
        playDownSound(Minecraft.getInstance().getSoundManager());
        onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
