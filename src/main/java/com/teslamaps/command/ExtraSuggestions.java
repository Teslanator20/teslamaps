package com.teslamaps.command;

// Ported from Skyblocker's JoinInstance/RngMeter/Chapters/Viewstash/AuctionBazaar/WarpAutocomplete
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.PlayerChat;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Lists for /joininstance, /joindungeon, /joinkuudra, /rngmeter, /chapter, /viewstash,
 * /ahs, /bz and /setmaxspeed, plus a rank-filtered online /warp list. Remote lists come from
 * Skyblocker's public API (hysky.de), loaded once; the join lists have a built-in fallback.
 */
public final class ExtraSuggestions {

    private static final String API = "https://hysky.de/api/";
    private static final Path WARP_CACHE = FabricLoader.getInstance().getConfigDir()
            .resolve("teslamaps").resolve("warp_cache.json");
    private static final Set<String> MVP_PLUS = Set.of("MVP+", "MVP++", "YOUTUBE", "ADMIN", "GM", "MOD", "HELPER", "OWNER", "PIG+++", "STAFF");
    private static final String[] NUMBERS = {"ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN"};

    public static final List<String> VIEWSTASH = List.of("material", "item");
    public static final List<String> MAX_SPEED = List.of("100", "150", "200", "250", "300", "350", "400", "450", "500");

    private static volatile Map<String, String> instances = fallbackInstances();
    private static volatile Map<String, List<String>> rngMeters = Map.of();
    private static volatile List<String> chapters = List.of();
    private static volatile Map<String, Boolean> warps = Map.of();
    private static volatile boolean loaded = false;

    private ExtraSuggestions() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ExtraSuggestions").section("Chat")
                .when(() -> cfg().commandSuggestionsExtra)
                .chat(t -> learnRank());
        if (cfg().commandSuggestionsExtra) load();
    }

    /** Your rank from your own chat lines, for the /warp filter. */
    private static void learnRank() {
        PlayerChat.Msg m = PlayerChat.current();
        if (m == null || !m.own() || m.channel() == PlayerChat.Channel.NPC) return;
        cfg().ownHypixelRank = m.rank() == null ? "NONE" : m.rank();
    }

    public static boolean enabled() {
        return HypixelSuggestions.enabled() && cfg().commandSuggestionsExtra;
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject o = JsonParser.parseString(get("joininstances")).getAsJsonObject();
                Map<String, String> m = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) m.put(e.getKey(), e.getValue().getAsString());
                if (!m.isEmpty()) instances = m;
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[TeslaMaps] joininstance list: {}", e.toString());
            }
            try {
                JsonObject o = JsonParser.parseString(get("rngmeters")).getAsJsonObject();
                Map<String, List<String>> m = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    List<String> subs = new ArrayList<>();
                    for (JsonElement s : e.getValue().getAsJsonArray()) subs.add(s.getAsString());
                    m.put(e.getKey(), subs);
                }
                rngMeters = m;
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[TeslaMaps] rngmeter list: {}", e.toString());
            }
            try {
                List<String> l = new ArrayList<>();
                for (JsonElement s : JsonParser.parseString(get("chapters")).getAsJsonObject().getAsJsonArray("locations")) {
                    l.add(s.getAsString());
                }
                chapters = l;
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[TeslaMaps] chapters list: {}", e.toString());
            }
            String warpJson = null;
            try {
                warpJson = get("locations");
                Files.createDirectories(WARP_CACHE.getParent());
                Files.writeString(WARP_CACHE, warpJson, StandardCharsets.UTF_8);
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[TeslaMaps] warp list: {}", e.toString());
                try {
                    if (Files.exists(WARP_CACHE)) warpJson = Files.readString(WARP_CACHE, StandardCharsets.UTF_8);
                } catch (Exception ignored) {}
            }
            if (warpJson != null) {
                try {
                    Map<String, Boolean> m = new LinkedHashMap<>();
                    for (Map.Entry<String, JsonElement> e : JsonParser.parseString(warpJson).getAsJsonObject().entrySet()) {
                        m.put(e.getKey(), e.getValue().getAsBoolean());
                    }
                    warps = m;
                } catch (Exception e) {
                    TeslaMaps.LOGGER.warn("[TeslaMaps] warp list parse: {}", e.toString());
                }
            }
        });
    }

    private static String get(String path) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + path)).timeout(Duration.ofSeconds(20)).GET().build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
        return res.body();
    }

    private static Map<String, String> fallbackInstances() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("CATACOMBS_ENTRANCE", "Catacombs");
        for (String n : NUMBERS) m.put("CATACOMBS_FLOOR_" + n, "Catacombs");
        for (String n : NUMBERS) m.put("MASTER_CATACOMBS_FLOOR_" + n, "Catacombs");
        for (String k : new String[]{"NORMAL", "HOT", "BURNING", "FIERY", "INFERNAL"}) m.put("KUUDRA_" + k, "Kuudra");
        return m;
    }

    /** type = null (all), "Catacombs" or "Kuudra". */
    public static List<String> instances(String type) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : instances.entrySet()) {
            if (type == null || type.equalsIgnoreCase(e.getValue())) out.add(e.getKey());
        }
        return out;
    }

    public static List<String> rngTypes() {
        return new ArrayList<>(rngMeters.keySet());
    }

    public static List<String> rngSubtypes(String type) {
        for (Map.Entry<String, List<String>> e : rngMeters.entrySet()) {
            if (e.getKey().equalsIgnoreCase(type)) return e.getValue();
        }
        return List.of();
    }

    public static List<String> chapters() {
        return chapters;
    }

    /** Null = no online list yet, use the built-in one. */
    public static List<String> warps() {
        Map<String, Boolean> w = warps;
        if (w.isEmpty()) return null;
        String rank = cfg().ownHypixelRank;
        boolean all = rank == null || rank.isEmpty() || MVP_PLUS.contains(rank);
        List<String> out = new ArrayList<>(w.size());
        for (Map.Entry<String, Boolean> e : w.entrySet()) {
            if (all || !e.getValue()) out.add(e.getKey());
        }
        return out;
    }
}
