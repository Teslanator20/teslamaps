package com.teslamaps.features.customize;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** One-off async pulls of the NEU repo constants the customize screen needs. */
final class CustomizeRepo {
    private static final String NEU =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/%s.json";

    private CustomizeRepo() {}

    static void load(String constant, Consumer<JsonElement> onLoaded) {
        CompletableFuture.runAsync(() -> {
            try {
                onLoaded.accept(com.teslamaps.utils.RepoCache.fetch("neu-" + constant, NEU.formatted(constant), 30));
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[Customize] failed to load {}.json: {}", constant, e.toString());
            }
        });
    }
}
