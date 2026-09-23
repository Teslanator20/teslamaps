package com.teslamaps.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Current mayor, minister and perks from the keyless election resource.
 * Ported from Skyblocker's MayorUtils / Devonian's MayorApi: refetch every 20 min, and
 * clear everything the moment a new mayor is elected so an outgoing Paul never counts.
 */
public final class ElectionApi {

    private static final String URL = "https://api.hypixel.net/v2/resources/skyblock/election";
    private static final long REFRESH_MS = 20 * 60_000L;
    private static final long RETRY_MS = 5 * 60_000L;
    private static final long SB_EPOCH = 1560275700000L;
    private static final long YEAR_MS = 446400000L;
    private static final long ELECTION_END_MS = 105600000L;

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private static volatile String mayor = "";
    private static volatile String minister = "";
    private static volatile Set<String> perks = Set.of();
    private static volatile boolean loaded = false;
    private static volatile boolean fetching = false;
    private static volatile long nextFetch = 0L;
    private static long clearAt = 0L;

    private ElectionApi() {}

    public static void register() {
        Feature.of("ElectionApi").when(ElectionApi::wanted).tick(ElectionApi::tick);
    }

    private static boolean wanted() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.paulAutoDetect || c.witherDragonPaulAuto;
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        if (clearAt == 0L) clearAt = nextElectionEnd(now);
        if (now >= clearAt) {
            mayor = "";
            minister = "";
            perks = Set.of();
            loaded = false;
            clearAt = nextElectionEnd(now + 60_000L);
            nextFetch = now + RETRY_MS;
            TeslaMaps.LOGGER.info("[Election] new mayor elected, perks cleared until the next fetch");
        }
        if (fetching || now < nextFetch) return;
        fetch();
    }

    public static void refresh() {
        nextFetch = 0L;
        if (!fetching) fetch();
    }

    private static void fetch() {
        fetching = true;
        nextFetch = System.currentTimeMillis() + REFRESH_MS;
        CompletableFuture<HttpResponse<String>> call;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(15)).GET().build();
            call = CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            call = CompletableFuture.failedFuture(e);
        }
        call.whenComplete((resp, err) -> {
            try {
                if (err != null) throw err instanceof java.util.concurrent.CompletionException && err.getCause() != null ? err.getCause() : err;
                if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode());
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                JsonObject m = root.getAsJsonObject("mayor");
                if (m == null) throw new IllegalStateException("no mayor");

                Set<String> p = new HashSet<>();
                addPerks(p, m.getAsJsonArray("perks"));
                String min = "";
                JsonObject mi = m.getAsJsonObject("minister");
                if (mi != null) {
                    min = str(mi, "name");
                    JsonObject mp = mi.getAsJsonObject("perk");
                    if (mp != null) p.add(str(mp, "name").toLowerCase(Locale.ROOT));
                }
                mayor = str(m, "name");
                minister = min;
                perks = Set.copyOf(p);
                loaded = true;
                TeslaMaps.LOGGER.info("[Election] mayor={} minister={} perks={}", mayor, minister, perks);
            } catch (Throwable e) {
                nextFetch = System.currentTimeMillis() + RETRY_MS;
                TeslaMaps.LOGGER.warn("[Election] fetch failed: {}", e.toString());
            } finally {
                fetching = false;
            }
        });
    }

    private static void addPerks(Set<String> out, JsonArray arr) {
        if (arr == null) return;
        for (JsonElement e : arr) {
            if (e.isJsonObject()) out.add(str(e.getAsJsonObject(), "name").toLowerCase(Locale.ROOT));
        }
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    /** Next "Late Spring 27th" in real time. */
    static long nextElectionEnd(long now) {
        long inYear = Math.floorMod(now - SB_EPOCH, YEAR_MS);
        long until = inYear < ELECTION_END_MS ? ELECTION_END_MS - inYear : YEAR_MS - inYear + ELECTION_END_MS;
        return now + until;
    }

    public static boolean isLoaded() { return loaded; }
    public static String mayor() { return mayor; }
    public static String minister() { return minister; }
    public static Set<String> perks() { return perks; }

    /** Perk name, case-insensitive, from the mayor or the minister. */
    public static boolean hasPerk(String name) {
        return perks.contains(name.toLowerCase(Locale.ROOT));
    }

    /** Paul's +10 score perk, mayor or minister. */
    public static boolean ezpz() {
        return hasPerk("ezpz");
    }

    /** Paul's blessings +25% perk. */
    public static boolean benediction() {
        return hasPerk("benediction");
    }
}
