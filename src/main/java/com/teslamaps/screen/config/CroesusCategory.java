package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class CroesusCategory {

    private CroesusCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.toggle("Croesus Profit Overlay", () -> config.croesusProfitOverlay, v -> config.croesusProfitOverlay = v);
        b.toggle("Highlight Most Profitable", () -> config.croesusHighlightBest, v -> config.croesusHighlightBest = v);
        b.toggle("Gray Out Other Chests", () -> config.croesusDimOthers, v -> config.croesusDimOthers = v);
        b.toggle("Highlight Unopened (green)", () -> config.croesusHighlightUnopened, v -> config.croesusHighlightUnopened = v);
        b.toggle("Hide Opened Chests", () -> config.croesusHideOpened, v -> config.croesusHideOpened = v);
        b.toggle("Compact Mode (profit only)", () -> config.croesusCompact, v -> config.croesusCompact = v);
    }
}
