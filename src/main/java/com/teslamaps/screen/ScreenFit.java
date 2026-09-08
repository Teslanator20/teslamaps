package com.teslamaps.screen;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * Safety net against unreachable widgets. A gui scale of 4 or 5 shrinks the screen to as little
 * as 384x216 gui units, and any screen that lays itself out from fixed design sizes then puts its
 * button row past the bottom edge, where it can never be clicked.
 * <p>
 * Screens should still size themselves from {@code width}/{@code height} - this only catches what
 * slips through, so a bad layout costs an ugly overlap instead of a dead button.
 */
public final class ScreenFit {

    private ScreenFit() {}

    private static final int MARGIN = 2;

    /** Call at the end of {@code init()}, after every widget has been added. */
    public static void clamp(Screen screen) {
        int sw = screen.width, sh = screen.height;
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget w)) continue;

            // A widget wider or taller than the window can never fit, so trim it first.
            if (w.getWidth() > sw - MARGIN * 2) w.setWidth(Math.max(8, sw - MARGIN * 2));
            if (w.getHeight() > sh - MARGIN * 2) w.setHeight(Math.max(8, sh - MARGIN * 2));

            w.setX(Math.clamp(w.getX(), MARGIN, Math.max(MARGIN, sw - w.getWidth() - MARGIN)));
            w.setY(Math.clamp(w.getY(), MARGIN, Math.max(MARGIN, sh - w.getHeight() - MARGIN)));
        }
    }
}
