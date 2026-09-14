package com.teslamaps.features.capes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Browses our own cape catalogue on the Supabase project (table cape_gallery,
 * files in the public "capes" bucket under gallery/<category>/) - no third-party
 * site involved anymore. Grouped by category, filtered locally.
 */
public class CapeGallery {

    private static final int THUMB_W = 256, THUMB_H = 128;
    private static final int MAX_PARALLEL = 10;
    private static final int THUMB_CACHE = 120;
    private static final long MAX_BYTES = 6L * 1024 * 1024;

    private static final Path CACHE_DIR = FabricLoader.getInstance()
            .getConfigDir().resolve("teslamaps").resolve("cape_thumbs");

    /** One client for everything - per-request clients killed the grid before. */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * One catalogue row. {@code path} is the storage key ("gallery/Baddies/Madelyn_Cline.gif"),
     * {@code crop} is set for an animated entry that is not already a cape sheet.
     */
    public record Entry(String path, String name, String category, CapeCompose.Crop crop) {
        /** The part below gallery/, extension included - unique, so it keys the thumb cache. */
        public String slug() { return path.substring("gallery/".length()); }

        public boolean animated() { return path.endsWith(".gif"); }
    }

    public static volatile boolean loading;
    public static volatile String status = "";
    public static volatile int generation;

    private static final List<Entry> CATALOGUE = new ArrayList<>();
    private static final List<Entry> RESULTS = new ArrayList<>();
    private static final Map<String, Identifier> THUMBS = new LinkedHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger inFlight = new AtomicInteger();

    private static String query = "";

    public static List<Entry> results() { return RESULTS; }

    public static int total() { return CATALOGUE.size(); }

