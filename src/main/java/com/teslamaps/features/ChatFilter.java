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

import com.teslamaps.config.TeslaMapsConfig;

public class ChatFilter {

    public static boolean shouldHide(String raw) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.chatFilterEnabled) return false;

        String t = raw.replaceAll("(?i)§[0-9A-FK-OR]", "");

        if (c.chatFilterEmpty && t.trim().isEmpty()) return true;

        if (c.chatFilterWatcher && t.contains("[BOSS] The Watcher:")) return true;
        if (c.chatFilterF4Boss && (t.contains("[BOSS] Thorn:")
                || t.toLowerCase().contains("[crowd]"))) return true; // [CROWD]/[Crowd] spectator spam
        if (c.chatFilterOruo && t.contains("[STATUE] Oruo the Omniscient:")) return true;
        if (c.chatFilterProfileInfo && (t.contains("You are playing on profile:")
                || t.contains("Profile ID:"))) return true;
        if (c.chatFilterPerkBuffs && (t.contains("buff changed!") || t.contains("New buff:")
                || t.contains("disable this messaging by toggling"))) return true;

        if (t.contains(": ")) return false;

        if (c.chatFilterBlessings && (t.contains("DUNGEON BUFF!")
                || (t.contains("Blessing of") && t.contains("was picked up!"))
                || t.contains("Granted you +") || t.contains("Also granted you +"))) return true;
        if (c.chatFilterEssence && (t.contains("ESSENCE!")
                || (t.contains("Wither Essence!") && t.contains("extra essence")))) return true;
        if (c.chatFilterKeys && (t.contains("Key was picked up!")
                || (t.contains("has obtained") && t.contains("Key")))) return true;
        if (c.chatFilterDoors && t.contains(" door!")
                && (t.contains("opened a") || t.contains("opened the"))) return true;
        if (c.chatFilterWish && t.contains("Wish healed you for")) return true;
        if (c.chatFilterPickups && t.contains("was picked up!")) return true;

        if (c.chatFilterBlocksInWay && t.contains("There are blocks in the way!")) return true;
        if (c.chatFilterUltReady && t.contains("is ready to use! Press DROP to activate it!")) return true;
        if (c.chatFilterAoeDamage && (t.contains("enemies for") || t.contains("enemy for")) && t.contains("damage")) return true;
        if (c.chatFilterGuildXp && t.contains("GEXP from playing")) return true;
        if (c.chatFilterKillCombo && t.contains("Kill Combo")) return true;

        if (c.chatFilterStash && (t.contains("stashed away!")
                || t.contains("materials stashed!") || t.contains("types of materials stashed")
                || (t.contains("CLICK HERE") && t.contains("pick them up")))) return true;
        if (c.chatFilterServerMsgs && (t.contains("Sending to server")
                || t.contains("Warping...") || t.contains("Queuing..."))) return true;
        if (c.chatFilterSacks && t.contains("[Sacks]")) return true;
        if (c.chatFilterBonePlating && t.contains("bone plating reduced the damage")) return true;

        return false;
    }
}
