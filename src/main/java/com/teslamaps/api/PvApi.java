package com.teslamaps.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * skyblock-pv's backend. Auth is a mojang session join, so no hypixel key is needed.
 * Only used while our own key application is pending - set one with /tmap apikey and
 * HypixelApi talks to hypixel directly again.
 */
public class PvApi {
    private static final String BASE = "https://skyblock-pv.thatgravyboat.tech";

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ExecutorService executor = Executors.newFixedThreadPool(2);

    private static volatile String key = null;
    private static volatile boolean authFailed = false;

    private static String userAgent() {
        String version = FabricLoader.getInstance().getModContainer("teslamaps")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
        return "TeslaMaps/" + version;
    }

    public static boolean authenticate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() == null) return false;

        try {
            String server = UUID.randomUUID().toString();
            UUID uuid = mc.getUser().getProfileId();

            mc.services().sessionService().joinServer(uuid, mc.getUser().getAccessToken(), server);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/authenticate"))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", userAgent())
                    .header("x-minecraft-username", mc.getUser().getName())
                    .header("x-minecraft-server", server)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || response.body().isBlank()) {
                TeslaMaps.LOGGER.warn("PV API auth returned {}", response.statusCode());
                authFailed = true;
                key = null;
                return false;
            }

            key = response.body().trim();
            authFailed = false;
            return true;
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to authenticate with the PV API", e);
            authFailed = true;
            key = null;
            return false;
        }
    }

    public static boolean isAuthenticated() {
        return key != null && !authFailed;
    }

    /** Empty object on any failure, so callers can stay dumb. */
    public static CompletableFuture<JsonObject> get(String path, String intent) {
        return CompletableFuture.supplyAsync(() -> request(path, intent, true), executor);
    }

    private static JsonObject request(String path, String intent, boolean retry) {
        if (key == null && !authenticate()) return null;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", userAgent())
                    .header("Authorization", key)
                    .header("X-Intent", intent)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401 && retry) {
                key = null;
                return authenticate() ? request(path, intent, false) : null;
            }
            if (response.statusCode() != 200) {
                TeslaMaps.LOGGER.warn("PV API {} returned {}", path, response.statusCode());
                return null;
            }

            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("PV API request failed: {}", path, e);
            return null;
        }
    }

    public static String dashed(String uuid) {
        if (uuid.contains("-")) return uuid;
        return uuid.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
    }
}
