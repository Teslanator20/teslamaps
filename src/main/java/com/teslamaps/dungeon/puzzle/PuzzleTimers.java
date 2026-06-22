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
package com.teslamaps.dungeon.puzzle;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.CheckmarkState;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.RoomType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class PuzzleTimers {

    private static String current = null;
    private static long startMs = 0L;
    private static boolean reported = false;

    public static void tick() {
        if (!TeslaMapsConfig.get().puzzleTimers || !DungeonManager.isInDungeon()) { current = null; return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        DungeonRoom room = DungeonManager.getCurrentRoom();
        if (room == null || room.getType() != RoomType.PUZZLE || room.getName() == null) { current = null; return; }

        String name = room.getName();
        if (!name.equals(current)) {
            current = name;
            startMs = System.currentTimeMillis();
            reported = false;
        }
        if (reported) return;

        CheckmarkState st = room.getCheckmarkState();
        if (st == CheckmarkState.WHITE || st == CheckmarkState.GREEN) {
            reported = true;
            double secs = (System.currentTimeMillis() - startMs) / 1000.0;
            TeslaMapsConfig c = TeslaMapsConfig.get();
            Double pb = c.puzzlePbs.get(name);
            boolean newPb = pb == null || secs < pb;
            String suffix;
            if (newPb) {
                suffix = pb == null ? " §6§l(NEW PB!)" : String.format(" §6§l(NEW PB!§r §7old: %.2fs§6§l)", pb);
                c.puzzlePbs.put(name, secs);
                TeslaMapsConfig.save();
            } else {
                suffix = String.format(" §7(PB: %.2fs)", pb);
            }
            mc.player.sendSystemMessage(Component.literal(String.format("§b[TeslaMaps] §f%s solved in §a%.2fs%s", name, secs, suffix)));
        } else if (st == CheckmarkState.FAILED) {
            reported = true;
        }
    }
}
