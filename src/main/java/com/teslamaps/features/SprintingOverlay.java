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
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class SprintingOverlay {

    public static final String SAMPLE_TEXT = "Not Sprinting"; // widest state, for HUD-editor sizing/preview

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        if (!TeslaMapsConfig.get().sprintingOverlay) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.screen != null) return;

        boolean sprinting = mc.player.isSprinting();
        String text = (sprinting ? "§aSprinting" : "§cNot Sprinting");
        TeslaMapsConfig config = TeslaMapsConfig.get();
        draw(context, mc, config.sprintingX, config.sprintingY, config.sprintingScale, text);
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        draw(context, mc, x, y, scale, "§aSprinting");
    }

    public static void draw(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, String text) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        context.text(mc.font, text, 0, 0, 0xFFFFFFFF);
        pose.popMatrix();
    }
}
