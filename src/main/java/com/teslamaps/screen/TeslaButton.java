package com.teslamaps.screen;

import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.utils.AnimClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Text button in the glass style, so screens do not mix in vanilla's stone buttons. */
public class TeslaButton extends AbstractWidget {

    private final Runnable action;
    private float hover;

    public TeslaButton(int x, int y, int width, int height, String label, Runnable action) {
        super(x, y, width, height, Component.literal(label));
        this.action = action;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHoveredOrFocused();
        hover += ((hovered ? 1.0f : 0.0f) - hover) * AnimClock.lerpFactor(0.35f);

        HudStyle.screenButton(ctx, getX(), getY(), getWidth(), getHeight(), hovered, active);

        Minecraft mc = Minecraft.getInstance();
        String label = getMessage().getString();
        int textY = getY() + (getHeight() - mc.font.lineHeight) / 2 + 1;
        ctx.centeredText(mc.font, label, getX() + getWidth() / 2, textY,
                active ? 0xFFFFFFFF : 0xFF808080);
    }

    @Override
    public void onClick(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        playDownSound(Minecraft.getInstance().getSoundManager());
        action.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
