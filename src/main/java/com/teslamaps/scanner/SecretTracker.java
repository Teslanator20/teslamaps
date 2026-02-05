package com.teslamaps.scanner;

import com.teslamaps.TeslaMaps;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.map.DungeonRoom;
import com.teslamaps.utils.ScoreboardUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks secrets by monitoring the action bar and scoreboard.
 * Action bar shows "X/Y Secrets" format.
 * Scoreboard shows "Secrets Found: X" format.
 */
public class SecretTracker {
    private static int dungeonSecretsFound = 0;
    private static int lastKnownFound = -1;  // -1 means not initialized yet
    private static int tickCounter = 0;

    // Pattern to match "X/Y Secrets" in action bar (found/total)
    private static final Pattern ACTION_BAR_PATTERN = Pattern.compile("(\\d+)/(\\d+) Secrets");

    // Pattern to match "Secrets Found: X" on scoreboard
    private static final Pattern SCOREBOARD_PATTERN = Pattern.compile("Secrets Found: (\\d+)");

    /**
     * Called every tick to check scoreboard for secrets.
     */
    public static void tick() {
        if (!DungeonManager.isInDungeon()) return;

        tickCounter++;
        // Check scoreboard every 10 ticks
        if (tickCounter % 10 != 0) return;

        checkScoreboard();
    }

    /**
     * Check scoreboard for "Secrets Found: X" line.
     */
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
                    // Ignore
                }
                break;
            }
        }
    }

    /**
     * Called when an action bar (overlay) message is received.
     * Parses "X/Y Secrets" format from action bar.
     * The action bar shows CURRENT ROOM's secrets, not dungeon-wide!
     */
    public static void onActionBarMessage(Text message) {
        if (!DungeonManager.isInDungeon()) return;

        String text = message.getString();

        // Strip color codes (§X where X is any character) before parsing
        String cleanText = text.replaceAll("§.", "");

        // Debug: log action bar messages occasionally
        if (tickCounter % 100 == 0) {
            TeslaMaps.LOGGER.debug("[SecretTracker] Action bar: {} -> {}", text, cleanText);
        }

        Matcher matcher = ACTION_BAR_PATTERN.matcher(cleanText);
        if (matcher.find()) {
            try {
                int roomFound = Integer.parseInt(matcher.group(1));
                int roomTotal = Integer.parseInt(matcher.group(2));

                // Action bar shows CURRENT ROOM's secrets, set it directly
                DungeonRoom currentRoom = getCurrentPlayerRoom();
                if (currentRoom != null) {
                    // Only update if the total matches (sanity check)
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
                // Ignore parsing errors
            }
        }
    }

    /**
     * Process a secret count update - attribute increases to current room.
     */
    private static void processSecretUpdate(int newFound) {
        // First observation - just initialize without treating as gain
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

            // Attribute to current room
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
            // Total changed (reset or missed something)
            lastKnownFound = newFound;
            dungeonSecretsFound = newFound;
        }
    }

    /**
     * Get the room the player is currently standing in.
     */
    private static DungeonRoom getCurrentPlayerRoom() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;

        double x = mc.player.getX();
        double z = mc.player.getZ();

        // Convert world coordinates to grid coordinates
        // Dungeon starts at -200,-200 with 32-block cells
        int gridX = (int) Math.floor((x + 200) / 32.0);
        int gridZ = (int) Math.floor((z + 200) / 32.0);

        // Clamp to valid grid range (0-5)
        gridX = Math.max(0, Math.min(5, gridX));
        gridZ = Math.max(0, Math.min(5, gridZ));

        return DungeonManager.getRoomAt(gridX, gridZ);
    }

    /**
     * Called when a room's checkmark state changes to GREEN.
     * This confirms all secrets in the room are found.
     */
    public static void onRoomCompleted(DungeonRoom room) {
        if (room != null && room.getSecrets() > 0) {
            room.setFoundSecrets(room.getSecrets());
            TeslaMaps.LOGGER.debug("[SecretTracker] Room '{}' completed - all {} secrets found",
                    room.getName(), room.getSecrets());
        }
    }

    /**
     * Get current dungeon-wide secrets found.
     */
    public static int getDungeonSecretsFound() {
        return dungeonSecretsFound;
    }

    /**
     * Reset tracker state (called when leaving dungeon).
     */
    public static void reset() {
        dungeonSecretsFound = 0;
        lastKnownFound = -1;  // -1 means not initialized
        tickCounter = 0;
    }
}
