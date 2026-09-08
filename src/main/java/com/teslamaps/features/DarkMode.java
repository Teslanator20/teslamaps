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
