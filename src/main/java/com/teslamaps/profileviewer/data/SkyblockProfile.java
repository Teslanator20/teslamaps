package com.teslamaps.profileviewer.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.TeslaMaps;

import java.util.*;

/**
 * Data for a single Skyblock profile.
 */
public class SkyblockProfile {
    private final String profileId;
    private final String cuteName;
    private final JsonObject fullProfileJson;
    private final JsonObject memberData;
    private final String ownerUuid;

    // Parsed data
    private final Map<String, SkillData> skills = new LinkedHashMap<>();
    private final DungeonData dungeonData;
    private final List<PetData> pets = new ArrayList<>();
    private final Map<String, Long> collections = new HashMap<>();

    // Additional data loaded separately
    private JsonObject museumData;
    private JsonObject gardenData;
    private JsonObject skyCryptData;  // Full SkyCrypt profile data

    public SkyblockProfile(String profileId, String cuteName, JsonObject fullProfileJson,
                           JsonObject memberData, String ownerUuid) {
        this.profileId = profileId;
        this.cuteName = cuteName;
        this.fullProfileJson = fullProfileJson;
        this.memberData = memberData;
        this.ownerUuid = ownerUuid;

        parseSkills();
        this.dungeonData = new DungeonData(memberData);
        parsePets();
        parseCollections();
    }

    private void parseSkills() {
        // Skill names and their API keys
        String[][] skillKeys = {
                {"farming", "Farming"},
                {"mining", "Mining"},
                {"combat", "Combat"},
                {"foraging", "Foraging"},
                {"fishing", "Fishing"},
                {"enchanting", "Enchanting"},
                {"alchemy", "Alchemy"},
                {"carpentry", "Carpentry"},
                {"runecrafting", "Runecrafting"},
                {"social", "Social"},
                {"taming", "Taming"}
        };

        JsonObject playerData = memberData.has("player_data") ?
                memberData.getAsJsonObject("player_data") : null;

        for (String[] skill : skillKeys) {
            String apiKey = skill[0];
            String displayName = skill[1];

            double xp = 0;
            // Try new API path first (player_data.experience.SKILL_<name>)
            if (playerData != null && playerData.has("experience")) {
                JsonObject exp = playerData.getAsJsonObject("experience");
                String expKey = "SKILL_" + apiKey;
                if (exp.has(expKey)) {
                    xp = exp.get(expKey).getAsDouble();
                }
            }

            skills.put(apiKey, new SkillData(displayName, xp, apiKey));
        }
    }

    private void parsePets() {
        try {
            // Pets can be in pets_data.pets or player_data.pets
            JsonArray petsArray = null;

            if (memberData.has("pets_data")) {
                JsonObject petsData = memberData.getAsJsonObject("pets_data");
                if (petsData.has("pets")) {
                    petsArray = petsData.getAsJsonArray("pets");
                }
            }

            if (petsArray == null) return;

            for (JsonElement elem : petsArray) {
                JsonObject petJson = elem.getAsJsonObject();
                String type = petJson.has("type") ? petJson.get("type").getAsString() : "Unknown";
                String tier = petJson.has("tier") ? petJson.get("tier").getAsString() : "COMMON";
                double exp = petJson.has("exp") ? petJson.get("exp").getAsDouble() : 0;
                boolean active = petJson.has("active") && petJson.get("active").getAsBoolean();
                String heldItem = petJson.has("heldItem") && !petJson.get("heldItem").isJsonNull() ?
                        petJson.get("heldItem").getAsString() : null;
                String skin = petJson.has("skin") && !petJson.get("skin").isJsonNull() ?
                        petJson.get("skin").getAsString() : null;

                pets.add(new PetData(type, tier, exp, active, heldItem, skin));
            }

            // Sort by active first, then by rarity, then by level
            pets.sort((a, b) -> {
                if (a.isActive() != b.isActive()) return a.isActive() ? -1 : 1;
                if (a.getRarityOrdinal() != b.getRarityOrdinal())
                    return Integer.compare(b.getRarityOrdinal(), a.getRarityOrdinal());
                return Integer.compare(b.getLevel(), a.getLevel());
            });
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to parse pets", e);
        }
    }

    private void parseCollections() {
        try {
            if (!memberData.has("collection")) return;

            JsonObject collectionJson = memberData.getAsJsonObject("collection");
            for (Map.Entry<String, JsonElement> entry : collectionJson.entrySet()) {
                collections.put(entry.getKey(), entry.getValue().getAsLong());
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to parse collections", e);
        }
    }

    // Getters
    public String getProfileId() { return profileId; }
    public String getCuteName() { return cuteName; }
    public JsonObject getFullProfileJson() { return fullProfileJson; }
    public JsonObject getMemberData() { return memberData; }
    public String getOwnerUuid() { return ownerUuid; }

    public Map<String, SkillData> getSkills() { return skills; }
    public DungeonData getDungeonData() { return dungeonData; }
    public List<PetData> getPets() { return pets; }
    public Map<String, Long> getCollections() { return collections; }

    public JsonObject getMuseumData() { return museumData; }
    public void setMuseumData(JsonObject museumData) { this.museumData = museumData; }

    public JsonObject getGardenData() { return gardenData; }
    public void setGardenData(JsonObject gardenData) { this.gardenData = gardenData; }

    public JsonObject getSkyCryptData() { return skyCryptData; }
    public void setSkyCryptData(JsonObject skyCryptData) { this.skyCryptData = skyCryptData; }

    /**
     * Calculate skill average.
     */
    public double getSkillAverage() {
        double total = 0;
        int count = 0;
        for (Map.Entry<String, SkillData> entry : skills.entrySet()) {
            // Exclude runecrafting and social from average
            if (entry.getKey().equals("runecrafting") || entry.getKey().equals("social")) continue;
            total += entry.getValue().getLevel();
            count++;
        }
        return count > 0 ? total / count : 0;
    }

    /**
     * Get coin purse amount.
     */
    public double getCoinPurse() {
        try {
            if (memberData.has("currencies")) {
                JsonObject currencies = memberData.getAsJsonObject("currencies");
                if (currencies.has("coin_purse")) {
                    return currencies.get("coin_purse").getAsDouble();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0;
    }

    /**
     * Get bank balance.
     */
    public double getBankBalance() {
        try {
            if (fullProfileJson.has("banking")) {
                JsonObject banking = fullProfileJson.getAsJsonObject("banking");
                if (banking.has("balance")) {
                    return banking.get("balance").getAsDouble();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0;
    }

    /**
     * Get fairy souls collected.
     */
    public int getFairySouls() {
        try {
            if (memberData.has("fairy_soul")) {
                JsonObject fairySoul = memberData.getAsJsonObject("fairy_soul");
                if (fairySoul.has("total_collected")) {
                    return fairySoul.get("total_collected").getAsInt();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0;
    }

    /**
     * Get Skyblock level.
     */
    public double getSkyblockLevel() {
        try {
            if (memberData.has("leveling")) {
                JsonObject leveling = memberData.getAsJsonObject("leveling");
                if (leveling.has("experience")) {
                    double xp = leveling.get("experience").getAsDouble();
                    // SB Level = XP / 100
                    return xp / 100.0;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0;
    }
}
