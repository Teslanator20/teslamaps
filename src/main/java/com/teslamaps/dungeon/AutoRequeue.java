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
package com.teslamaps.dungeon;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;

public class AutoRequeue {
    private static long requeueAt = 0L; // 0 = nothing scheduled
    private static boolean downtimeHold = false; // set by !dt -> pause auto-requeue for the run
    private static boolean blockNextRequeue = false; // a member left before run end -> skip the upcoming requeue

    public static void setDowntimeHold(boolean hold) { downtimeHold = hold; if (hold) requeueAt = 0L; }

    public static void onChatMessage(String msg) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        msg = msg.replaceAll("(?i)§[0-9A-FK-OR]", ""); // strip legacy color codes

        String low = msg.toLowerCase();
        if (low.contains("left the party") || low.contains("disbanded")
                || low.contains("removed from") || low.contains("kicked")
                || low.contains("was removed") || low.contains("has been removed")
                || low.contains("no longer allowed to access this instance")) {
            if (requeueAt != 0L) requeueAt = 0L;
            else blockNextRequeue = true;
            return;
        }

        if (c.autoRequeue && msg.contains("> EXTRA STATS <")) {
            if (blockNextRequeue) { blockNextRequeue = false; return; } // a member left this run -> don't requeue
            if (downtimeHold) return; // ChatCommands announces the reminders + clears the hold
            requeueAt = System.currentTimeMillis() + Math.max(0, c.requeueDelaySeconds) * 1000L;
            return;
        }

        if (c.requeueOnPartyR && isPartyR(msg)) {
            blockNextRequeue = false;
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
