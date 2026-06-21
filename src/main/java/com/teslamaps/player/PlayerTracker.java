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
package com.teslamaps.player;

import com.teslamaps.TeslaMaps;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.mixin.PlayerTabOverlayAccessor;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class PlayerTracker {
    public static final Pattern PLAYER_TAB_PATTERN = Pattern.compile(
            "\\[\\d+] (?:\\[[A-Za-z]+] )?(?<name>[A-Za-z0-9_]+) (?:.+ )?\\((?<class>\\S+) ?(?<level>[LXVI0]+)?\\)");

    public static final Pattern PLAYER_GHOST_PATTERN = Pattern.compile("  (?<name>[A-Za-z0-9_]+) .+ became a ghost\\.");

    private static final DungeonPlayer @Nullable [] players = new DungeonPlayer[5];

    private static List<PlayerInfo> playerList = new ArrayList<>();

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

    private static void updatePlayerList() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener networkHandler = mc.getConnection();

        if (networkHandler != null) {
            try {
                playerList = networkHandler.getOnlinePlayers()
                        .stream()
                        .sorted(PlayerTabOverlayAccessor.getOrdering())
                        .toList();
            } catch (Exception e) {
                playerList = new ArrayList<>(networkHandler.getOnlinePlayers());
            }
        }
    }

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

    public static @Nullable Matcher getPlayerFromTab(int index) {
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

    public static @Nullable String strAt(int idx) {
        if (playerList == null || playerList.size() <= idx) {
            return null;
        }

        Component txt = playerList.get(idx).getTabListDisplayName();
        if (txt == null) {
            return null;
        }
        String str = txt.getString().replaceAll("§.", "").trim();
        if (str.isEmpty()) {
            return null;
        }
        return str;
    }

    public static void onGhostMessage(String message) {
        Matcher matcher = PLAYER_GHOST_PATTERN.matcher(message);
        if (!matcher.find()) return;

        String name = matcher.group("name");
        Minecraft mc = Minecraft.getInstance();
        if ("You".equals(name) && mc.player != null) {
            name = mc.player.getName().getString();
        }

        String finalName = name;
        getPlayer(name).ifPresent(player -> {
            player.ghost();
            TeslaMaps.LOGGER.info("Player '{}' became a ghost", finalName);
        });
    }

    public static DungeonPlayer @Nullable [] getPlayersOrdered() {
        return players;
    }

    public static Optional<DungeonPlayer> getPlayer(String name) {
        return Arrays.stream(players)
                .filter(p -> p != null && p.getName().equals(name))
                .findAny();
    }

    public static List<DungeonPlayer> getPlayers() {
        List<DungeonPlayer> list = new ArrayList<>();
        for (DungeonPlayer player : players) {
            if (player != null) {
                list.add(player);
            }
        }
        return list;
    }

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

            if (uuid != null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        var handler = Minecraft.getInstance().getConnection();
                        if (handler != null) {
                            handler.getPlayerInfo(uuid);
                        }
                    } catch (Exception ignored) {}
                }, Executors.newVirtualThreadPerTaskExecutor());
            }
        }

        private static @Nullable UUID findPlayerUuid(String name) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return null;

            for (var entity : mc.level.entitiesForRendering()) {
                if (entity instanceof Player player) {
                    if (player.getGameProfile().name().equals(name)) {
                        return player.getUUID();
                    }
                }
            }
            return null;
        }

        public void update(String dungeonClass) {
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
