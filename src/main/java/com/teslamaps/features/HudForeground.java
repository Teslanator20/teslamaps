package com.teslamaps.features;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * While the inventory is open the draggable panels move from the HUD pass
 * (under the screen) to the screen pass (over it), so they stay readable and
 * clickable. Registered via Feature.hudForeground; RecipeBookOverlayMixin
 * draws them here instead.
 */
public class HudForeground {

    public static boolean active() {
        return Minecraft.getInstance().gui.screen()
                instanceof net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
    }

    public static void render(GuiGraphicsExtractor g) {
        if (!active()) return;
        com.teslamaps.debug.Perf.begin("HudForeground");
        com.teslamaps.registry.FeatureRegistry.foreground(g, Minecraft.getInstance().getDeltaTracker());
        com.teslamaps.debug.Perf.end();
    }
}
