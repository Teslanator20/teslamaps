package com.teslamaps.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
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

/**
 * Repo json with a disk copy: every good download is written to config/teslamaps/repo/, and when
 * GitHub / the API is down the last copy is used instead. Keeps a per-file status for /tmap repo.
 * Idea from SkyHanni's data/repo/AbstractRepoManager.
 */
public final class RepoCache {

    public record Status(String name, String source, long time, String error) {}

    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("repo");
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Map<String, Status> status = new LinkedHashMap<>();
    private static final Map<String, Runnable> reloaders = new LinkedHashMap<>();

    private RepoCache() {}

    /** Downloads and parses {@code url}; falls back to the disk copy. Blocking, call off the main thread. */
    public static JsonElement fetch(String name, String url, int timeoutSeconds) throws Exception {
        Path file = DIR.resolve(name + ".json");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds)).header("User-Agent", "TeslaMaps").GET().build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new RuntimeException("HTTP " + response.statusCode());
            JsonElement json = JsonParser.parseString(response.body());
            try {
                Files.createDirectories(DIR);
                Files.writeString(file, response.body(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[Repo] could not write cache {}: {}", name, e.toString());
            }
            put(new Status(name, "live", System.currentTimeMillis(), null));
            return json;
        } catch (Exception e) {
            if (Files.isRegularFile(file)) {
                try {
                    JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                    put(new Status(name, "cache", Files.getLastModifiedTime(file).toMillis(), e.toString()));
                    TeslaMaps.LOGGER.warn("[Repo] {} download failed ({}), using the disk copy", name, e.toString());
                    return json;
                } catch (Exception bad) {
                    TeslaMaps.LOGGER.warn("[Repo] disk copy of {} unreadable: {}", name, bad.toString());
                }
            }
            put(new Status(name, "failed", System.currentTimeMillis(), e.toString()));
            throw e;
        }
    }

    private static synchronized void put(Status s) {
        status.put(s.name(), s);
    }

    public static synchronized List<Status> status() {
        return new ArrayList<>(status.values());
    }

    /** A loader registers how to fetch itself again, for /tmap repo reload. */
    public static synchronized void onReload(String owner, Runnable reload) {
        reloaders.putIfAbsent(owner, reload);
    }

    public static void reloadAll() {
        List<Runnable> list;
        synchronized (RepoCache.class) {
            list = new ArrayList<>(reloaders.values());
        }
        for (Runnable r : list) {
            try {
                r.run();
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[Repo] reload failed: {}", e.toString());
            }
        }
    }
}
