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

                SkyblockProfile profile = new SkyblockProfile(profileId, cuteName, profileData, memberData, uuid);
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
        return "Status unavailable";
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
