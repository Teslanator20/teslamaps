package com.teslamaps.features;

// Ported from Skysoft's features/chat/ImageLinkPreview.kt + RemoteImageLoader.kt (fixed host list, no per-host trust)
import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.KeyNames;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Hover preview for image links in open chat. Only a fixed list of big image hosts is ever fetched. */
public final class ImageLinkPreview {

    private ImageLinkPreview() {}

    private static final Set<String> HOSTS = Set.of("i.imgur.com", "imgur.com", "www.imgur.com",
            "cdn.discordapp.com", "media.discordapp.net", "pbs.twimg.com");
    private static final Set<String> EXT = Set.of("png", "jpg", "jpeg", "gif", "bmp");
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_SIDE = 4096;
    private static final int CACHE = 8;
    private static final int MAX_W = 420, MAX_H = 236;

    private record Loaded(Identifier id, int width, int height) {}

    private static final Map<String, Loaded> TEXTURES = new LinkedHashMap<>();
    private static final Map<String, String> ERRORS = new HashMap<>();
    private static final Set<String> LOADING = new HashSet<>();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static int lastX = Integer.MIN_VALUE, lastY, recheck;
    private static String url;
    private static URI request;
    private static int texCounter;

    public static void register() {
        Feature.of("ImageLinkPreview")
                .config("Chat", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("Image Links");
                    b.toggle("Image Link Preview", () -> c.imageLinkPreview, v -> c.imageLinkPreview = v)
                            .desc("Hover an image link in open chat to see it. Only imgur, Discord CDN and Twitter images are loaded, nothing else.");
                    b.keybind("  Hold To Preview", () -> c.imageLinkPreviewKey, v -> c.imageLinkPreviewKey = v)
                            .desc("Unbound = always preview on hover");
                });
    }

    /** From ChatScreen.extractRenderState. */
    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.imageLinkPreview || !c.section("Chat")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mouseX != lastX || mouseY != lastY || ++recheck >= 10) {
            lastX = mouseX;
            lastY = mouseY;
            recheck = 0;
            find(mc, mouseX, mouseY);
        }
        if (url == null) return;
        boolean show = c.imageLinkPreviewKey < 0 || KeyNames.isDown(c.imageLinkPreviewKey);
        Loaded img = TEXTURES.get(url);
        if (show && img == null && !LOADING.contains(url) && !ERRORS.containsKey(url)) load(url, request);

        String status = !show ? "§7Hold " + keyName(c.imageLinkPreviewKey) + " to show the image"
                : ERRORS.containsKey(url) ? "§c" + ERRORS.get(url)
                : img == null ? "§7Loading image..." : null;
        int w = 0, h = 0;
        if (show && img != null) {
            float fit = Math.min(1f, Math.min((float) MAX_W / img.width(), (float) MAX_H / img.height()));
            w = Math.max(1, Math.round(img.width() * fit));
            h = Math.max(1, Math.round(img.height() * fit));
        }
        String shown = mc.font.plainSubstrByWidth(url, MAX_W);
        int textW = Math.max(mc.font.width(shown), status == null ? 0 : mc.font.width(status));
        int pw = Math.max(w, textW) + 10;
        int ph = h + (h > 0 ? 4 : 0) + 10 + (status == null ? 0 : 10) + 10;
        int sw = ctx.guiWidth(), sh = ctx.guiHeight();
        int x = mouseX + 12 + pw <= sw - 4 ? mouseX + 12 : mouseX - pw - 12;
        x = Math.max(4, Math.min(x, sw - pw - 4));
        int y = Math.max(4, Math.min(mouseY - ph / 2, sh - ph - 4));

        HudStyle.screenPanel(ctx, x, y, pw, ph, 4);
        int cy = y + 5;
        if (w > 0) {
            int ix = x + (pw - w) / 2;
            ctx.blit(img.id(), ix, cy, ix + w, cy + h, 0f, 1f, 0f, 1f);
            cy += h + 4;
        }
        ctx.text(mc.font, shown, x + 5, cy, 0xFFFFFFFF, false);
        if (status != null) ctx.text(mc.font, status, x + 5, cy + 10, 0xFFAAAAAA, false);
    }

    private static String keyName(int key) {
        return KeyNames.display(key);
    }

    private static void find(Minecraft mc, int mouseX, int mouseY) {
        ActiveTextCollector.ClickableStyleFinder finder = new ActiveTextCollector.ClickableStyleFinder(mc.font, mouseX, mouseY);
        mc.gui.getChat().captureClickableText(finder, mc.getWindow().getGuiScaledHeight(), mc.gui.getGuiTicks(),
                ChatComponent.DisplayMode.FOREGROUND);
        Style style = finder.result();
        if (style == null || !(style.getClickEvent() instanceof ClickEvent.OpenUrl open)) {
            url = null;
            return;
        }
        String u = open.uri().toASCIIString();
        if (u.equals(url)) return;
        URI req = resolve(open.uri());
        url = req == null ? null : u;
        request = req;
    }

    /** The request uri for a trusted image link, else null. */
    static URI resolve(URI uri) {
        try {
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null) return null;
            if (uri.getPort() != -1 && uri.getPort() != 443) return null;
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
            if (host == null || !HOSTS.contains(host)) return null;
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (host.equals("imgur.com") || host.equals("www.imgur.com")) {
                String id = path.replaceAll("^/+|/+$", "");
                return id.matches("[A-Za-z0-9]{3,16}") ? URI.create("https://i.imgur.com/" + id + ".png") : null;
            }
            String query = uri.getRawQuery();
            String format = null;
            if (query != null) {
                for (String p : query.split("&")) if (p.startsWith("format=")) format = p.substring(7).toLowerCase(Locale.ROOT);
            }
            if ("webp".equals(format) && (host.equals("media.discordapp.net") || host.equals("pbs.twimg.com"))) {
                return new URI("https", null, host, -1, path, query.replaceAll("(?i)format=webp", "format=png"), null);
            }
            int dot = path.lastIndexOf('.');
            String ext = format != null ? format : dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
            return EXT.contains(ext) ? uri : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean trusted(URI uri) {
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme()) && host != null && HOSTS.contains(host.toLowerCase(Locale.ROOT));
    }

    private static void load(String key, URI first) {
        LOADING.add(key);
        CompletableFuture.runAsync(() -> {
            try {
                URI uri = first;
                HttpResponse<byte[]> res = null;
                for (int i = 0; i < 4; i++) {
                    if (!trusted(uri)) throw new IllegalStateException("Untrusted host");
                    res = CLIENT.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                            .header("User-Agent", "TeslaMaps").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                    int code = res.statusCode();
                    if (code != 301 && code != 302 && code != 303 && code != 307 && code != 308) break;
                    uri = uri.resolve(res.headers().firstValue("Location").orElseThrow());
                    res = null;
                }
                if (res == null) throw new IllegalStateException("Too many redirects");
                if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
                String type = res.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                if (!type.startsWith("image/")) throw new IllegalStateException("Not an image");
                byte[] data = res.body();
                if (data.length > MAX_BYTES) throw new IllegalStateException("Larger than 8 MB");
                byte[] png = toPng(data);
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> upload(key, png));
            } catch (Throwable t) {
                TeslaMaps.LOGGER.warn("Image preview {} failed: {}", key, t.toString());
                String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                Minecraft.getInstance().execute(() -> {
                    LOADING.remove(key);
                    ERRORS.put(key, msg);
                });
            }
        });
    }

    private static byte[] toPng(byte[] data) throws Exception {
        if (data.length > 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') return data;
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new ByteArrayInputStream(data));
        if (img == null) throw new IllegalStateException("Unsupported format");
        if (img.getWidth() > MAX_SIDE || img.getHeight() > MAX_SIDE) throw new IllegalStateException("Image too large");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static void upload(String key, byte[] png) {
        LOADING.remove(key);
        Minecraft mc = Minecraft.getInstance();
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            if (image.getWidth() > MAX_SIDE || image.getHeight() > MAX_SIDE) {
                image.close();
                ERRORS.put(key, "Image too large");
                return;
            }
            Identifier id = Identifier.fromNamespaceAndPath("teslamaps", "preview/" + (texCounter++));
            mc.getTextureManager().register(id, new DynamicTexture(() -> "TeslaMaps preview", image));
            TEXTURES.put(key, new Loaded(id, image.getWidth(), image.getHeight()));
            while (TEXTURES.size() > CACHE) {
                String oldest = TEXTURES.keySet().iterator().next();
                Loaded gone = TEXTURES.remove(oldest);
                if (gone != null) mc.getTextureManager().release(gone.id());
            }
        } catch (Throwable t) {
            ERRORS.put(key, "Not a readable image");
        }
    }
}
