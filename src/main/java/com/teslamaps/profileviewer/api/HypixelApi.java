package com.teslamaps.profileviewer.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP client for official Hypixel API.
 * Requires API key from config.
 */
public class HypixelApi {
    private static final String HYPIXEL_API = "https://api.hypixel.net";
    private static final String MOJANG_API = "https://api.mojang.com";

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ExecutorService executor = Executors.newFixedThreadPool(3);

    // Simple in-memory cache
    private static final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final JsonObject data;
        final long expiresAt;

        CacheEntry(JsonObject data, long ttlMs) {
            this.data = data;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /**
     * Convert player name to UUID via Mojang API.
     */
    public static CompletableFuture<String> nameToUuid(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            String cacheKey = "uuid:" + playerName.toLowerCase();
            CacheEntry cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                return cached.data.get("id").getAsString();
            }

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(MOJANG_API + "/users/profiles/minecraft/" + playerName))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    TeslaMaps.LOGGER.warn("Mojang API returned {}: {}", response.statusCode(), playerName);
                    return null;
                }

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                // Cache UUID lookups for 1 hour
                cache.put(cacheKey, new CacheEntry(json, 3600000));

                return json.get("id").getAsString();
            } catch (Exception e) {
                TeslaMaps.LOGGER.error("Failed to get UUID for {}", playerName, e);
                return null;
            }
        }, executor);
    }

    /**
     * Get all Skyblock profiles for a player via Hypixel API.
     * Returns transformed profile data compatible with the viewer.
     * @param uuid Player UUID (not name)
     */
    public static CompletableFuture<JsonObject> getProfiles(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String cacheKey = "profiles:" + uuid;
            CacheEntry cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                return cached.data;
            }

            String apiKey = TeslaMapsConfig.get().hypixelApiKey;
            if (apiKey == null || apiKey.isEmpty()) {
                return createErrorResponse("No Hypixel API key configured! Set it in config.");
            }

            try {
                String url = HYPIXEL_API + "/skyblock/profiles?uuid=" + uuid;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("API-Key", apiKey)
                        .header("User-Agent", "TeslaMaps/1.0")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 403) {
                    return createErrorResponse("Invalid API key");
                }

                if (response.statusCode() == 429) {
                    return createErrorResponse("Rate limited - try again later");
                }

                if (response.statusCode() != 200) {
                    TeslaMaps.LOGGER.warn("Hypixel API returned {}", response.statusCode());
                    return createErrorResponse("Hypixel API error: " + response.statusCode());
                }

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                // Check for API error
                if (!json.has("success") || !json.get("success").getAsBoolean()) {
                    String cause = json.has("cause") ? json.get("cause").getAsString() : "Unknown error";
                    return createErrorResponse(cause);
                }

                if (!json.has("profiles") || json.get("profiles").isJsonNull()) {
                    return createErrorResponse("Player has no Skyblock profiles");
                }

                // Transform Hypixel format to our expected format
                JsonObject transformed = transformHypixelProfiles(json, uuid);

                // Cache the response
                int cacheTtl = TeslaMapsConfig.get().pvCacheDurationSeconds * 1000;
                cache.put(cacheKey, new CacheEntry(transformed, cacheTtl));

                return transformed;
            } catch (Exception e) {
                TeslaMaps.LOGGER.error("Failed to fetch Hypixel profiles for {}", uuid, e);
                return createErrorResponse("Network error: " + e.getMessage());
            }
        }, executor);
    }

    /**
     * Transform Hypixel API response to our expected format.
     */
    private static JsonObject transformHypixelProfiles(JsonObject hypixelResponse, String uuid) {
        JsonObject result = new JsonObject();
        JsonObject profiles = new JsonObject();

        if (!hypixelResponse.has("profiles")) {
            result.add("profiles", profiles);
            return result;
        }

        for (JsonElement profileElement : hypixelResponse.getAsJsonArray("profiles")) {
            JsonObject profile = profileElement.getAsJsonObject();
            String profileId = profile.get("profile_id").getAsString();
            String cuteName = profile.has("cute_name") ? profile.get("cute_name").getAsString() : "Unknown";

            // Get member data for this player
            JsonObject members = profile.getAsJsonObject("members");
            JsonObject memberData = members.has(uuid) ? members.getAsJsonObject(uuid) : new JsonObject();

            // Build profile object
            JsonObject profileObj = new JsonObject();
            profileObj.addProperty("profile_id", profileId);
            profileObj.addProperty("cute_name", cuteName);
            profileObj.addProperty("current", profile.has("selected") && profile.get("selected").getAsBoolean());
            profileObj.add("data", memberData);  // Member's profile data
            profileObj.add("raw", profile);      // Full profile with all members

            profiles.add(cuteName, profileObj);
        }

        result.add("profiles", profiles);
        return result;
    }

    /**
     * Get museum data for a profile.
     */
    public static CompletableFuture<JsonObject> getMuseum(String profileId) {
        return CompletableFuture.supplyAsync(() -> {
            String cacheKey = "museum:" + profileId;
            CacheEntry cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                return cached.data;
            }

            String apiKey = TeslaMapsConfig.get().hypixelApiKey;
            if (apiKey == null || apiKey.isEmpty()) {
                return new JsonObject();
            }

            try {
                String url = HYPIXEL_API + "/skyblock/museum?profile=" + profileId;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("API-Key", apiKey)
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    return new JsonObject();
                }

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                if (json.has("success") && json.get("success").getAsBoolean()) {
                    // Cache for 5 minutes
                    cache.put(cacheKey, new CacheEntry(json, 300000));
                    return json;
                }

                return new JsonObject();
            } catch (Exception e) {
                TeslaMaps.LOGGER.error("Failed to fetch museum data", e);
                return new JsonObject();
            }
        }, executor);
    }

    /**
     * Get garden data for a profile.
     */
    public static CompletableFuture<JsonObject> getGarden(String profileId) {
        return CompletableFuture.supplyAsync(() -> {
            String cacheKey = "garden:" + profileId;
            CacheEntry cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                return cached.data;
            }

            String apiKey = TeslaMapsConfig.get().hypixelApiKey;
            if (apiKey == null || apiKey.isEmpty()) {
                return new JsonObject();
            }

            try {
                String url = HYPIXEL_API + "/skyblock/garden?profile=" + profileId;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("API-Key", apiKey)
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    return new JsonObject();
                }

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                if (json.has("success") && json.get("success").getAsBoolean()) {
                    // Cache for 5 minutes
                    cache.put(cacheKey, new CacheEntry(json, 300000));
                    return json;
                }

                return new JsonObject();
            } catch (Exception e) {
                TeslaMaps.LOGGER.error("Failed to fetch garden data", e);
                return new JsonObject();
            }
        }, executor);
    }

    /**
     * Get bingo data for a player.
     */
    public static CompletableFuture<JsonObject> getBingo(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String cacheKey = "bingo:" + uuid;
            CacheEntry cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                return cached.data;
            }

            String apiKey = TeslaMapsConfig.get().hypixelApiKey;
            if (apiKey == null || apiKey.isEmpty()) {
                return new JsonObject();
            }

            try {
                String url = HYPIXEL_API + "/skyblock/bingo?uuid=" + uuid;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("API-Key", apiKey)
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    return new JsonObject();
                }

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                if (json.has("success") && json.get("success").getAsBoolean()) {
                    // Cache for 5 minutes
                    cache.put(cacheKey, new CacheEntry(json, 300000));
                    return json;
                }

                return new JsonObject();
            } catch (Exception e) {
                TeslaMaps.LOGGER.error("Failed to fetch bingo data", e);
                return new JsonObject();
            }
        }, executor);
    }

    private static JsonObject createErrorResponse(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("success", false);
        error.addProperty("error", message);
        return error;
    }

    /**
     * Clear the cache.
     */
    public static void clearCache() {
        cache.clear();
    }

    /**
     * Clear cache for a specific player.
     */
    public static void clearCacheForPlayer(String playerName) {
        cache.entrySet().removeIf(e -> e.getKey().toLowerCase().contains(playerName.toLowerCase()));
    }
}
