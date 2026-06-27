/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * Copyright (c) 2026 Teslanator20.
 *
 * See the LICENSE file in the project root for full terms.
 */
package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Reminds you to swap off the Spirit pet (movement) to a damage pet when the boss starts.
public class SpiritPetReminder {

    private static final Pattern EQUIP = Pattern.compile("(?:summoned|equipped) your (?:\\[Lvl \\d+] )?(?<pet>.+?)[!.]");
    private static final Pattern DESPAWN = Pattern.compile("despawned your (?:\\[Lvl \\d+] )?(?<pet>.+?)[!.]");

    private static boolean spiritEquipped = false;
    private static boolean wasInBoss = false;
    private static boolean firedThisRun = false;

    public static void onChatMessage(String raw) {
        String m = raw.replaceAll("(?i)§[0-9A-FK-OR]", "");
        Matcher d = DESPAWN.matcher(m);
        if (d.find()) { spiritEquipped = false; return; }
        Matcher e = EQUIP.matcher(m);
        if (e.find()) spiritEquipped = e.group("pet").trim().equalsIgnoreCase("Spirit");
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().spiritPetReminder) return;
        if (!DungeonManager.isInDungeon()) { wasInBoss = false; firedThisRun = false; return; }
        boolean boss = DungeonManager.isInBoss();
        if (boss && !wasInBoss && !firedThisRun && spiritEquipped) {
            firedThisRun = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal(
                        "§a[TeslaMaps] §c§lSWITCH OFF SPIRIT PET! §r§7— swap to your damage pet for the boss"));
                mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
            }
        }
        wasInBoss = boss;
    }
}
