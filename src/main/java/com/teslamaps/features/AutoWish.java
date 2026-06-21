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

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.player.PlayerTracker;
import net.minecraft.client.Minecraft;

public class AutoWish {

    private static int scheduledDropTick = -1;
    private static boolean dropAll = false;

    public static void onChatMessage(String message) {
        if (!TeslaMapsConfig.get().autoWish) return;
        if (!DungeonManager.isInDungeon()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!isHealer()) {
            return;
        }

        int delay = -1;

        if (message.contains("Maxor is enraged!")) {
            delay = 1;
        }
        else if (message.contains("[BOSS] Goldor: You have done it, you destroyed the factory")) {
            delay = 1;
        }
        else if (message.contains("[BOSS] Sadan: My giants! Unleashed!")) {
            delay = 25;
        }

        if (delay > 0) {
            scheduleWish(delay);
            TeslaMaps.LOGGER.info("[AutoWish] Scheduled wish in {} ticks", delay);
        }
    }

    private static final java.util.Random RANDOM = new java.util.Random();

    private static void scheduleWish(int delayTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int jittered = Math.max(1, delayTicks + RANDOM.nextInt(21) - 10);
        scheduledDropTick = mc.player.tickCount + jittered;
        dropAll = false; // Use normal drop, not drop all
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().autoWish) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (scheduledDropTick > 0 && mc.player.tickCount >= scheduledDropTick) {
            mc.player.drop(dropAll);
            TeslaMaps.LOGGER.info("[AutoWish] Used wish!");

            scheduledDropTick = -1;
            dropAll = false;
        }
    }

    public static void reset() {
        scheduledDropTick = -1;
        dropAll = false;
    }

    private static boolean isHealer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        String playerName = mc.player.getName().getString();

        for (PlayerTracker.DungeonPlayer dp : PlayerTracker.getPlayers()) {
            if (dp.getName().equals(playerName)) {
                String dungeonClass = dp.getDungeonClass();
                return "Healer".equals(dungeonClass);
            }
        }

        return false;
    }
}
