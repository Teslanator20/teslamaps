/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file adapts the Dark Mode feature from NoammAddons
 * (https://github.com/Noamm9/NoammAddons, CC0 1.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class DarkMode {

    public static void renderUnderHud(GuiGraphicsExtractor ctx) {
        if (TeslaMapsConfig.get().darkModeTintHud) return;
        draw(ctx);
    }

    public static void renderOverHud(GuiGraphicsExtractor ctx) {
        if (!TeslaMapsConfig.get().darkModeTintHud) return;
        draw(ctx);
    }

    private static void draw(GuiGraphicsExtractor ctx) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.darkMode) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        int alpha = Math.round(Math.max(1, Math.min(100, config.darkModeOpacity)) / 100f * 255f);
        int color = (alpha << 24);
        ctx.fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), color);
    }
}
