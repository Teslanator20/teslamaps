package com.teslamaps.features.chattabs;

// Colors ported from Skysoft's PixelButtonRenderer
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ChatTabButton extends AbstractWidget {

    private static final int BORDER = 0xFF111315;
    private static final int NORMAL = 0xFF3B4147;
    private static final int HOVERED = 0xFF4B5963;
    private static final int SELECTED = 0xFF286B98;
    private static final int ACCENT = 0xFF65C2FF;
    private static final int HIGHLIGHT = 0xFF6F7880;
    private static final int INSET = 0xFF1D2226;
    private static final int TEXT = 0xFFFFFFFF;

    private final ChatTabs.Channel channel;
    private final Runnable onPress;

    public ChatTabButton(ChatTabs.TabBounds bounds, Runnable onPress) {
        super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), Component.literal(bounds.channel().label));
        this.channel = bounds.channel();
        this.onPress = onPress;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        onPress.run();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        boolean selected = ChatTabs.selected() == channel;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        // chamfered border
        g.fill(x + 1, y, x + w - 1, y + h, BORDER);
        g.fill(x, y + 1, x + w, y + h - 1, BORDER);

        // fill
        int fill = selected ? SELECTED : (isHovered() ? HOVERED : NORMAL);
        int ix = x + 1, iy = y + 1, iw = w - 2, ih = h - 2;
        g.fill(ix + 1, iy, ix + iw - 1, iy + ih, fill);
        g.fill(ix, iy + 1, ix + iw, iy + ih - 1, fill);

        // bevel
        int tl = selected ? INSET : HIGHLIGHT;
        int br = selected ? HIGHLIGHT : INSET;
        g.fill(ix + 1, iy, ix + iw - 1, iy + 1, tl);
        g.fill(ix, iy + 1, ix + 1, iy + ih - 1, tl);
        g.fill(ix + 1, iy + ih - 1, ix + iw - 1, iy + ih, br);
        g.fill(ix + iw - 1, iy + 1, ix + iw, iy + ih - 1, br);

        if (selected) {
            g.fill(ix + 1, iy + ih - 2, ix + iw - 1, iy + ih - 1, ACCENT);
        }

        var font = Minecraft.getInstance().font;
        int tx = x + (w - font.width(getMessage()) + 1) / 2;
        int ty = y + (h - font.lineHeight + 2) / 2;
        g.text(font, getMessage().getString(), tx, ty, TEXT, false);
    }

    // arrow/tab focus navigation would steal the chat input's focus
    @Override
    public net.minecraft.client.gui.ComponentPath nextFocusPath(
            net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
        return null;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
