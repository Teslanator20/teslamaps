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
package com.teslamaps.scanner;

import com.teslamaps.TeslaMaps;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.utils.ScoreboardUtils;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class SecretTracker {
    private static int dungeonSecretsFound = 0;
    private static int lastKnownFound = -1;  // -1 means not initialized yet
    private static int tickCounter = 0;

    private static final Pattern ACTION_BAR_PATTERN = Pattern.compile("(\\d+)/(\\d+) Secrets");

    private static final Pattern SCOREBOARD_PATTERN = Pattern.compile("Secrets Found: (\\d+)");

    public static void tick() {
        if (!DungeonManager.isInDungeon()) return;

        tickCounter++;
        if (tickCounter % 10 != 0) return;

        checkScoreboard();
    }

    private static void checkScoreboard() {
        List<String> lines = ScoreboardUtils.getScoreboardLines();
        for (String line : lines) {
            String clean = ScoreboardUtils.cleanLine(line);
            Matcher matcher = SCOREBOARD_PATTERN.matcher(clean);
            if (matcher.find()) {
                try {
                    int found = Integer.parseInt(matcher.group(1));
                    processSecretUpdate(found);
                } catch (NumberFormatException e) {
                }
                break;
            }
        }
    }

    public static void onActionBarMessage(Component message) {
        if (!DungeonManager.isInDungeon()) return;

        String text = message.getString();

        String cleanText = text.replaceAll("§.", "");

        if (tickCounter % 100 == 0) {
            TeslaMaps.LOGGER.debug("[SecretTracker] Action bar: {} -> {}", text, cleanText);
        }

        Matcher matcher = ACTION_BAR_PATTERN.matcher(cleanText);
        if (matcher.find()) {
            try {
                int roomFound = Integer.parseInt(matcher.group(1));
                int roomTotal = Integer.parseInt(matcher.group(2));

                DungeonRoom currentRoom = getCurrentPlayerRoom();
                if (currentRoom != null) {
                    if (currentRoom.getSecrets() == roomTotal || currentRoom.getSecrets() == 0) {
                        int oldFound = currentRoom.getFoundSecrets();
                        if (oldFound != roomFound) {
                            currentRoom.setFoundSecrets(roomFound);
                            TeslaMaps.LOGGER.debug("[SecretTracker] Room '{}' secrets: {}/{} (was {})",
                                    currentRoom.getName(), roomFound, roomTotal, oldFound);
                        }
                    }
                }
            } catch (NumberFormatException e) {
            }
        }
    }

    private static void processSecretUpdate(int newFound) {
        if (lastKnownFound < 0) {
            lastKnownFound = newFound;
            dungeonSecretsFound = newFound;
            TeslaMaps.LOGGER.debug("[SecretTracker] Initialized with dungeon total: {}", newFound);
            return;
        }

        if (newFound > lastKnownFound) {
            int secretsGained = newFound - lastKnownFound;
            lastKnownFound = newFound;
            dungeonSecretsFound = newFound;

            DungeonRoom currentRoom = getCurrentPlayerRoom();
            if (currentRoom != null && currentRoom.getSecrets() > 0) {
                int current = Math.max(0, currentRoom.getFoundSecrets());
                int newRoomFound = Math.min(current + secretsGained, currentRoom.getSecrets());
                currentRoom.setFoundSecrets(newRoomFound);
                TeslaMaps.LOGGER.debug("[SecretTracker] Secret found in '{}': {}/{} (dungeon total: {})",
                        currentRoom.getName(), newRoomFound, currentRoom.getSecrets(), newFound);
            } else {
                TeslaMaps.LOGGER.debug("[SecretTracker] Secret found but no valid room (total: {})", newFound);
            }
        } else if (newFound != lastKnownFound) {
            lastKnownFound = newFound;
            dungeonSecretsFound = newFound;
        }
    }

    private static DungeonRoom getCurrentPlayerRoom() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;

        double x = mc.player.getX();
        double z = mc.player.getZ();

        int gridX = (int) Math.floor((x + 200) / 32.0);
        int gridZ = (int) Math.floor((z + 200) / 32.0);

        gridX = Math.max(0, Math.min(5, gridX));
        gridZ = Math.max(0, Math.min(5, gridZ));

        return DungeonManager.getRoomAt(gridX, gridZ);
    }

    public static void onRoomCompleted(DungeonRoom room) {
        if (room != null && room.getSecrets() > 0) {
            room.setFoundSecrets(room.getSecrets());
            TeslaMaps.LOGGER.debug("[SecretTracker] Room '{}' completed - all {} secrets found",
                    room.getName(), room.getSecrets());
        }
    }

    public static int getDungeonSecretsFound() {
        return dungeonSecretsFound;
    }

    public static void reset() {
        dungeonSecretsFound = 0;
        lastKnownFound = -1;  // -1 means not initialized
        tickCounter = 0;
    }
}
