/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class Zoom {
    private static boolean zoomed = false;
    private static int originalFov = 0;
    private static float currentFov = 30f;   // live FOV, adjusted by scroll while held

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        boolean held = cfg.zoomEnabled && cfg.zoomKey >= 0
                && mc.player != null && mc.screen == null && mc.getWindow() != null
                && GLFW.glfwGetKey(mc.getWindow().handle(), cfg.zoomKey) == GLFW.GLFW_PRESS;

        if (held && !zoomed) {
            originalFov = mc.options.fov().get();
            currentFov = clamp(cfg.zoomFov);     // reset to default on every fresh zoom-in
            mc.options.fov().set(Math.round(currentFov));
            zoomed = true;
        } else if (!held && zoomed) {
            mc.options.fov().set(originalFov);
            zoomed = false;
        } else if (held) {
            mc.options.fov().set(Math.round(currentFov));
        }
    }

    /** Called from MouseScrollMixin. Returns true if the scroll was consumed for zooming. */
    public static boolean onScroll(double vertical) {
        if (!zoomed || vertical == 0) return false;
        currentFov -= (float) vertical * Math.max(1f, currentFov * 0.1f);  // scroll up = zoom in
        currentFov = clamp(currentFov);
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) mc.options.fov().set(Math.round(currentFov));
        return true;
    }

    private static float clamp(float fov) {
        return Math.max(1f, Math.min(110f, fov));
    }
}
