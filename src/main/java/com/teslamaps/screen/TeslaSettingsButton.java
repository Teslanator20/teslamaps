package com.teslamaps.screen;

import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.utils.AnimClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Square glass button with a gear, opens the TeslaMaps settings. */
public class TeslaSettingsButton extends AbstractWidget {

    private float hover;
    private final Runnable action;

    public TeslaSettingsButton(int x, int y, int size) {
        this(x, y, size, null);
    }

    public TeslaSettingsButton(int x, int y, int size, Runnable action) {
        super(x, y, size, size, Component.literal("TeslaMaps Settings"));
        this.action = action;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHoveredOrFocused();
        hover += ((hovered ? 1.0f : 0.0f) - hover) * AnimClock.lerpFactor(0.35f);

        TeslaGui.glassButton(ctx, getX(), getY(), getWidth(), getHeight(), hovered, true);

        int gearAlpha = (int) (150 + hover * 90);
        TeslaGui.gearIcon(ctx, getX() + getWidth() / 2.0f, getY() + getHeight() / 2.0f,
                getWidth() * 0.62f, TeslaGui.withAlpha(0xFFFFFF, gearAlpha));
    }

    @Override
    public void onClick(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        playDownSound(Minecraft.getInstance().getSoundManager());
        if (action != null) action.run();
        else Minecraft.getInstance().setScreen(new MapConfigScreen());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
