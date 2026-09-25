package com.teslamaps.profileviewer.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.api.HypixelApi;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SkyblockProfiles {
    private final String uuid;
    private final String playerName;
    private final Map<String, SkyblockProfile> profiles = new LinkedHashMap<>();
    private SkyblockProfile selectedProfile;

    private JsonObject rawResponse;  // Full SkyCrypt response

    private boolean loaded = false;
    private String error = null;

    public SkyblockProfiles(String uuid, String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
    }

    public CompletableFuture<Void> load() {
        return HypixelApi.getProfiles(uuid).thenAccept(response -> {
            if (response.has("error")) {
                this.error = response.get("error").getAsString();
                return;
            }

            this.rawResponse = response;

            if (!response.has("profiles")) {
                this.error = "No Skyblock profiles found";
                return;
            }

            JsonObject profilesObj = response.getAsJsonObject("profiles");
            if (profilesObj.size() == 0) {
                this.error = "No Skyblock profiles found";
                return;
            }

            for (Map.Entry<String, JsonElement> entry : profilesObj.entrySet()) {
                String cuteName = entry.getKey();
                JsonObject profileData = entry.getValue().getAsJsonObject();

                String profileId = profileData.has("profile_id") ?
                        profileData.get("profile_id").getAsString() : cuteName;

                JsonObject memberData = profileData.has("data") ?
                        profileData.getAsJsonObject("data") : profileData;

                JsonObject rawData = profileData.has("raw") ?
                        profileData.getAsJsonObject("raw") : memberData;

                SkyblockProfile profile = new SkyblockProfile(profileId, cuteName, rawData, memberData, uuid);
                profile.setSkyCryptData(profileData);  // Store full profile data
                profiles.put(cuteName, profile);

                if (profileData.has("current") && profileData.get("current").getAsBoolean()) {
                    selectedProfile = profile;
                }
            }

            if (selectedProfile == null && !profiles.isEmpty()) {
                selectedProfile = profiles.values().iterator().next();
            }

            this.loaded = true;

        }).exceptionally(e -> {
            TeslaMaps.LOGGER.error("Failed to load profiles for {}", playerName, e);
            this.error = "Failed to load: " + e.getMessage();
            return null;
        });
    }

    public CompletableFuture<Void> loadProfileData(SkyblockProfile profile) {
        return CompletableFuture.completedFuture(null);
    }

    public String getUuid() { return uuid; }
    public String getPlayerName() { return playerName; }
    public Map<String, SkyblockProfile> getProfiles() { return profiles; }
    public SkyblockProfile getSelectedProfile() { return selectedProfile; }
    public void setSelectedProfile(SkyblockProfile profile) { this.selectedProfile = profile; }
    public boolean isLoaded() { return loaded; }
    public String getError() { return error; }

    public String getDisplayName() {
        return playerName;
    }

    public String getOnlineStatus() {
        return status;
    }

    public void loadStatus() {
        com.teslamaps.api.PvApi.get("/status/" + com.teslamaps.api.PvApi.dashed(uuid), "status")
                .thenAccept(response -> {
                    if (response == null) return;
                    boolean online = response.has("online") && response.get("online").getAsBoolean();
                    String location = response.has("mode") && !response.get("mode").isJsonNull()
                            ? response.get("mode").getAsString() : null;
                    statusMode = online ? location : null;
                    status = online
                            ? (location != null ? "Online - " + prettyMode(location) : "Online")
                            : "Offline";
                });
    }

    private static String prettyMode(String mode) {
        String out = mode.replace('_', ' ').toLowerCase();
        return out.isEmpty() ? out : Character.toUpperCase(out.charAt(0)) + out.substring(1);
    }

    private volatile String statusMode;
    private volatile double networkExp = -1;

    /** Raw /status mode (hub, mining_3, ...), null while offline or unknown. */
    public String getStatusMode() {
        return statusMode;
    }

    /** -1 when the player object never arrived. */
    public double getNetworkExp() {
        return networkExp;
    }

    public static String islandName(String mode) {
        if (mode == null || mode.isBlank()) return null;
        return switch (mode) {
            case "dynamic" -> "Private Island";
            case "hub" -> "Hub";
            case "mining_1" -> "Gold Mine";
            case "mining_2" -> "Deep Caverns";
            case "mining_3" -> "Dwarven Mines";
            case "crystal_hollows" -> "Crystal Hollows";
            case "mineshaft" -> "Glacite Mineshaft";
            case "combat_1" -> "Spider's Den";
            case "combat_3" -> "The End";
            case "farming_1" -> "The Barn";
            case "farming_2" -> "Mushroom Desert";
            case "foraging_1" -> "The Park";
            case "foraging_2" -> "Galatea";
            case "winter" -> "Jerry's Workshop";
            case "dungeon_hub" -> "Dungeon Hub";
            case "dungeon" -> "Dungeon";
            case "garden" -> "The Garden";
            case "rift" -> "The Rift";
            case "crimson_isle" -> "Crimson Isle";
            case "kuudra", "instanced" -> "Kuudra";
            case "dark_auction" -> "Dark Auction";
            case "fishing_1" -> "Backwater Bayou";
            default -> prettyMode(mode);
        };
    }

    private String status = "Status unknown";
    private String rankPrefix = "";

    public String getRankPrefix() {
        return rankPrefix;
    }

    /** Rank needs the hypixel player object - own key, then the pv backend, then elitebot's mirror. */
    public void loadPlayer() {
        com.teslamaps.api.HypixelApi.getPlayer(uuid).thenApply(player -> {
            if (player != null && !player.keySet().isEmpty()) return player;
            JsonObject viaPv = com.teslamaps.api.PvApi
                    .get("/player/" + com.teslamaps.api.PvApi.dashed(uuid), "player").join();
            if (viaPv != null && viaPv.has("player") && viaPv.get("player").isJsonObject()) {
                return viaPv.getAsJsonObject("player");
            }
            // the pv backend keeps /player switched off, elitebot mirrors the same object publicly
            return com.teslamaps.api.HypixelApi.getPlayerViaElite(uuid);
        }).thenAccept(player -> {
            if (player == null || player.keySet().isEmpty()) return;
            rankPrefix = buildRank(player);
            if (player.has("networkExp") && player.get("networkExp").isJsonPrimitive()) {
                try {
                    networkExp = player.get("networkExp").getAsDouble();
                } catch (NumberFormatException ignored) {
                }
            }

            // the treasure hunter achievement counts every secret the account ever found,
            // across all profiles and the deleted ones - the profile only knows its own
            if (player.has("achievements") && player.get("achievements").isJsonObject()) {
                JsonObject achievements = player.getAsJsonObject("achievements");
                if (achievements.has("skyblock_treasure_hunter")
                        && achievements.get("skyblock_treasure_hunter").isJsonPrimitive()) {
                    try {
                        totalSecrets = achievements.get("skyblock_treasure_hunter").getAsLong();
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (achievements.has("skyblock_hard_working_miner")
                        && achievements.get("skyblock_hard_working_miner").isJsonPrimitive()) {
                    try {
                        commissions = achievements.get("skyblock_hard_working_miner").getAsLong();
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        });
    }

    private long totalSecrets = -1;
    private long commissions = -1;

    /** Account wide commission count from the achievement, -1 when unknown. */
    public long getCommissions() {
        return commissions;
    }

    /** -1 when unknown, the achievement needs an api key. */
    public long getTotalSecrets() {
        return totalSecrets;
    }

    private static String buildRank(JsonObject player) {
        String staff = string(player, "rank");
        if (staff != null && !staff.equals("NORMAL")) {
            return switch (staff) {
                case "ADMIN" -> "§c[ADMIN] ";
                case "GAME_MASTER" -> "§2[GM] ";
                case "MODERATOR" -> "§2[MOD] ";
                case "HELPER" -> "§9[HELPER] ";
                case "YOUTUBER" -> "§c[§fYOUTUBE§c] ";
                default -> "";
            };
        }

        String plusColor = colorCode(string(player, "rankPlusColor"), "§c");
        String monthly = string(player, "monthlyPackageRank");
        if ("SUPERSTAR".equals(monthly)) {
            String base = colorCode(string(player, "monthlyRankColor"), "§6");
            return base + "[MVP" + plusColor + "++" + base + "] ";
        }

        String package_ = string(player, "newPackageRank");
        if (package_ == null) package_ = string(player, "packageRank");
        if (package_ == null) return "§7";

        return switch (package_) {
            case "MVP_PLUS" -> "§b[MVP" + plusColor + "+§b] ";
            case "MVP" -> "§b[MVP] ";
            case "VIP_PLUS" -> "§a[VIP§6+§a] ";
            case "VIP" -> "§a[VIP] ";
            default -> "§7";
        };
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
    }

    private static String colorCode(String name, String fallback) {
        if (name == null) return fallback;
        return switch (name) {
            case "BLACK" -> "§0";
            case "DARK_BLUE" -> "§1";
            case "DARK_GREEN" -> "§2";
            case "DARK_AQUA" -> "§3";
            case "DARK_RED" -> "§4";
            case "DARK_PURPLE" -> "§5";
            case "GOLD" -> "§6";
            case "GRAY" -> "§7";
            case "DARK_GRAY" -> "§8";
            case "BLUE" -> "§9";
            case "GREEN" -> "§a";
            case "AQUA" -> "§b";
            case "RED" -> "§c";
            case "LIGHT_PURPLE" -> "§d";
            case "YELLOW" -> "§e";
            case "WHITE" -> "§f";
            default -> fallback;
        };
    }

    private String guildName;

    public String getGuildName() {
        return guildName;
    }

    public void loadGuild() {
        HypixelApi.getGuildName(uuid).thenAccept(name -> {
            if (name != null) {
                guildName = name;
                return;
            }
            com.teslamaps.api.PvApi.get("/guild/" + com.teslamaps.api.PvApi.dashed(uuid), "guild")
                    .thenAccept(response -> {
                        if (response == null || !response.has("guild") || !response.get("guild").isJsonObject()) return;
                        JsonObject guild = response.getAsJsonObject("guild");
                        if (guild.has("name") && guild.get("name").isJsonPrimitive()) {
                            guildName = guild.get("name").getAsString();
                        }
                    });
        });
    }

    public static void addToRecentPlayers(String playerName) {
        TeslaMapsConfig config = TeslaMapsConfig.get();

        // the same name in different spelling counted as two entries and showed up twice
        config.recentPlayers.removeIf(name -> name.equalsIgnoreCase(playerName));
        config.recentPlayers.add(0, playerName);

        java.util.List<String> unique = new java.util.ArrayList<>();
        for (String name : config.recentPlayers) {
            boolean known = false;
            for (String kept : unique) {
                if (kept.equalsIgnoreCase(name)) {
                    known = true;
                    break;
                }
            }
            if (!known) unique.add(name);
        }

        while (unique.size() > 10) unique.remove(unique.size() - 1);
        config.recentPlayers.clear();
        config.recentPlayers.addAll(unique);
        TeslaMapsConfig.save();
    }
}
