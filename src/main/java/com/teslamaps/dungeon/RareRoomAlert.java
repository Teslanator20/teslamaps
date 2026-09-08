package com.teslamaps.dungeon;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.utils.LoudSound;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

// Title + chat when a high value secret room is scanned. Concept from Skyblocker's RareRoomAlert.
public class RareRoomAlert {

    private static final Set<String> alerted = new HashSet<>();

    // called from RoomScanner right after a newly identified room is added to the grid
    public static void onRoomDiscovered(DungeonRoom room) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.rareRoomAlert) return;
        if (room == null) return;

        String name = room.getName();
        if (!"Trinity".equals(name)) return;

        // merges can identify the same room twice
        if (!alerted.add(name)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.gui.setTitle(Component.literal("§d§l" + name.toUpperCase()));
        mc.player.sendSystemMessage(Component.literal("§c[TeslaMaps] §fRare room: §d" + name));
        LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), config.rareRoomVolume, 1.8f);
    }

    public static void reset() {
        alerted.clear();
    }
}
