package com.teslamaps.api;

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

public class HypixelApi {
    private static final String HYPIXEL_API = "https://api.hypixel.net/v2";
    private static final String MOJANG_API = "https://api.mojang.com";

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

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

                cache.put(cacheKey, new CacheEntry(json, 3600000));

                return json.get("id").getAsString();
            } catch (Exception e) {
                TeslaMaps.LOGGER.error("Failed to get UUID for {}", playerName, e);
                return null;
            }
        }, executor);
    }

    /** Profiles keyed by cute name, each with the member data of the given uuid. */
    public static CompletableFuture<JsonObject> getProfiles(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String cacheKey = "profiles:" + uuid;
            CacheEntry cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                return cached.data;
            }

            String apiKey = TeslaMapsConfig.get().hypixelApiKey;
            if (apiKey == null || apiKey.isBlank()) {
                return error("No Hypixel API key configured");
            }

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(HYPIXEL_API + "/skyblock/profiles?uuid=" + uuid))
                        .timeout(Duration.ofSeconds(15))
                        .header("API-Key", apiKey)
                        .header("User-Agent", "TeslaMaps/1.0")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 403) return error("Invalid API key");
                if (response.statusCode() == 429) return error("Rate limited - try again later");
                if (response.statusCode() != 200) {
                    TeslaMaps.LOGGER.warn("Hypixel API returned {}", response.statusCode());
                    return error("Hypixel API error: " + response.statusCode());
                }

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                if (!json.has("success") || !json.get("success").getAsBoolean()) {
                    return error(json.has("cause") ? json.get("cause").getAsString() : "Unknown error");
                }
                if (!json.has("profiles") || json.get("profiles").isJsonNull()) {
                    return error("Player has no Skyblock profiles");
                }

                JsonObject transformed = transform(json, uuid);
                cache.put(cacheKey, new CacheEntry(transformed, TeslaMapsConfig.get().pvCacheDurationSeconds * 1000));
                return transformed;
            } catch (Exception e) {
                TeslaMaps.LOGGER.error("Failed to fetch Hypixel profiles for {}", uuid, e);
                return error("Network error: " + e.getMessage());
            }
        }, executor);
    }

    private static JsonObject transform(JsonObject response, String uuid) {
        JsonObject result = new JsonObject();
        JsonObject profiles = new JsonObject();

        for (JsonElement element : response.getAsJsonArray("profiles")) {
            JsonObject profile = element.getAsJsonObject();
            String cuteName = profile.has("cute_name") ? profile.get("cute_name").getAsString() : "Unknown";
            JsonObject members = profile.getAsJsonObject("members");

            JsonObject entry = new JsonObject();
            entry.addProperty("profile_id", profile.get("profile_id").getAsString());
            entry.addProperty("cute_name", cuteName);
            entry.addProperty("current", profile.has("selected") && profile.get("selected").getAsBoolean());
            entry.add("data", members.has(uuid) ? members.getAsJsonObject(uuid) : new JsonObject());

            profiles.add(cuteName, entry);
        }

        result.add("profiles", profiles);
        return result;
    }

    /** null = key works, otherwise the reason. Hypixel dropped /key, so this uses a real request. */
    public static CompletableFuture<String> validateKey(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String apiKey = TeslaMapsConfig.get().hypixelApiKey;
            if (apiKey == null || apiKey.isBlank()) return "No API key set";

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(HYPIXEL_API + "/player?uuid=" + uuid))
                        .timeout(Duration.ofSeconds(10))
                        .header("API-Key", apiKey)
                        .header("User-Agent", "TeslaMaps/1.0")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) return null;

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                return json.has("cause") ? json.get("cause").getAsString() : "HTTP " + response.statusCode();
            } catch (Exception e) {
                return "Network error: " + e.getMessage();
            }
        }, executor);
    }

    private static JsonObject error(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("success", false);
        error.addProperty("error", message);
        return error;
    }

    public static void clearCache() {
        cache.clear();
    }
}
