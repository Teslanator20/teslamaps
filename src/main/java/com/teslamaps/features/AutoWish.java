package com.teslamaps.features;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.player.PlayerTracker;
import net.minecraft.client.MinecraftClient;

/**
 * Auto Wish - Automatically uses healer ultimate ability at key moments in dungeons.
 * Only works when playing as Healer class.
 *
 * Triggers on specific boss messages:
 * - Maxor enraged (F7/M7)
 * - Goldor factory destroyed (F7/M7)
 * - Sadan giants unleashed (F6/M6)
 */
public class AutoWish {

    private static int scheduledDropTick = -1;
    private static boolean dropAll = false;

    /**
     * Called from ChatMixin when a chat message is received.
     */
    public static void onChatMessage(String message) {
        if (!TeslaMapsConfig.get().autoWish) return;
        if (!DungeonManager.isInDungeon()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // Only trigger if player is Healer class
        if (!isHealer()) {
            return;
        }

        int delay = -1;

        // F7/M7 - Maxor enraged
        if (message.contains("Maxor is enraged!")) {
            delay = 1;
        }
        // F7/M7 - Goldor factory destroyed
        else if (message.contains("[BOSS] Goldor: You have done it, you destroyed the factory")) {
            delay = 1;
        }
        // F6/M6 - Sadan giants
        else if (message.contains("[BOSS] Sadan: My giants! Unleashed!")) {
            delay = 25;
        }

        if (delay > 0) {
            scheduleWish(delay);
            TeslaMaps.LOGGER.info("[AutoWish] Scheduled wish in {} ticks", delay);
        }
    }

    /**
     * Schedule dropping item (using wish) after a delay.
     */
    private static void scheduleWish(int delayTicks) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        scheduledDropTick = mc.player.age + delayTicks;
        dropAll = false; // Use normal drop, not drop all
    }

    /**
     * Called every tick to check if we should use wish.
     */
    public static void tick() {
        if (!TeslaMapsConfig.get().autoWish) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (scheduledDropTick > 0 && mc.player.age >= scheduledDropTick) {
            // Drop the item to trigger wish
            mc.player.dropSelectedItem(dropAll);
            TeslaMaps.LOGGER.info("[AutoWish] Used wish!");

            scheduledDropTick = -1;
            dropAll = false;
        }
    }

    public static void reset() {
        scheduledDropTick = -1;
        dropAll = false;
    }

    /**
     * Check if the local player is playing as Healer class.
     */
    private static boolean isHealer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;

        String playerName = mc.player.getName().getString();

        // Check PlayerTracker for our class
        for (PlayerTracker.DungeonPlayer dp : PlayerTracker.getPlayers()) {
            if (dp.getName().equals(playerName)) {
                String dungeonClass = dp.getDungeonClass();
                return "Healer".equals(dungeonClass);
            }
        }

        return false;
    }
}
