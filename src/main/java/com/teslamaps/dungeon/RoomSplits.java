/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.dungeon;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.map.DungeonRoom;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

// Live per-room pace HUD: times how long you spend in each room as you move through the dungeon,
// showing the current room counting up plus the last few completed rooms.
public class RoomSplits {

    public record Entry(String name, long ms) {}

    private static final int MAX_HISTORY = 6;
    private static final List<Entry> history = new ArrayList<>(); // newest first
    private static String currentRoom = null;
    private static long enterTime = 0L;

    public static void tick() {
        if (!TeslaMapsConfig.get().roomSplits) return;
        if (!DungeonManager.isInDungeon()) { reset(); return; }

        DungeonRoom room = DungeonManager.getCurrentRoom();
        String name = room != null ? room.getName() : null;
        if (name == null) return; // between/unknown room — keep timing the current one

        if (!name.equals(currentRoom)) {
            long now = System.currentTimeMillis();
            if (currentRoom != null && enterTime != 0L) {
                history.add(0, new Entry(currentRoom, now - enterTime));
                while (history.size() > MAX_HISTORY) history.remove(history.size() - 1);
            }
            currentRoom = name;
            enterTime = now;
        }
    }

    public static void render(GuiGraphicsExtractor ctx, DeltaTracker delta) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.roomSplits || !DungeonManager.isInDungeon()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(c.roomSplitsX, c.roomSplitsY);
        pose.scale(c.roomSplitsScale, c.roomSplitsScale);

        int y = 0;
        ctx.text(mc.font, "§6§lRoom Splits", 0, y, 0xFFFFFFFF);
        y += 10;
        if (currentRoom != null) {
            ctx.text(mc.font, "§e\u25b6 " + currentRoom + " §f" + fmt(System.currentTimeMillis() - enterTime), 0, y, 0xFFFFFFFF);
            y += 9;
        }
        for (Entry e : history) {
            ctx.text(mc.font, "§7" + e.name() + " §f" + fmt(e.ms()), 0, y, 0xFFFFFFFF);
            y += 9;
        }
        pose.popMatrix();
    }

    public static void reset() {
        history.clear();
        currentRoom = null;
        enterTime = 0L;
    }

    private static String fmt(long ms) {
        long totalSec = ms / 1000;
        return String.format("%d:%02d.%d", totalSec / 60, totalSec % 60, (ms % 1000) / 100);
    }
}
