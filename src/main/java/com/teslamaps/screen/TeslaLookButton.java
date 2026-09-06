package com.teslamaps.screen;

import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.utils.AnimClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Square glass button with an eye, opens the background/skybox picker. */
public class TeslaLookButton extends AbstractWidget {

    private float hover;

    public TeslaLookButton(int x, int y, int size) {
        super(x, y, size, size, Component.literal("TeslaMaps Look"));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHoveredOrFocused();
        hover += ((hovered ? 1.0f : 0.0f) - hover) * AnimClock.lerpFactor(0.35f);

        TeslaGui.glassButton(ctx, getX(), getY(), getWidth(), getHeight(), hovered, true);

        int alpha = (int) (150 + hover * 90);
        TeslaGui.eyeIcon(ctx, getX() + getWidth() / 2.0f, getY() + getHeight() / 2.0f,
                getWidth() * 0.66f, TeslaGui.withAlpha(0xFFFFFF, alpha));
    }

    @Override
    public void onClick(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        playDownSound(Minecraft.getInstance().getSoundManager());
        Minecraft.getInstance().setScreen(new LookScreen(Minecraft.getInstance().screen));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
