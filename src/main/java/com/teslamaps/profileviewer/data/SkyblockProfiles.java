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
                    status = online
                            ? (location != null ? "Online - " + prettyMode(location) : "Online")
                            : "Offline";
                });
    }

    private static String prettyMode(String mode) {
        String out = mode.replace('_', ' ').toLowerCase();
        return out.isEmpty() ? out : Character.toUpperCase(out.charAt(0)) + out.substring(1);
    }

    private String status = "Status unknown";
    private String rankPrefix = "";

    public String getRankPrefix() {
        return rankPrefix;
    }

    /**
     * Rank needs the hypixel player object. The pv backend answers that route empty, so this
     * only fills in once an own api key is set.
     */
    public void loadPlayer() {
        com.teslamaps.api.HypixelApi.getPlayer(uuid).thenAccept(player -> {
            if (player == null || player.keySet().isEmpty()) return;
            rankPrefix = buildRank(player);
        });
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

    public String getGuildName() {
        return null;
    }

    public static void addToRecentPlayers(String playerName) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        config.recentPlayers.remove(playerName);
        config.recentPlayers.add(0, playerName);
        while (config.recentPlayers.size() > 10) {
            config.recentPlayers.remove(config.recentPlayers.size() - 1);
        }
        TeslaMapsConfig.save();
    }
}
