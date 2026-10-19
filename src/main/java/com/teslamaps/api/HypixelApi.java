package com.teslamaps.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.capes.CapeSync;

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

    /** Expired profile blobs are multi-MB each; without this every viewed player stayed in memory forever. */
    private static void evictExpired() {
        cache.values().removeIf(CacheEntry::isExpired);
    }

    /** The configured key, or null when it is blank or obvious junk ("reset") - junk cost a 403 round trip per request. */
    private static String configuredKey() {
        String key = TeslaMapsConfig.get().hypixelApiKey;
        if (key == null) return null;
        key = key.trim();
        return key.replace("-", "").length() == 32 ? key : null;
    }

    private record Reply(int status, String body) {}

    /** Own key straight to Hypixel, otherwise the teslamaps proxy. null = neither available. */
    private static Reply hypixel(String path) throws Exception {
        String apiKey = configuredKey();
        if (apiKey != null) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(HYPIXEL_API + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("API-Key", apiKey)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(), response.body());
        }
        if (!CapeSync.serverConfigured()) return null;
        JsonObject body = new JsonObject();
        body.addProperty("path", path);
        HttpResponse<String> response = CapeSync.callAuthed("hypixel", body);
        return response == null ? null : new Reply(response.statusCode(), response.body());
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
                    // mojang rate limits hard, playerdb is the fallback skyblock-pv uses too
                    TeslaMaps.LOGGER.warn("Mojang API returned {} for {}, trying playerdb",
                            response.statusCode(), playerName);
                    return viaPlayerDb(playerName, cacheKey);
                }

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                cache.put(cacheKey, new CacheEntry(json, 3600000));

                return json.get("id").getAsString();
            } catch (Exception e) {
                TeslaMaps.LOGGER.error("Failed to get UUID for {}", playerName, e);
                return viaPlayerDb(playerName, cacheKey);
            }
        }, executor);
    }

    private static String viaPlayerDb(String playerName, String cacheKey) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://playerdb.co/api/player/minecraft/" + playerName))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "TeslaMaps/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String id = json.getAsJsonObject("data").getAsJsonObject("player")
                    .get("raw_id").getAsString();

            JsonObject cached = new JsonObject();
            cached.addProperty("id", id);
            cache.put(cacheKey, new CacheEntry(cached, 3600000));
            return id;
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("playerdb lookup for {} failed: {}", playerName, e.toString());
            return null;
        }
    }

    public static void invalidateProfiles(String uuid) {
        cache.remove("profiles:" + uuid);
    }

    /** Profiles keyed by cute name, each with the member data of the given uuid. */
    public static CompletableFuture<JsonObject> getProfiles(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            evictExpired();
            String cacheKey = "profiles:" + uuid;
            CacheEntry cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                return cached.data;
            }

            try {
                Reply response = hypixel("/skyblock/profiles?uuid=" + uuid);
                if (response == null) return profilesViaPv(uuid, cacheKey);

                // a dead key should not kill the viewer, the pv backend still works
                if (response.status() == 403) {
                    TeslaMaps.LOGGER.warn("Hypixel rejected the API key, falling back to the PV API");
                    return profilesViaPv(uuid, cacheKey);
                }
                if (response.status() == 429) return error("Rate limited - try again later");
                if (response.status() != 200) {
                    TeslaMaps.LOGGER.warn("Hypixel API returned {}", response.status());
                    return profilesViaPv(uuid, cacheKey);
                }

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();

                if (!json.has("success") || !json.get("success").getAsBoolean()) {
                    return profilesViaPv(uuid, cacheKey);
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

    private static JsonObject extraViaPv(String pvPath, String kind, String cacheKey) {
        if (pvPath == null) return new JsonObject();
        JsonObject viaPv = PvApi.get(pvPath, kind).join();
        if (viaPv == null) return new JsonObject();
        cache.put(cacheKey, new CacheEntry(viaPv, 300000));
        return viaPv;
    }

    private static JsonObject profilesViaPv(String uuid, String cacheKey) {
        // the pv backend regularly whiffs the first request (cold auth) - retry while the
        // screen still says Loading instead of parking the user on "No profile data"
        JsonObject viaPv = null;
        for (int attempt = 0; attempt < 3 && viaPv == null; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            JsonObject got = PvApi.get("/profiles/" + PvApi.dashed(uuid), "profiles").join();
            if (got != null && got.has("profiles") && !got.get("profiles").isJsonNull()) viaPv = got;
        }
        if (viaPv == null) {
            return error("No profile data (PV API gave nothing, and no working Hypixel key)");
        }
        JsonObject transformed = transform(viaPv, uuid);
        cache.put(cacheKey, new CacheEntry(transformed, TeslaMapsConfig.get().pvCacheDurationSeconds * 1000));
        return transformed;
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
            String dashed = PvApi.dashed(uuid);
            JsonObject member = members.has(uuid) ? members.getAsJsonObject(uuid)
                    : members.has(dashed) ? members.getAsJsonObject(dashed) : new JsonObject();
            entry.add("data", member);
            entry.add("raw", profile);

            profiles.add(cuteName, entry);
        }

        result.add("profiles", profiles);
        return result;
    }

    public static CompletableFuture<JsonObject> getMuseum(String profileId) {
        return getExtra("museum", "/skyblock/museum?profile=" + profileId, "/museum/" + PvApi.dashed(profileId), profileId);
    }

    public static CompletableFuture<JsonObject> getGarden(String profileId) {
        return getExtra("garden", "/skyblock/garden?profile=" + profileId, "/garden/" + PvApi.dashed(profileId), profileId);
    }

    /** The PV API has no bingo route, so this one needs an own key. */
    public static CompletableFuture<JsonObject> getBingo(String uuid) {
        return getExtra("bingo", "/skyblock/bingo?uuid=" + uuid, null, uuid);
    }

    /** Current bingo card, keyless resources endpoint. */
    public static CompletableFuture<JsonObject> getBingoGoals() {
        return CompletableFuture.supplyAsync(() -> {
            CacheEntry cached = cache.get("bingo_goals");
            if (cached != null && !cached.isExpired()) return cached.data;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(HYPIXEL_API + "/resources/skyblock/bingo"))
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) return new JsonObject();
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!json.has("success") || !json.get("success").getAsBoolean()) return new JsonObject();
                cache.put("bingo_goals", new CacheEntry(json, 600000));
                return json;
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Failed to fetch bingo goals: {}", e.toString());
                return new JsonObject();
            }
        }, executor);
    }

    private static CompletableFuture<JsonObject> getExtra(String kind, String path, String pvPath, String id) {
        return CompletableFuture.supplyAsync(() -> {
            String cacheKey = kind + ":" + id;
            CacheEntry cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                return cached.data;
            }

            try {
                Reply response = hypixel(path);
                if (response == null || response.status() != 200) return extraViaPv(pvPath, kind, cacheKey);

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("success") && json.get("success").getAsBoolean()) {
                    cache.put(cacheKey, new CacheEntry(json, 300000));
                    return json;
                }
                return extraViaPv(pvPath, kind, cacheKey);
            } catch (Exception e) {
                TeslaMaps.LOGGER.error("Failed to fetch {} data", kind, e);
                return new JsonObject();
            }
        }, executor);
    }

    /** The hypixel player object - ranks, socials. The pv api answers empty here, so this needs a key. */
    public static CompletableFuture<JsonObject> getPlayer(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String cacheKey = "player:" + uuid;
            CacheEntry cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) return cached.data;

            try {
                Reply response = hypixel("/player?uuid=" + uuid);
                if (response == null || response.status() != 200) return new JsonObject();

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!json.has("player") || !json.get("player").isJsonObject()) return new JsonObject();

                JsonObject player = json.getAsJsonObject("player");
                cache.put(cacheKey, new CacheEntry(player, 600000));
                return player;
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Failed to fetch the hypixel player for {}: {}", uuid, e.toString());
                return new JsonObject();
            }
        }, executor);
    }

    /** Elitebot's public account mirror - playerData carries the rank fields, no key needed. */
    public static JsonObject getPlayerViaElite(String uuid) {
        {
            String cacheKey = "eliteplayer:" + uuid;
            CacheEntry cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) return cached.data;

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.elitebot.dev/account/" + uuid.replace("-", "")))
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", "TeslaMaps/1.0")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) return new JsonObject();

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!json.has("playerData") || !json.get("playerData").isJsonObject()) return new JsonObject();

                JsonObject player = json.getAsJsonObject("playerData");
                cache.put(cacheKey, new CacheEntry(player, 600000));
                return player;
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Elitebot player lookup for {} failed: {}", uuid, e.toString());
                return new JsonObject();
            }
        }
    }

    /** The player's guild name, or null. Needs an own key like the player object. */
    public static CompletableFuture<String> getGuildName(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String cacheKey = "guild:" + uuid;
            CacheEntry cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                return cached.data.has("name") ? cached.data.get("name").getAsString() : null;
            }

            try {
                Reply response = hypixel("/guild?player=" + uuid);
                if (response == null || response.status() != 200) return null;

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonObject result = new JsonObject();
                if (json.has("guild") && json.get("guild").isJsonObject()) {
                    JsonObject guild = json.getAsJsonObject("guild");
                    if (guild.has("name")) result.addProperty("name", guild.get("name").getAsString());
                }
                cache.put(cacheKey, new CacheEntry(result, 600000));
                return result.has("name") ? result.get("name").getAsString() : null;
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Failed to fetch the guild for {}: {}", uuid, e.toString());
                return null;
            }
        }, executor);
    }

    /** The whole guild object of a player (members, expHistory), or null. Own key first, pv backend otherwise. */
    public static CompletableFuture<JsonObject> getGuild(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String cacheKey = "guildfull:" + uuid;
            CacheEntry cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) return cached.data;
            JsonObject guild = null;
            try {
                Reply response = hypixel("/guild?player=" + uuid.replace("-", ""));
                if (response != null && response.status() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (json.has("guild") && json.get("guild").isJsonObject()) guild = json.getAsJsonObject("guild");
                    else guild = new JsonObject();
                }
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Guild lookup for {} failed: {}", uuid, e.toString());
            }
            if (guild == null) {
                JsonObject viaPv = PvApi.get("/guild/" + PvApi.dashed(uuid), "guild").join();
                if (viaPv != null && viaPv.has("guild") && viaPv.get("guild").isJsonObject()) guild = viaPv.getAsJsonObject("guild");
                else if (viaPv != null) guild = new JsonObject();
            }
            if (guild == null) return null;
            cache.put(cacheKey, new CacheEntry(guild, 300000));
            return guild;
        }, executor);
    }

    /** uuid -> current name via Mojang's profile API, cached an hour. */
    public static String uuidToName(String uuid) {
        String cacheKey = "name:" + uuid;
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return cached.data.get("name").getAsString();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MOJANG_API + "/user/profile/" + uuid.replace("-", "")))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!json.has("name")) return null;
            cache.put(cacheKey, new CacheEntry(json, 3600000));
            return json.get("name").getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    /** null = key works, otherwise the reason. Hypixel dropped /key, so this uses a real request. */
    public static CompletableFuture<String> validateKey(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String apiKey = configuredKey();
            if (apiKey == null || apiKey.isBlank()) return "No API key set";

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(HYPIXEL_API + "/player?uuid=" + uuid))
                        .timeout(Duration.ofSeconds(10))
                        .header("API-Key", apiKey)
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

    /** Dev only: feeds the viewer a synthetic profile so the layout can be checked offline. */
    public static void injectFake(String name, String uuid, JsonObject profiles) {
        JsonObject id = new JsonObject();
        id.addProperty("id", uuid);
        cache.put("uuid:" + name.toLowerCase(), new CacheEntry(id, 600000));
        cache.put("profiles:" + uuid, new CacheEntry(profiles, 600000));
    }

    public static void clearCache() {
        cache.clear();
    }
}
