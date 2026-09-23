package com.teslamaps.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Which items live in a sack, from the same NEU repo TeslaMaps already reads item data from. */
public class SackItems {

    private static final String SACKS_URL =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/sacks.json";

    private static final Set<String> internalNames = ConcurrentHashMap.newKeySet();
    private static volatile Set<String> displayNames = Set.of();
    private static volatile Set<String> suggestions = Set.of();
    private static volatile boolean loaded = false;
    private static volatile boolean loading = false;

    public static void load() {
        if (loaded || loading) return;
        loading = true;
        RepoCache.onReload("SackItems", SackItems::reload);

        CompletableFuture.runAsync(() -> {
            try {
                JsonObject root = RepoCache.fetch("neu-sacks", SACKS_URL, 15).getAsJsonObject();
                JsonObject sacks = root.getAsJsonObject("sacks");
                for (Map.Entry<String, com.google.gson.JsonElement> entry : sacks.entrySet()) {
                    JsonObject sack = entry.getValue().getAsJsonObject();
                    if (!sack.has("contents")) continue;
                    for (var element : sack.getAsJsonArray("contents")) {
                        internalNames.add(element.getAsString().toUpperCase(Locale.ROOT));
                    }
                }
                loaded = true;
                TeslaMaps.LOGGER.info("Loaded {} sack items", internalNames.size());
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("Sack list fetch failed: {}", e.toString());
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

    /** False while the list is still downloading, so callers can skip validation instead of blocking. */
    public static boolean ready() {
        load();
        return loaded;
    }

    public static boolean isSackItem(String internalName) {
        return internalNames.contains(internalName.toUpperCase(Locale.ROOT));
    }

    /**
     * Mirrors SkyHanni's commandValidator: an internal name wins, otherwise the underscores are
     * read as spaces and matched against the display names.
     *
     * @return the internal name to send, or null when nothing matched.
     */
    public static String resolve(String query) {
        String internal = query.toUpperCase(Locale.ROOT).replace(':', '-');
        if (internalNames.contains(internal)) return internal;

        String name = internal.replace('_', ' ');
        String id = SkyblockItems.idForName(name);
        if (id != null) {
            String neu = id.toUpperCase(Locale.ROOT).replace(':', '-');
            if (internalNames.contains(neu)) return neu;
        }
        for (String known : names()) {
            if (known.equalsIgnoreCase(name)) return known;
        }
        return null;
    }

    /**
     * What tab completion offers: display names where Hypixel knows one, otherwise the raw
     * internal name, so the list is never empty while an item table is still missing.
     */
    public static Set<String> suggestions() {
        load();
        if (!suggestions.isEmpty()) return suggestions;
        if (internalNames.isEmpty()) return Set.of();

        Set<String> out = new java.util.TreeSet<>(names());
        Set<String> known = new HashSet<>();
        for (String name : out) known.add(name.toUpperCase(Locale.ROOT).replace(' ', '_'));
        for (String id : internalNames) {
            if (!known.contains(id.replace('-', '_'))) out.add(id);
        }
        // only cache once the item table is in, otherwise we freeze the fallback list
        if (SkyblockItems.loaded()) suggestions = out;
        return out;
    }

    /** Display names of every sack item, in Hypixel's casing. */
    public static Set<String> displayNames() {
        return names();
    }

    // Hypixel's own name -> id table gives us the display names; built once both lists are in
    private static Set<String> names() {
        if (!displayNames.isEmpty() || !SkyblockItems.loaded()) return displayNames;
        Set<String> hypixelIds = new HashSet<>();
        for (String id : internalNames) hypixelIds.add(id.replace('-', ':'));
        displayNames = SkyblockItems.displayNames(hypixelIds);
        return displayNames;
    }
}
