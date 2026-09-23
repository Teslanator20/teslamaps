package com.teslamaps.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every sea creature has its own announcement sentence, so a catch is matched on the whole
 * message instead of guessing from the name. The list comes from the SkyHanni repo, which
 * keeps up with new fishing islands that a hardcoded list always lags behind.
 */
public class SeaCreatureList {

    private static final String URL =
            "https://raw.githubusercontent.com/hannibal002/SkyHanni-REPO/main/constants/SeaCreatures.json";

    /** rarity is COMMON..LEGENDARY, rare is the bold flag Hypixel gives the notable catches. */
    public record Info(String name, String rarity, boolean rare, String color) {}

    private static final Map<String, String> messageToName = new ConcurrentHashMap<>();
    private static final Map<String, Info> byName = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;
    private static volatile boolean loading = false;

    public static void load() {
        if (loaded || loading) return;
        loading = true;
        RepoCache.onReload("SeaCreatureList", SeaCreatureList::reload);

        CompletableFuture.runAsync(() -> {
            try {
                JsonObject root = RepoCache.fetch("sh-SeaCreatures", URL, 15).getAsJsonObject();
                for (Map.Entry<String, JsonElement> variant : root.entrySet()) {
                    JsonObject group = variant.getValue().getAsJsonObject();
                    JsonObject creatures = group.getAsJsonObject("sea_creatures");
                    if (creatures == null) continue;
                    String groupColor = group.has("chat_color") ? group.get("chat_color").getAsString() : "§b";
                    for (Map.Entry<String, JsonElement> entry : creatures.entrySet()) {
                        JsonObject info = entry.getValue().getAsJsonObject();
                        byName.put(entry.getKey(), new Info(
                                entry.getKey(),
                                info.has("rarity") ? info.get("rarity").getAsString() : "COMMON",
                                info.has("rare") && info.get("rare").getAsBoolean(),
                                info.has("chat_color") ? info.get("chat_color").getAsString() : groupColor));
                        put(info.has("chat_message") ? info.get("chat_message").getAsString() : null, entry.getKey());
                        if (!info.has("alternate_messages")) continue;
                        for (JsonElement alternate : info.getAsJsonArray("alternate_messages")) {
                            put(alternate.getAsString(), entry.getKey());
                        }
                    }
                }
                loaded = true;
                TeslaMaps.LOGGER.info("Loaded {} sea creature messages", messageToName.size());
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Sea creature list fetch failed: {}", e.toString());
            } finally {
                loading = false;
            }
        });
    }

    /** Drops the loaded flag and fetches again; the old tables stay until the new ones are in. */
    public static void reload() {
        if (loading) return;
        loaded = false;
        load();
    }

    private static void put(String message, String name) {
        if (message == null) return;
        String clean = StringUtil.strip(message).trim();
        if (!clean.isEmpty()) messageToName.put(clean, name);
    }

    public static boolean ready() {
        return loaded;
    }

    public static int size() {
        return messageToName.size();
    }

    /** @return the creature name, or null when the line is not a catch. */
    public static String nameFor(String cleanMessage) {
        return messageToName.get(cleanMessage.trim());
    }

    /** @return rarity, rare flag and chat color, or null when the creature is unknown. */
    public static Info info(String name) {
        return byName.get(name);
    }
}
