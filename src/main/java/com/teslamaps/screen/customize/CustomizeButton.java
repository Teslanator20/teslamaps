package com.teslamaps.screen.customize;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Small button in the player inventory that opens the customize screen. Ported from Skyblocker. */
public class CustomizeButton extends AbstractWidget {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("teslamaps", "customize_screen/button");

    private CustomizeButton(int x, int y) {
        super(x, y, 10, 10, Component.empty());
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!SkyblockUtils.isInSkyblock()) return;
            if (!TeslaMapsConfig.get().showCustomizeButton) return;
            if (!(screen instanceof InventoryScreen inventoryScreen)) return;

            HandledScreenAccessor accessor = (HandledScreenAccessor) inventoryScreen;
            CustomizeButton button = new CustomizeButton(accessor.getX() + 63, accessor.getY() + 10);
            Screens.getWidgets(inventoryScreen).add(button);
        });
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TEXTURE, getX(), getY(), getWidth(), getHeight(),
                isHovered() ? 0xFFFAFA96 : 0x80FFFFFF);
        this.handleCursor(graphics);
    }

    @Override
    public void onClick(MouseButtonEvent click, boolean doubled) {
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new CustomizeScreen(client.screen, false));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {}
}
