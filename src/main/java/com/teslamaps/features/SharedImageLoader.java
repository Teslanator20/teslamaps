package com.teslamaps.features;

import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.TeslaMaps;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Downloads and decodes a shared image once, then hands out the texture. */
public class SharedImageLoader {

    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final int MAX_SIDE = 8192;
    private static final int CACHE = 12;

    public record Loaded(Identifier id, int width, int height) {}

    private static final Map<String, Loaded> TEXTURES = new LinkedHashMap<>();
    private static final java.util.Set<String> loading = new java.util.HashSet<>();
    // why a url never turned into a picture, so the chat can say it instead of staying blank
    private static final Map<String, String> ERRORS = new LinkedHashMap<>();

    public static String error(String url) {
        return ERRORS.get(url);
    }

    public static boolean isLoading(String url) {
        return loading.contains(url);
    }

    public static Loaded cached(String url) {
        return TEXTURES.get(url);
    }

    /** Rejects anything not on the trusted host, so a link in chat cannot steer the client. */
    public static boolean trusted(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase();
            // plain endsWith would also take "notcatbox.moe"
            return host.equals(ImageShare.TRUSTED_HOST) || host.endsWith("." + ImageShare.TRUSTED_HOST);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void load(String url, Consumer<Loaded> onDone, Consumer<String> onError) {
        Loaded known = TEXTURES.get(url);
        if (known != null) {
            onDone.accept(known);
            return;
        }
        if (!trusted(url)) {
            ERRORS.put(url, "Untrusted host");
            onError.accept("Untrusted host");
            return;
        }
        if (!loading.add(url)) return;

        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
                HttpResponse<byte[]> response = client.send(
                        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() != 200) {
                    fail(url, "HTTP " + response.statusCode(), onError);
                    return;
                }
                TeslaMaps.LOGGER.info("Shared image {} downloaded, {} bytes", url, response.body().length);
                byte[] data = response.body();
                if (data.length > MAX_BYTES) {
                    fail(url, "Larger than 20 MB", onError);
                    return;
                }

                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    loading.remove(url);
                    try {
                        NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
                        // a tiny file can still decode to something enormous
                        if (image.getWidth() > MAX_SIDE || image.getHeight() > MAX_SIDE) {
                            image.close();
                            onError.accept("Image too large");
                            return;
                        }
                        Identifier id = Identifier.fromNamespaceAndPath("teslamaps",
                                "shared/" + Integer.toHexString(url.hashCode()));
                        mc.getTextureManager().register(id, new DynamicTexture(() -> "TeslaMaps shared", image));
                        Loaded loaded = new Loaded(id, image.getWidth(), image.getHeight());
                        TEXTURES.put(url, loaded);

                        while (TEXTURES.size() > CACHE) {
                            String oldest = TEXTURES.keySet().iterator().next();
                            Loaded gone = TEXTURES.remove(oldest);
                            if (gone != null) mc.getTextureManager().release(gone.id());
                        }
                        ERRORS.remove(url);
                        onDone.accept(loaded);
                    } catch (Throwable t) {
                        TeslaMaps.LOGGER.warn("Shared image {} decode failed: {}", url, t.toString());
                        ERRORS.put(url, "Not a readable image");
                        onError.accept("Not a readable image");
                    }
                });
            } catch (Throwable t) {
                TeslaMaps.LOGGER.warn("Shared image download failed: {}", t.toString());
                fail(url, t.getClass().getSimpleName(), onError);
            }
        });
    }

    private static void fail(String url, String message, Consumer<String> onError) {
        TeslaMaps.LOGGER.warn("Shared image {} failed: {}", url, message);
        Minecraft.getInstance().execute(() -> {
            loading.remove(url);
            ERRORS.put(url, message);
            onError.accept(message);
        });
    }
}
