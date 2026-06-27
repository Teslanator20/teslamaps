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
import com.teslamaps.utils.ScoreboardUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PartyDuplicateAlert {

    // scoreboard line in the entrance looks like "[M] playername [Lvl42]"
    private static final Pattern CLASS_LINE = Pattern.compile("\\[([MBHAT])]\\s+([A-Za-z0-9_]+)");
    private static final Map<String, String> CLASS_NAMES = Map.of(
            "M", "Mage", "B", "Berserk", "H", "Healer", "A", "Archer", "T", "Tank");

    private static boolean firedThisRun = false;
    private static int tickCounter = 0;

    public static void onChatMessage(String message) {
        // not needed anymore (tick() checks directly), kept so a new floor re-arms the check promptly
        if (message.contains("The Catacombs, Floor")) firedThisRun = false;
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().partyDuplicateAlert) return;
        // reset when not in a dungeon so each run re-checks; otherwise check once per run
        if (!com.teslamaps.dungeon.DungeonManager.isInDungeon()) { firedThisRun = false; return; }
        if (firedThisRun) return;
        if (++tickCounter % 20 != 0) return; // throttle to ~1/s
        checkDuplicates();
    }

    private static void checkDuplicates() {
        Map<String, List<String>> byClass = new LinkedHashMap<>();
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            Matcher m = CLASS_LINE.matcher(ScoreboardUtils.cleanLine(line));
            if (!m.find()) continue;
            String cls = CLASS_NAMES.get(m.group(1));
            byClass.computeIfAbsent(cls, k -> new ArrayList<>()).add(m.group(2));
        }
        if (byClass.isEmpty()) return;

        List<String> dupes = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : byClass.entrySet()) {
            if (e.getValue().size() > 1) dupes.add(e.getKey() + ": " + String.join(", ", e.getValue()));
        }

        // keep re-checking until a dupe appears (classes can change pre-start)
        if (dupes.isEmpty()) return;
        firedThisRun = true;

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
