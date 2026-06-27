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
import com.teslamaps.map.CheckmarkState;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.utils.LoudSound;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

// Alerts when a room you never entered jumps straight to a checkmark (white or green) — i.e. a teammate
// rush/insta-cleared it. Fed the per-scan map checkmark state from MapScanner.
public class InstaClearAlert {

    private static final Map<String, CheckmarkState> prevState = new HashMap<>();
    private static final Set<String> visited = new HashSet<>();

    private static String key(DungeonRoom room) {
        int[] p = room.getPrimaryComponent();
        return p[0] + "," + p[1];
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().instaClearAlert) return;
        if (!DungeonManager.isInDungeon()) { reset(); return; }
        DungeonRoom cur = DungeonManager.getCurrentRoom();
        if (cur != null && cur.getName() != null) visited.add(key(cur));
    }

    // called from MapScanner with the freshly-scanned map checkmark for a room
    public static void onRoomState(DungeonRoom room, CheckmarkState state) {
        if (!TeslaMapsConfig.get().instaClearAlert) return;
        if (room == null || room.getName() == null) return;

        String k = key(room);
        CheckmarkState prev = prevState.get(k);
        boolean cleared = state == CheckmarkState.GREEN || state == CheckmarkState.WHITE;
        boolean wasCleared = prev == CheckmarkState.GREEN || prev == CheckmarkState.WHITE;

        if (cleared && !wasCleared && !visited.contains(k)) {
            alert(room, state);
        }
        prevState.put(k, state);
    }

    private static void alert(DungeonRoom room, CheckmarkState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String mark = state == CheckmarkState.GREEN ? "§a\u2714" : "§f\u2714";
        mc.player.sendSystemMessage(Component.literal(
                "§c[TeslaMaps] §fRush insta-clear: §e" + room.getName() + " " + mark));
        LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), TeslaMapsConfig.get().instaClearVolume, 1.6f);
    }

    public static void reset() {
        prevState.clear();
        visited.clear();
    }
}