    private static String baseUrl() {
        String url = TeslaMapsConfig.get().capeServerUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static String imageUrl(Entry entry) {
        return baseUrl() + "/storage/v1/object/public/capes/" + entry.path();
    }

    /** Drops the cached catalogue so the next search reloads it (after a gallery publish). */
    public static void refresh() {
        Minecraft.getInstance().execute(() -> {
            CATALOGUE.clear();
            search(query);
        });
    }

    /** Loads the catalogue once per session, then filters it locally. */
    public static void search(String text) {
        query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (!CATALOGUE.isEmpty()) {
            apply();
            return;
        }
        if (loading) return;
        loading = true;
        status = "§7Loading catalogue...";

        new Thread(() -> {
            try {
                String anon = TeslaMapsConfig.get().capeServerKey.trim();
                HttpResponse<String> response = HTTP.send(
                        HttpRequest.newBuilder(URI.create(baseUrl()
                                        + "/rest/v1/cape_gallery?select=path,name,category,crop&order=category,name"))
                                .timeout(Duration.ofSeconds(30))
                                .header("apikey", anon)
                                .header("Authorization", "Bearer " + anon)
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    fail("§cHTTP " + response.statusCode());
                    return;
                }
                List<Entry> found = new ArrayList<>();
                JsonArray rows = JsonParser.parseString(response.body()).getAsJsonArray();
                for (JsonElement element : rows) {
                    if (!element.isJsonObject()) continue;
                    JsonObject row = element.getAsJsonObject();
                    String path = string(row, "path");
                    String name = string(row, "name");
                    String category = string(row, "category");
                    if (path == null || name == null || !path.startsWith("gallery/")) continue;
                    if (!path.endsWith(".png") && !path.endsWith(".gif")) continue;
                    found.add(new Entry(path, name, category == null ? "Gallery" : category,
                            CapeCompose.Crop.parse(string(row, "crop"))));
                }
                Minecraft.getInstance().execute(() -> {
                    CATALOGUE.clear();
                    CATALOGUE.addAll(found);
                    loading = false;
                    apply();
                });
            } catch (Throwable t) {
                TeslaMaps.LOGGER.warn("Cape gallery failed: {}", t.toString());
                fail("§c" + t.getClass().getSimpleName());
            }
        }, "TeslaMaps cape gallery").start();
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    /** Grid rows are cheap but not free, so long result sets are cut and the cut is said out loud. */
    private static final int MAX_SHOWN = 400;

    private static void apply() {
        RESULTS.clear();
        int matched = 0;
        for (Entry entry : CATALOGUE) {
            if (!query.isEmpty()
                    && !entry.name().toLowerCase(Locale.ROOT).contains(query)
                    && !entry.category().toLowerCase(Locale.ROOT).contains(query)) continue;
            matched++;
            if (RESULTS.size() < MAX_SHOWN) RESULTS.add(entry);
        }
        generation++;
        if (matched > RESULTS.size()) {
            status = "§7" + RESULTS.size() + " of " + matched + " matches shown · filter further";
        } else if (query.isEmpty()) {
            status = "§7" + CATALOGUE.size() + " capes · type a name to filter";
        } else {
            status = "§7" + matched + " match \"" + query + "\"";
        }
    }

    private static void fail(String message) {
        Minecraft.getInstance().execute(() -> {
            loading = false;
            status = message;
        });
    }

    /**
     * The grid only asks for tiles it is about to draw, so this stays lazy. The full
     * png is decoded, scaled to a thumbnail and thrown away again - keeping the
     * originals on the gpu would be megabytes of vram per tile.
     */
    public static Identifier thumb(Entry entry) {
        String slug = entry.slug();
        Identifier known = THUMBS.get(slug);
        if (known != null) return known;
        if (inFlight.get() >= MAX_PARALLEL || !PENDING.add(slug)) return null;
        inFlight.incrementAndGet();

        new Thread(() -> {
            NativeImage small = null;
            try {
                Path cached = CACHE_DIR.resolve(slug.replace('/', '_') + ".thumb.png");
                if (Files.isRegularFile(cached)) {
                    small = NativeImage.read(Files.readAllBytes(cached));
                } else {
                    HttpResponse<byte[]> response = HTTP.send(
                            HttpRequest.newBuilder(URI.create(imageUrl(entry)))
                                    .timeout(Duration.ofSeconds(30)).GET().build(),
                            HttpResponse.BodyHandlers.ofByteArray());
                    if (response.statusCode() != 200 || response.body().length > MAX_BYTES) {
                        release(slug);
                        return;
                    }
                    // decoding and shrinking is plain cpu work, only the upload needs the render thread
                    // an animated entry shows its first frame, composed exactly like it renders
                    try (NativeImage full = entry.animated()
                            ? GifCape.firstSheet(response.body(), entry.crop())
                            : NativeImage.read(response.body())) {
                        small = new NativeImage(THUMB_W, THUMB_H, false);
                        full.resizeSubRectTo(0, 0, full.getWidth(), full.getHeight(), small);
                    }
                    try {
                        Files.createDirectories(CACHE_DIR);
                        small.writeToFile(cached);
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable t) {
                if (small != null) small.close();
                release(slug);
                return;
            }

            NativeImage ready = small;
            Minecraft.getInstance().execute(() -> {
                try {
                    Identifier id = Identifier.fromNamespaceAndPath("teslamaps",
                            "cape/gallery/" + slug.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
                    Minecraft.getInstance().getTextureManager()
                            .register(id, new DynamicTexture(() -> "TeslaMaps gallery", ready));
                    THUMBS.put(slug, id);
                    while (THUMBS.size() > THUMB_CACHE) {
                        String oldest = THUMBS.keySet().iterator().next();
                        Identifier gone = THUMBS.remove(oldest);
                        if (gone != null) Minecraft.getInstance().getTextureManager().release(gone);
                    }
                } catch (Throwable ignored) {
                    ready.close();
                } finally {
                    release(slug);
                }
            });
        }, "TeslaMaps gallery thumb").start();
        return null;
    }

    private static void release(String slug) {
        PENDING.remove(slug);
        inFlight.decrementAndGet();
    }
}
