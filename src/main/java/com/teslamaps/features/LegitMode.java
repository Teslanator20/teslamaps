package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class LegitMode {
    private static boolean peeking = false;
    private static boolean peekToggled = false;
    private static boolean peekWasDown = false;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        boolean down = cfg.legitMode
                && mc.player != null && mc.screen == null && mc.getWindow() != null
                && com.teslamaps.utils.KeyNames.isDown(cfg.legitPeekKey);
        if (!cfg.legitPeekToggle) {
            peeking = down;
            peekToggled = false;
        } else {
            if (down && !peekWasDown) peekToggled = !peekToggled;
            if (!cfg.legitMode) peekToggled = false;
            peeking = peekToggled;
        }
        peekWasDown = down;
    }

    /** Legit Mode master switch (affects the map). */
    public static boolean isActive() {
        return TeslaMapsConfig.get().legitMode;
    }

    /** True when Legit Mode should block bannable features (ESP, auto, ...).
     *  Off when "Map Legit Mode Only" is set. */
    public static boolean blocksCheats() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        return cfg.legitMode && !cfg.legitMapOnly;
    }

    /** True when the map should hide unexplored rooms (legit on and not peeking). */
    public static boolean isFiltering() {
        return TeslaMapsConfig.get().legitMode && !peeking;
    }
}
