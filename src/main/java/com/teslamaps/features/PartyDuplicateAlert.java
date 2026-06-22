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
import com.teslamaps.player.PlayerTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PartyDuplicateAlert {

    private static final List<String> CLASSES = List.of("Archer", "Berserk", "Healer", "Mage", "Tank");
    private static boolean firedThisRun = false;
    private static int retryTicks = 0;

    public static void onChatMessage(String message) {
        if (!TeslaMapsConfig.get().partyDuplicateAlert) return;
        if (message.contains("The Catacombs, Floor")) {
            firedThisRun = false;
            retryTicks = 200;
        }
    }

    public static void tick() {
        if (retryTicks <= 0) return;
        retryTicks--;
        if (!firedThisRun && TeslaMapsConfig.get().partyDuplicateAlert) checkDuplicates();
    }

    private static void checkDuplicates() {
        Map<String, List<String>> byClass = new HashMap<>();
        for (PlayerTracker.DungeonPlayer p : PlayerTracker.getPlayers()) {
            String cls = p.getDungeonClass();
            if (!CLASSES.contains(cls)) continue;
            byClass.computeIfAbsent(cls, k -> new ArrayList<>()).add(p.getName());
        }
        if (byClass.isEmpty()) return;

        List<String> dupes = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : byClass.entrySet()) {
            if (e.getValue().size() > 1) dupes.add(e.getKey() + ": " + String.join(", ", e.getValue()));
        }

        firedThisRun = true;
        if (dupes.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        mc.player.sendSystemMessage(Component.literal("§a[TeslaMaps] §c§lDUPLICATE CLASSES! §7" + String.join(" §8| §7", dupes)));
        if (c.partyDuplicateSound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
        if (c.partyDuplicateMessage && mc.getConnection() != null) {
            mc.getConnection().sendCommand("pc Duplicate classes: " + String.join(" | ", dupes));
        }
    }

    public static void reset() { firedThisRun = false; }
}
