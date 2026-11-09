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
            TeslaMaps.debug("[AutoWish] Scheduled wish in {} ticks", delay);
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
            mc.gameMode.dropItem(mc.player, dropAll);
            TeslaMaps.debug("[AutoWish] Used wish!");

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
