package com.teslamaps.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.features.croesus.CraftCost;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Every crafting recipe of the NEU repo in one table. The repo zip is streamed once and only the
 * first crafting recipe per item is kept, cached in config/teslamaps/repo/neu-recipes.json.
 */
public final class NeuRecipeIndex {

    public record Entry(String name, boolean vanilla, int count, Map<String, Integer> ingredients) {}

    private static final String ZIP = "https://codeload.github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/zip/refs/heads/master";
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("repo").resolve("neu-recipes.json");
    private static final long MAX_AGE = 3L * 24 * 3600 * 1000;
    private static final Gson GSON = new Gson();

    private static volatile Map<String, Entry> recipes = Map.of();
    private static volatile boolean loading;
    private static volatile long lastTry;

    private NeuRecipeIndex() {}

    public static boolean loaded() { return !recipes.isEmpty(); }

    public static boolean loading() { return loading; }

    /** Null while not loaded; starts the load. */
    public static Map<String, Entry> recipesOrNull() {
        if (recipes.isEmpty()) load();
        return recipes.isEmpty() ? null : recipes;
    }

    public static void load() {
        long now = System.currentTimeMillis();
        if (loading || now - lastTry < 120_000L) return;
        lastTry = now;
        loading = true;
        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Entry> disk = readDisk();
                boolean fresh = disk != null && Files.getLastModifiedTime(FILE).toMillis() > now - MAX_AGE;
                if (disk != null) recipes = disk;
                if (fresh) return;
                try {
                    Map<String, Entry> live = download();
                    if (!live.isEmpty()) {
                        recipes = live;
                        Files.createDirectories(FILE.getParent());
                        Files.writeString(FILE, GSON.toJson(live), StandardCharsets.UTF_8);
                        TeslaMaps.LOGGER.info("[Recipes] {} crafting recipes from the NEU repo", live.size());
                    }
                } catch (Exception e) {
                    TeslaMaps.LOGGER.warn("[Recipes] repo download failed: {}", e.toString());
                }
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("[Recipes] load failed: {}", e.toString());
            } finally {
                loading = false;
            }
        });
    }

    private static Map<String, Entry> readDisk() {
        try {
            if (!Files.isRegularFile(FILE)) return null;
            Map<String, Entry> m = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8),
                    new TypeToken<HashMap<String, Entry>>() {}.getType());
            return m == null || m.isEmpty() ? null : m;
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Entry> download() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(ZIP)).timeout(Duration.ofMinutes(3))
                .header("User-Agent", "TeslaMaps").GET().build();
        HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) throw new RuntimeException("HTTP " + resp.statusCode());
        Map<String, Entry> out = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(resp.body())) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                String n = e.getName();
                if (e.isDirectory() || !n.endsWith(".json")) continue;
                int items = n.indexOf("/items/");
                if (items < 0 || n.indexOf('/', items + 7) >= 0) continue;
                try {
                    JsonObject o = JsonParser.parseReader(new InputStreamReader(zip, StandardCharsets.UTF_8)).getAsJsonObject();
                    List<CraftCost.Recipe> list = CraftCost.parse(o);
                    if (list.isEmpty() || !o.has("internalname")) continue;
                    CraftCost.Recipe r = list.getFirst();
                    String name = o.has("displayname") ? o.get("displayname").getAsString() : o.get("internalname").getAsString();
                    boolean vanilla = o.has("vanilla") && o.get("vanilla").getAsBoolean();
                    out.put(o.get("internalname").getAsString(), new Entry(name, vanilla, r.count(), r.ingredients()));
                } catch (Exception ignored) {
                }
            }
        }
        return out;
    }
}
