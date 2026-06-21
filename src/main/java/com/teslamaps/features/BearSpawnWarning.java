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
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.utils.LoudSound;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;

public class BearSpawnWarning {
    private static final BlockPos WATCH_POS = new BlockPos(7, 77, 34);
    private static final int SCAN_INTERVAL = 1;    // scan every tick so the timer fires as fast as possible
    private static final int ALERT_DURATION = 68;  // 3.4s countdown (the bear spawns ~3.4s after the lantern lights)

    private static int tickCounter = 0;
    private static boolean wasSeaLantern = false;
    private static int alertTicks = 0;

    public static void tick() {
        if (alertTicks > 0) alertTicks--;

        if (!TeslaMapsConfig.get().bearSpawnWarning) {
            wasSeaLantern = false;
            return;
        }

        var floor = DungeonManager.getCurrentFloor();
        if (floor == null || floor.getLevel() != 4) {
            wasSeaLantern = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        double dx = mc.player.getX() - (WATCH_POS.getX() + 0.5);
        double dz = mc.player.getZ() - (WATCH_POS.getZ() + 0.5);
        if (dx * dx + dz * dz > 200 * 200) { // not anywhere near the boss arena
            wasSeaLantern = false;
            return;
        }

        if (++tickCounter < SCAN_INTERVAL) return;
        tickCounter = 0;

        boolean isSeaLantern = mc.level.getBlockState(WATCH_POS).is(Blocks.SEA_LANTERN);
        if (isSeaLantern && !wasSeaLantern) {
            trigger();
        }
        wasSeaLantern = isSeaLantern;
    }

    private static void trigger() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        alertTicks = ALERT_DURATION;
        float vol = config.bearSpawnVolume;
        if (config.bearSpawnWardenSound) LoudSound.play(SoundEvents.WARDEN_EMERGE, vol, 1.0f);
        if (config.bearSpawnWitherSound) LoudSound.play(SoundEvents.WITHER_DEATH, vol, 1.0f);
    }

    public static final String ALERT_TEXT = "3.4"; // representative sample for HUD-editor width/preview

    public static void render(GuiGraphicsExtractor context, DeltaTracker delta) {
        if (alertTicks <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        TeslaMapsConfig config = TeslaMapsConfig.get();
        String text = String.format("%.1f", alertTicks / 20.0);
        drawAlert(context, mc, config.bearSpawnX, config.bearSpawnY, config.bearSpawnScale, text);
    }

    public static void drawAlert(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale) {
        drawAlert(context, mc, x, y, scale, ALERT_TEXT);
    }

    public static void drawAlert(GuiGraphicsExtractor context, Minecraft mc, int x, int y, float scale, String text) {
        var pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        context.text(mc.font, text, 0, 0, 0xFFFF0000);
        pose.popMatrix();
    }
}
