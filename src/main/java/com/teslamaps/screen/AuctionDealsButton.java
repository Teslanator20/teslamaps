package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.AuctionPriceCompare;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Toggles the auction value-deal highlight from inside the auction browser. */
public class AuctionDealsButton extends AbstractWidget {

    private static final String ON = "§aDeals: ON";
    private static final String OFF = "§7Deals: OFF";

    private AuctionDealsButton(int x, int y, int width) {
        super(x, y, width, 12, Component.empty());
        updateTooltip();
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!SkyblockUtils.isInSkyblock()) return;
            TeslaMapsConfig c = TeslaMapsConfig.get();
            if (!c.section("Inventory") || !c.auctionCompareButton) return;
            if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
            if (!AuctionPriceCompare.isAuctionTitle(cs.getTitle().getString())) return;

            HandledScreenAccessor accessor = (HandledScreenAccessor) cs;
            int w = client.font.width(OFF) + 10;
            Screens.getWidgets(cs).add(new AuctionDealsButton(
                    accessor.getX() + accessor.getImageWidth() - w, accessor.getY() - 14, w));
        });
    }

    private void updateTooltip() {
        setTooltip(Tooltip.create(Component.literal(TeslaMapsConfig.get().auctionPriceCompare
                ? "Hide the estimated value highlight"
                : "Tint listings by price against the estimated item value")));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        boolean on = TeslaMapsConfig.get().auctionPriceCompare;
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
                isHovered() ? 0xC0303030 : 0xA0101010);
        graphics.centeredText(Minecraft.getInstance().font, on ? ON : OFF,
                getX() + getWidth() / 2, getY() + 2, 0xFFFFFFFF);
        this.handleCursor(graphics);
    }

    @Override
    public void onClick(MouseButtonEvent click, boolean doubled) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        c.auctionPriceCompare = !c.auctionPriceCompare;
        TeslaMapsConfig.save();
        updateTooltip();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {}
}
