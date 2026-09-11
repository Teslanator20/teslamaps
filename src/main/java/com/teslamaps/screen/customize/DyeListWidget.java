package com.teslamaps.screen.customize;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * One-column list that only ever scrolls by whole rows, so no entry is ever sliced
 * at the top or bottom edge. Vanilla's ScrollableLayout scrolls per pixel and does.
 */
public class DyeListWidget extends AbstractContainerWidget {
    public static final int ROW = 20;

    private final List<AbstractWidget> rows;

    public DyeListWidget(int width, int visibleRows, List<AbstractWidget> rows) {
        super(0, 0, width, visibleRows * ROW, Component.literal("Dye List"), AbstractScrollArea.defaultSettings(6));
        this.rows = rows;
        layoutRows();
    }

    private void layoutRows() {
        int rowWidth = getWidth() - AbstractScrollArea.SCROLLBAR_WIDTH;
        int y = getY() - (int) scrollAmount();
        for (AbstractWidget row : rows) {
            row.setWidth(rowWidth);
            row.setX(getX());
            row.setY(y);
            y += ROW;
        }
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        layoutRows();
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        layoutRows();
    }

    @Override
    public void setScrollAmount(double amount) {
        // the clamp in super keeps this a multiple of ROW because maxScrollAmount is one
        super.setScrollAmount(Math.round(amount / ROW) * (double) ROW);
        layoutRows();
    }

    @Override
    protected int contentHeight() {
        return rows.size() * ROW;
    }

    @Override
    protected double scrollRate() {
        return ROW;
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return rows;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.enableScissor(getX(), getY(), getRight(), getBottom());
        for (AbstractWidget row : rows) {
            if (row.getBottom() <= getY() || row.getY() >= getBottom()) continue;
            row.extractRenderState(graphics, mouseX, mouseY, a);
        }
        extractScrollbar(graphics, mouseX, mouseY);
        graphics.disableScissor();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {}

    /** A section title that takes exactly one row, so the whole list stays on the grid. */
    public static class Header extends AbstractWidget {
        public Header(Component text) {
            super(0, 0, 150, ROW, text);
            this.active = false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            graphics.centeredText(Minecraft.getInstance().font, getMessage(),
                    getX() + getWidth() / 2, getY() + (ROW - 9) / 2, 0xFFFFFFFF);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {}
    }
}
