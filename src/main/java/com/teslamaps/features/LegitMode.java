/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * Copyright (c) 2026 Teslanator20.
 *
 * See the LICENSE file in the project root for full terms.
 */
package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class LegitMode {
    private static boolean peeking = false;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        peeking = cfg.legitMode && cfg.legitPeekKey >= 0
                && mc.player != null && mc.screen == null && mc.getWindow() != null
                && GLFW.glfwGetKey(mc.getWindow().handle(), cfg.legitPeekKey) == GLFW.GLFW_PRESS;
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
