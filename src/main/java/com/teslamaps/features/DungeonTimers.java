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

import java.util.LinkedHashMap;
import java.util.Map;

public class DungeonTimers {

    public static final String SAMPLE_TEXT = "Warp: 30.00s"; // for HUD-editor sizing
    private static final Map<String, String> lines = new LinkedHashMap<>();

    public static void set(String key, String text) { lines.put(key, text); }
    public static void clear(String key) { lines.remove(key); }
    public static void clearAll() { lines.clear(); }

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        if (lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        draw(ctx, mc, c.dungeonTimersX, c.dungeonTimersY, c.dungeonTimersScale, lines.values().toArray(new String[0]));
    }

    public static void draw(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale) {
        draw(ctx, mc, x, y, scale, new String[]{"§eWarp§f: §c30.00s", "§dBonzo§f: §a§lREADY"});
    }

    private static void draw(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale, String[] rows) {
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int row = 0;
        for (String s : rows) { ctx.text(mc.font, s, 0, row, 0xFFFFFFFF); row += 10; }
        pose.popMatrix();
    }
}
