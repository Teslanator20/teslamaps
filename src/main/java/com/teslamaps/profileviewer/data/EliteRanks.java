package com.teslamaps.profileviewer.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Every elite leaderboard position of one profile in a single request, cached for five minutes. */
public class EliteRanks {
    private static final String API = "https://api.eliteskyblock.com";
    private static final long TTL = 5 * 60_000L;
    private static final int MAX_ENTRIES = 32;

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private record Entry(Map<String, Integer> ranks, long at) {}

    private static final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private static final Set<String> pending = ConcurrentHashMap.newKeySet();
    private static volatile Map<String, String> collectionBoards = Map.of();
    private static volatile boolean boardsRequested;
    private static volatile String current;
    private static volatile int version;

    /** Makes this profile the one {@link #rank} answers for, fetching it when it is not cached. */
    public static void request(String uuid, String profileId) {
        if (uuid == null || profileId == null || profileId.isEmpty()) return;
        String key = uuid.replace("-", "") + "/" + profileId;
        current = key;
        version++;
        loadBoards();

        Entry entry = cache.get(key);
        if (entry != null && System.currentTimeMillis() - entry.at() < TTL) return;
        if (!pending.add(key)) return;

        CompletableFuture.runAsync(() -> {
            Map<String, Integer> ranks = new HashMap<>();
            try {
                JsonObject root = get(API + "/leaderboards/" + key);
                JsonObject data = root != null && root.has("ranks") && root.get("ranks").isJsonObject()
                        ? root.getAsJsonObject("ranks") : new JsonObject();
                for (Map.Entry<String, JsonElement> e : data.entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonElement rank = e.getValue().getAsJsonObject().get("rank");
                    if (rank != null && rank.isJsonPrimitive() && rank.getAsJsonPrimitive().isNumber()) {
                        ranks.put(e.getKey(), rank.getAsInt());
                    }
                }
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Elite leaderboard lookup for {} failed: {}", key, e.toString());
            }
            long now = System.currentTimeMillis();
            cache.values().removeIf(old -> now - old.at() >= TTL);
            if (cache.size() >= MAX_ENTRIES) cache.clear();
            cache.put(key, new Entry(Map.copyOf(ranks), now));
            pending.remove(key);
            version++;
        });
    }

    /** Collection id to leaderboard id, fetched once a session. */
    private static void loadBoards() {
        if (boardsRequested) return;
        boardsRequested = true;
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject root = get(API + "/leaderboards");
                JsonObject boards = root != null && root.has("leaderboards") && root.get("leaderboards").isJsonObject()
                        ? root.getAsJsonObject("leaderboards") : new JsonObject();
                Map<String, String> out = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : boards.entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject info = e.getValue().getAsJsonObject();
                    JsonElement collection = info.get("collection");
                    JsonElement interval = info.get("intervalType");
                    if (collection == null || !collection.isJsonPrimitive()) continue;
                    if (interval != null && interval.isJsonPrimitive()
                            && !interval.getAsString().equalsIgnoreCase("current")) continue;
                    out.put(collection.getAsString(), e.getKey());
                }
                collectionBoards = Map.copyOf(out);
                version++;
            } catch (Exception e) {
                boardsRequested = false;
                TeslaMaps.LOGGER.warn("Elite leaderboard list failed: {}", e.toString());
            }
        });
    }

    private static JsonObject get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "TeslaMaps/1.0")
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return null;
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    /** Position on a leaderboard for the current profile, -1 when unranked or not loaded. */
    public static int rank(String board) {
        String key = current;
        Entry entry = key == null ? null : cache.get(key);
        if (entry == null) return -1;
        return entry.ranks().getOrDefault(board, -1);
    }

    public static int collectionRank(String collectionId) {
        String board = collectionBoards.get(collectionId);
        return board == null ? -1 : rank(board);
    }

    /** Bumps whenever something new arrived, so cached page trees know to rebuild. */
    public static int version() {
        return version;
    }

    /** "§dLeaderboard: #1.234", or null when there is nothing to show. */
    public static String line(int rank) {
        return rank > 0 ? "§dLeaderboard: #" + com.teslamaps.profileviewer.ui.Kit.dots(rank) : null;
    }
}
