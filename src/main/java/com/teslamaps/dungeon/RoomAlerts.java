package com.teslamaps.dungeon;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.map.CheckmarkState;
import com.teslamaps.map.DoorType;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.map.RoomType;
import com.teslamaps.registry.Feature;
import com.teslamaps.scanner.DoorScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

// Devonian CurrentRoomCleared.kt + NoammAddons RoomAlerts.kt
public class RoomAlerts {

    private static DungeonRoom lastRoom;
    private static CheckmarkState lastState;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("RoomAlerts").section("Score & Splits")
                .when(() -> (cfg().roomClearedAlert || cfg().roomSecretsDoneAlert) && DungeonManager.isInDungeon())
                .tick(RoomAlerts::tick);
    }

    private static void tick() {
        DungeonRoom room = DungeonManager.isInBossArea() ? null : DungeonManager.getCurrentRoom();
        if (room == null || room.getName() == null) {
            lastRoom = null;
            lastState = null;
            return;
        }
        CheckmarkState state = room.getCheckmarkState();
        if (room == lastRoom && state != lastState) {
            TeslaMapsConfig c = cfg();
            boolean wasCleared = lastState == CheckmarkState.WHITE || lastState == CheckmarkState.GREEN;
            boolean cleared = state == CheckmarkState.WHITE || state == CheckmarkState.GREEN;
            if (c.roomClearedAlert && cleared && !wasCleared && room.getType() != RoomType.FAIRY
                    && !(c.roomClearedNoBlood && room.getType() == RoomType.BLOOD)
                    && (!c.roomClearedOnlyBloodRush || hasKeyDoor(room))) {
                alert("§aRoom Cleared!");
            } else if (c.roomSecretsDoneAlert && lastState == CheckmarkState.WHITE && state == CheckmarkState.GREEN
                    && room.getSecrets() > 0) {
                alert("§bSecrets Done!");
            }
        }
        lastRoom = room;
        lastState = state;
    }

    private static boolean hasKeyDoor(DungeonRoom room) {
        for (int[] c : room.getComponents()) {
            if (keyDoor(c[0], c[1], c[0] + 1, c[1]) || keyDoor(c[0], c[1], c[0] - 1, c[1])
                    || keyDoor(c[0], c[1], c[0], c[1] + 1) || keyDoor(c[0], c[1], c[0], c[1] - 1)) return true;
        }
        return false;
    }

    private static boolean keyDoor(int x1, int z1, int x2, int z2) {
        DoorType t = DoorScanner.getDoorType(x1, z1, x2, z2);
        return t == DoorType.WITHER || t == DoorType.BLOOD || DoorScanner.isKeyDoor(x1, z1, x2, z2);
    }

    private static void alert(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int ticks = Math.max(5, Math.round(cfg().roomAlertTime * 20));
        mc.gui.hud.setTimes(0, ticks, 5);
        mc.gui.hud.setTitle(Component.literal(text));
        mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
    }
}
