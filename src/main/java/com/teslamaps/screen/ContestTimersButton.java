package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Hides or shows the contest timers straight from the inventory. */
public class ContestTimersButton extends AbstractWidget {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("teslamaps", "customize_screen/button");

    private ContestTimersButton(int x, int y) {
        super(x, y, 10, 10, Component.empty());
        updateTooltip();
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!SkyblockUtils.isInSkyblock()) return;
            TeslaMapsConfig c = TeslaMapsConfig.get();
            if (!c.section("Contest Timers") || !c.contestTimerButton) return;
            if (!(screen instanceof InventoryScreen inventoryScreen)) return;

            HandledScreenAccessor accessor = (HandledScreenAccessor) inventoryScreen;
            Screens.getWidgets(inventoryScreen)
                    .add(new ContestTimersButton(accessor.getX() + 63, accessor.getY() + 22));
        });
    }

    private void updateTooltip() {
        setTooltip(Tooltip.create(Component.literal(
                TeslaMapsConfig.get().contestTimer ? "Hide contest timers" : "Show contest timers")));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        boolean on = TeslaMapsConfig.get().contestTimer;
        int tint = isHovered() ? 0xFFFAFA96 : (on ? 0xFF8AE68A : 0x80FFFFFF);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TEXTURE, getX(), getY(),
                getWidth(), getHeight(), tint);
        graphics.centeredText(net.minecraft.client.Minecraft.getInstance().font, "C",
                getX() + getWidth() / 2, getY() + 1, on ? 0xFFFFFFFF : 0xFF808080);
        this.handleCursor(graphics);
    }

    @Override
    public void onClick(MouseButtonEvent click, boolean doubled) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        c.contestTimer = !c.contestTimer;
        TeslaMapsConfig.save();
        updateTooltip();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {}
}
