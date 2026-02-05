package com.teslamaps.player;

import com.teslamaps.TeslaMaps;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.mixin.PlayerTabOverlayAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks all players in the dungeon for map display.
 * Uses vanilla tab list ordering.
 */
public class PlayerTracker {
    /**
     * Match a player entry in the dungeon tab list.
     * Group 1: name
     * Group 2: class (or literal "EMPTY" pre dungeon start)
     * Group 3: level (or nothing, if pre dungeon start)
     */
    public static final Pattern PLAYER_TAB_PATTERN = Pattern.compile(
            "\\[\\d+] (?:\\[[A-Za-z]+] )?(?<name>[A-Za-z0-9_]+) (?:.+ )?\\((?<class>\\S+) ?(?<level>[LXVI0]+)?\\)");

    public static final Pattern PLAYER_GHOST_PATTERN = Pattern.compile(" ☠ (?<name>[A-Za-z0-9_]+) .+ became a ghost\\.");

    /**
     * Ordered array of players - index matches map decoration order.
     * Index 0 is the local player (self), indices 1-4 are other party members.
     */
    private static final DungeonPlayer @Nullable [] players = new DungeonPlayer[5];

    /**
     * Cached player list sorted by vanilla ordering.
     */
    private static List<PlayerListEntry> playerList = new ArrayList<>();

    public static void reset() {
        Arrays.fill(players, null);
        playerList = new ArrayList<>(); // Create new list instead of clear() since toList() returns immutable
    }

    public static void tick() {
        if (!DungeonManager.isInDungeon()) {
            return;
        }

        updatePlayerList();
        updatePlayers();
    }

    /**
     * Update the cached player list using vanilla's tab list ordering.
     */
    private static void updatePlayerList() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();

        if (networkHandler != null) {
            try {
                playerList = networkHandler.getPlayerList()
                        .stream()
                        .sorted(PlayerTabOverlayAccessor.getOrdering())
                        .toList();
            } catch (Exception e) {
                // Fallback if accessor fails
                playerList = new ArrayList<>(networkHandler.getPlayerList());
            }
        }
    }

    /**
     * Update players from tab list.
     */
    private static void updatePlayers() {
        for (int i = 0; i < 5; i++) {
            Matcher matcher = getPlayerFromTab(i + 1);

            if (matcher == null) {
                players[i] = null;
                continue;
            }

            String name = matcher.group("name");
            String dungeonClass = matcher.group("class");

            DungeonPlayer dungeonPlayer = players[i];
            if (dungeonPlayer != null && dungeonPlayer.getName().equals(name)) {
                dungeonPlayer.update(dungeonClass);
            } else {
                players[i] = new DungeonPlayer(name, dungeonClass);
            }
        }
    }

    /**
     * Get player info from tab list at a specific dungeon player index (1-5).
     * Uses vanilla tab list ordering.
     */
    public static @Nullable Matcher getPlayerFromTab(int index) {
        // Tab list structure: players at positions 1, 5, 9, 13, 17 (every 4th slot starting at 1)
        int tabPosition = 1 + (index - 1) * 4;

        String str = strAt(tabPosition);
        if (str == null) {
            return null;
        }

        Matcher m = PLAYER_TAB_PATTERN.matcher(str);
        if (!m.matches()) {
            return null;
        }
        return m;
    }

    /**
     * Get the display name at some index of the player list as string.
     */
    public static @Nullable String strAt(int idx) {
        if (playerList == null || playerList.size() <= idx) {
            return null;
        }

        Text txt = playerList.get(idx).getDisplayName();
        if (txt == null) {
            return null;
        }
        String str = txt.getString().replaceAll("§.", "").trim();
        if (str.isEmpty()) {
            return null;
        }
        return str;
    }

    /**
     * Handle ghost message - mark player as dead.
     */
    public static void onGhostMessage(String message) {
        Matcher matcher = PLAYER_GHOST_PATTERN.matcher(message);
        if (!matcher.find()) return;

        String name = matcher.group("name");
        MinecraftClient mc = MinecraftClient.getInstance();
        if ("You".equals(name) && mc.player != null) {
            name = mc.player.getName().getString();
        }

        String finalName = name;
        getPlayer(name).ifPresent(player -> {
            player.ghost();
            TeslaMaps.LOGGER.info("Player '{}' became a ghost", finalName);
        });
    }

    /**
     * Get the ordered player array. Index 0 is local player.
     */
    public static DungeonPlayer @Nullable [] getPlayersOrdered() {
        return players;
    }

    /**
     * Get a player by name.
     */
    public static Optional<DungeonPlayer> getPlayer(String name) {
        return Arrays.stream(players)
                .filter(p -> p != null && p.getName().equals(name))
                .findAny();
    }

    /**
     * Get all non-null players as a list.
     */
    public static List<DungeonPlayer> getPlayers() {
        List<DungeonPlayer> list = new ArrayList<>();
        for (DungeonPlayer player : players) {
            if (player != null) {
                list.add(player);
            }
        }
        return list;
    }

    /**
     * Inner class representing a dungeon player.
     */
    public static class DungeonPlayer {
        private @Nullable UUID uuid;
        private final String name;
        private String dungeonClass = "EMPTY";
        private boolean alive = true;
        private long lastGhostTime = 0;

        public DungeonPlayer(String name, String dungeonClass) {
            this.uuid = findPlayerUuid(name);
            this.name = name;
            update(dungeonClass);

            // Pre-fetch game profile for smooth skin rendering
            if (uuid != null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        // Trigger skin loading by looking up the player in tab list
                        var handler = MinecraftClient.getInstance().getNetworkHandler();
                        if (handler != null) {
                            handler.getPlayerListEntry(uuid);
                        }
                    } catch (Exception ignored) {}
                }, Executors.newVirtualThreadPerTaskExecutor());
            }
        }

        private static @Nullable UUID findPlayerUuid(String name) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return null;

            // Search through all entities
            for (var entity : mc.world.getEntities()) {
                if (entity instanceof PlayerEntity player) {
                    if (player.getGameProfile().name().equals(name)) {
                        return player.getUuid();
                    }
                }
            }
            return null;
        }

        public void update(String dungeonClass) {
            // Prevent tab list from overriding a recently ghosted player
            if ("EMPTY".equals(this.dungeonClass) && lastGhostTime + 2000 > System.currentTimeMillis()) {
                return;
            }
            this.dungeonClass = dungeonClass;
            this.alive = !"EMPTY".equals(dungeonClass);
        }

        public void ghost() {
            update("EMPTY");
            lastGhostTime = System.currentTimeMillis();
        }

        public @Nullable UUID getUuid() {
            if (uuid == null) {
                uuid = findPlayerUuid(name); // Retry finding UUID
            }
            return uuid;
        }

        public String getName() {
            return name;
        }

        public String getDungeonClass() {
            return dungeonClass;
        }

        public boolean isAlive() {
            return alive;
        }

        @Override
        public String toString() {
            return "DungeonPlayer{name='" + name + "', uuid=" + uuid + ", class=" + dungeonClass + ", alive=" + alive + "}";
        }
    }
}
