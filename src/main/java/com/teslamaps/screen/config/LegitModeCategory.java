package com.teslamaps.screen.config;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.GuiSounds;
import com.teslamaps.render.gui.GuiStyle;
import com.teslamaps.screen.*;
import net.minecraft.client.Minecraft;

public final class LegitModeCategory {

    private LegitModeCategory() {}

    public static void build(ConfigBuilder b, TeslaMapsConfig config) {
        b.label("Legit Mode");
        b.toggle("Enable Legit Mode", () -> config.legitMode, v -> config.legitMode = v);
        b.toggle("Map Legit Mode Only", () -> config.legitMapOnly, v -> config.legitMapOnly = v);
        b.keybind("Peek Key (hold to reveal map)", () -> config.legitPeekKey, v -> config.legitPeekKey = v);
        b.info("Map: only explored rooms + doors (+ 1x1 guess).");
        b.info("Full mode also blocks all ESP, Auto Wish/GFS.");
        b.info("Map Only: just the map, cheats stay on.");
        b.info("Solvers, waypoints, timers always stay on.");
    }
}
