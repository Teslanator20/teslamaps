package com.teslamaps.dungeon;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;

/**
 * Auto requeue (ported from Odin's DungeonQueue): sends /instancerequeue after a dungeon ends,
 * or when a party member types "r". Cancels if you leave / get kicked from the party.
 */
public class AutoRequeue {
    private static long requeueAt = 0L; // 0 = nothing scheduled

    public static void onChatMessage(String msg) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        msg = msg.replaceAll("(?i)§[0-9A-FK-OR]", ""); // strip legacy color codes

        // Cancel a pending requeue if the party broke up / you got kicked.
        if (msg.contains("left the party") || msg.contains("has disbanded")
                || msg.contains("been removed from") || msg.contains("You were kicked")
                || msg.contains("no longer allowed to access this instance")) {
            requeueAt = 0L;
            return;
        }

        // Dungeon end -> requeue after the configured delay.
        if (c.autoRequeue && msg.contains("> EXTRA STATS <")) {
            requeueAt = System.currentTimeMillis() + Math.max(0, c.requeueDelaySeconds) * 1000L;
            return;
        }

        // A party member typed "r" -> requeue.
        if (c.requeueOnPartyR && isPartyR(msg)) {
            requeueAt = System.currentTimeMillis() + 500L;
        }
    }

    private static boolean isPartyR(String msg) {
        if (!msg.startsWith("Party >")) return false;
        int colon = msg.lastIndexOf(": ");
        if (colon < 0) return false;
        String content = msg.substring(colon + 2).trim().toLowerCase();
        return content.equals("r") || content.equals("re") || content.equals("requeue");
    }

    public static void tick() {
        if (requeueAt == 0L || System.currentTimeMillis() < requeueAt) return;
        requeueAt = 0L;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand("instancerequeue");
    }
}
