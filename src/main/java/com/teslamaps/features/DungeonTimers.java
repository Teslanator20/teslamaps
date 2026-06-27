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
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DungeonTimers {

    public static final String SAMPLE_TEXT = "Warp: 30.00s"; // for HUD-editor sizing
    public record Row(String text, ItemStack icon) {}
    private static final Map<String, Row> lines = new LinkedHashMap<>();

    // the Bonzo/Spirit/Phoenix masks render as their own movable block
    private static final Set<String> INVINCIBILITY_KEYS = Set.of("bonzo", "spirit", "phoenix");

    private static final float ICON = 0.625f; // 16px -> 10px, ~text height

    public static void set(String key, String text) { lines.put(key, new Row(text, null)); }
    public static void set(String key, String text, ItemStack icon) { lines.put(key, new Row(text, icon)); }
    public static void clear(String key) { lines.remove(key); }
    public static void clearAll() { lines.clear(); }

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        if (lines.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();

        List<Row> inv = new ArrayList<>();
        List<Row> rest = new ArrayList<>();
        for (Map.Entry<String, Row> e : lines.entrySet()) {
            (INVINCIBILITY_KEYS.contains(e.getKey()) ? inv : rest).add(e.getValue());
        }
        if (!rest.isEmpty()) drawRows(ctx, mc, c.dungeonTimersX, c.dungeonTimersY, c.dungeonTimersScale, rest);
        boolean showInv = !c.invincibilityOnlyInBoss || com.teslamaps.dungeon.DungeonManager.isInBoss();
        if (showInv && !inv.isEmpty()) drawRows(ctx, mc, c.invincibilityX, c.invincibilityY, c.invincibilityScale, inv);
    }

    // HUD-editor preview: the warp/purple/etc block
    public static void draw(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale) {
        drawRows(ctx, mc, x, y, scale, List.of(
                new Row("§eWarp§f: §c30.00s", null), new Row("§dPurple§f: §d2.10s", null)));
    }

    // HUD-editor preview: the invincibility (skull) block
    public static void drawInvincibility(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale) {
        drawRows(ctx, mc, x, y, scale, List.of(
                new Row("§a§lREADY", com.teslamaps.features.TimerTriggers.maskIcon(0)),
                new Row("§c30.00s", com.teslamaps.features.TimerTriggers.maskIcon(1)),
                new Row("§a2.50s", com.teslamaps.features.TimerTriggers.maskIcon(2))));
    }

    private static void drawRows(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float scale, Iterable<Row> rows) {
        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int row = 0;
        for (Row r : rows) {
            if (r.icon() != null && !r.icon().isEmpty()) {
                pose.pushMatrix();
                pose.translate(0, row);
                pose.scale(ICON, ICON);
                ctx.item(r.icon(), 0, 0);
                pose.popMatrix();
                ctx.text(mc.font, r.text(), 12, row + 1, 0xFFFFFFFF);
            } else {
                ctx.text(mc.font, r.text(), 0, row, 0xFFFFFFFF);
            }
            row += 11;
        }
        pose.popMatrix();
    }
}
