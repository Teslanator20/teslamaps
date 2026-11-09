package com.teslamaps.features.capes;

import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.TeslaMaps;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import java.nio.ByteBuffer;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

/**
 * Brings a cape png in from disk or a url. One job at a time; the screen polls
 * {@link #fraction} and {@link #message} for the progress bar.
 */
public class CapeImport {

    private static final long MAX_BYTES = 16L * 1024 * 1024;   // tenor gifs get big
    private static final String TOO_BIG = "Larger than 16 MB";
    private static final int CHUNK = 16 * 1024;
    /** Enough of a share page to hold its meta tags. */
    private static final int MAX_PAGE_BYTES = 512 * 1024;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    /** Tried in order; the cdn links first, because og:image is sometimes only a preview. */
    private static final Pattern[] PAGE_MEDIA = {
            Pattern.compile("https?://(?:media\\d*|c)\\.tenor\\.com/[^\"'\\s<>]+\\.gif",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("https?://(?:media\\d*|i)\\.giphy\\.com/[^\"'\\s<>]+\\.gif",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("<meta[^>]+itemprop=[\"']contentUrl[\"'][^>]+content=[\"']([^\"']+)[\"']",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("[\"'](https?://[^\"'\\s<>]+\\.gif)[\"']", Pattern.CASE_INSENSITIVE),
    };

    public static volatile boolean running;
    public static volatile boolean failed;
    /** 0..1 while running, negative when the total size is unknown. */
    public static volatile float fraction;
    public static volatile String message = "";

    /** Set when a job finished, so the screen can jump to the new cape. */
    public static volatile String importedId;

    /** Native file picker (SDL, async). Filter memory lives until the callback ran. */
    public static void pickFile() {
        if (running) return;
        begin("Waiting for the file picker...");
        ByteBuffer name = MemoryUtil.memUTF8("PNG or GIF image");
        ByteBuffer pattern = MemoryUtil.memUTF8("png;gif");
        SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.calloc(1);
        filters.get(0).set(name, pattern);
        SDL_DialogFileCallback[] cb = new SDL_DialogFileCallback[1];
        cb[0] = SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
            String picked = null;
            boolean error = filelist == 0L;
            if (!error) {
                long first = MemoryUtil.memGetAddress(filelist);
                if (first != 0L) picked = MemoryUtil.memUTF8(first);
            }
            filters.free();
            MemoryUtil.memFree(name);
            MemoryUtil.memFree(pattern);
            cb[0].free();
            if (error) fail("File picker unavailable");
            else if (picked == null) reset();
            else fromFile(Path.of(picked));
        });
        try {
            SDLDialog.SDL_ShowOpenFileDialog(cb[0], 0L, Minecraft.getInstance().getWindow().handle(), filters, (CharSequence) null, false);
        } catch (Throwable t) {
            fail("File picker unavailable");
        }
    }

    /** Reads a local png with progress and stores it as a cape. */
    public static void fromFile(Path source) {
        message = "Reading " + source.getFileName();
        fraction = 0f;
        running = true;
        failed = false;
        new Thread(() -> {
            try {
                long size = Files.size(source);
                if (size > MAX_BYTES) {
                    fail(TOO_BIG);
                    return;
                }
                byte[] data;
                try (InputStream in = Files.newInputStream(source)) {
                    data = read(in, size);
                }
                store(stripExtension(source.getFileName().toString()), data);
            } catch (Throwable t) {
                fail(t.getClass().getSimpleName());
            }
        }, "TeslaMaps cape file").start();
    }

    /**
     * Downloads a png the player typed in themselves. No host allowlist here on
     * purpose - unlike a chat link this url cannot come from anyone else.
     */
    public static void fromUrl(String url) {
        fromUrl(url, null);
    }

    /** {@code name} overrides the file name, used by the online browser. */
    public static void fromUrl(String url, String name) {
        fromUrl(url, name, null);
    }

    /** {@code subfolder} places the file in a category folder, used by the gallery. */
    public static void fromUrl(String url, String name, String subfolder) {
        fromUrl(url, name, subfolder, null);
    }

    /**
     * {@code crop} is the crop a gallery gif was published with - passing it through means
     * the download lands ready to wear instead of asking the player to crop it again.
     */
    public static void fromUrl(String url, String name, String subfolder, CapeCompose.Crop crop) {
        if (running) return;
        String trimmed = url == null ? "" : url.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (Throwable t) {
            fail("Not a url");
            return;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            fail("Only http and https");
            return;
        }
        begin("Downloading...");
        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
                URI direct = shortcut(uri);
                HttpResponse<InputStream> response = direct != null
                        ? get(client, direct, false) : get(client, uri, true);
                if (direct != null && response.statusCode() != 200) {
                    response = get(client, uri, true);   // guessed wrong, fall back to the page
                }
                if (response.statusCode() != 200) {
                    fail("HTTP " + response.statusCode());
                    return;
                }
                String type = response.headers().firstValue("content-type")
                        .orElse("").toLowerCase(Locale.ROOT);
                if (type.contains("html") || type.contains("xml")) {
                    // a tenor/giphy/imgur page instead of the picture - pull the media out of it
                    message = "Looking for the gif...";
                    URI media;
                    try (InputStream in = response.body()) {
                        media = mediaFromPage(new String(in.readNBytes(MAX_PAGE_BYTES),
                                java.nio.charset.StandardCharsets.UTF_8), uri);
                    }
                    if (media == null) {
                        fail("No image on that page");
                        return;
                    }
                    message = "Downloading...";
                    response = get(client, media, false);
                    if (response.statusCode() != 200) {
                        fail("HTTP " + response.statusCode());
                        return;
                    }
                    String mediaType = response.headers().firstValue("content-type")
                            .orElse("").toLowerCase(Locale.ROOT);
                    if (mediaType.contains("html")) {
                        // never store a page as if it were a picture
                        fail("That link served a page, not an image");
                        return;
                    }
                }
                long size = response.headers().firstValueAsLong("content-length").orElse(-1L);
                if (size > MAX_BYTES) {
                    fail(TOO_BIG);
                    return;
                }
                byte[] data;
                try (InputStream in = response.body()) {
                    data = read(in, size);
                }
                TeslaMaps.LOGGER.info("[Cape] {} -> {} ({} bytes, {}, {})", uri, response.uri(),
                        data.length, response.headers().firstValue("content-type").orElse("?"),
                        magic(data));
                store(name == null || name.isBlank() ? nameFromUrl(uri) : name, data, subfolder, crop);
            } catch (Throwable t) {
                fail(t.getClass().getSimpleName());
            }
        }, "TeslaMaps cape download").start();
    }

    /**
     * {@code html} decides the Accept header, and that is not cosmetic: tenor's cdn
     * content-negotiates, so asking for text/html on a .gif url really does hand back
     * a html page with status 200.
     */
    private static HttpResponse<InputStream> get(HttpClient client, URI uri, boolean html)
            throws Exception {
        return client.send(
                HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(60))
                        // the gif sites hand a plain java client an empty page
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", html
                                ? "text/html,application/xhtml+xml,*/*;q=0.8"
                                : "image/gif,image/png,image/*;q=0.9,*/*;q=0.8")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
    }

    /**
     * Finds the real picture behind a share page. Tenor and Giphy both put it in
     * og:image, and their cdn urls are a good fallback when the tag is missing.
     */
    static URI mediaFromPage(String pageHtml, URI page) {
        // json blobs in the page escape their slashes
        String html = pageHtml.replace("\\/", "/");
        String id = pageId(page);
        URI first = null;
        for (Pattern pattern : PAGE_MEDIA) {
            Matcher matcher = pattern.matcher(html);
            while (matcher.find()) {
                String found = matcher.group(matcher.groupCount()).trim().replace("&amp;", "&");
                if (!isImage(found)) continue;
                URI media;
                try {
                    media = page.resolve(found);
                } catch (Throwable ignored) {
                    continue;
                }
                // a share page is full of other people's thumbnails - the right one carries its id
                if (id != null && found.contains(id)) return media;
                if (first == null) first = media;
            }
        }
        return first;
    }

    /** The id a share url ends with, e.g. giphy's "3o7abKhOpu0NwenH3O". */
    private static String pageId(URI page) {
        String path = page.getPath() == null ? "" : page.getPath();
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        int slash = path.lastIndexOf('/');
        String last = stripExtension(slash >= 0 ? path.substring(slash + 1) : path);
        int dash = last.lastIndexOf('-');
        if (dash >= 0) last = last.substring(dash + 1);
        return last.length() >= 6 ? last : null;
    }

    /**
     * Sites whose media url can be built from the share url, so no page fetch is needed.
     * Giphy serves a javascript page with no meta tags to a plain client.
     */
    private static URI shortcut(URI page) {
        String host = page.getHost() == null ? "" : page.getHost().toLowerCase(Locale.ROOT);
        if (!host.equals("giphy.com") && !host.endsWith(".giphy.com")) return null;
        String id = pageId(page);
        return id == null ? null : URI.create("https://i.giphy.com/" + id + ".gif");
    }

    /** First bytes as readable text, so a failed import says what actually arrived. */
    private static String magic(byte[] data) {
        int n = Math.min(12, data.length);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < n; i++) {
            char c = (char) (data[i] & 0xFF);
            out.append(c >= 0x20 && c < 0x7f ? c : '.');
        }
        return out.toString();
    }

    private static boolean isImage(String url) {
        String path = url.toLowerCase(Locale.ROOT);
        int query = path.indexOf('?');
        if (query > 0) path = path.substring(0, query);
        return path.endsWith(".gif") || path.endsWith(".png")
                || path.endsWith(".jpg") || path.endsWith(".jpeg");
    }

    private static byte[] read(InputStream in, long expected) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(expected > 0 ? (int) expected : CHUNK);
        byte[] buffer = new byte[CHUNK];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
            if (out.size() > MAX_BYTES) throw new IllegalArgumentException(TOO_BIG);
            fraction = expected > 0 ? Math.min(1f, (float) out.size() / expected) : -1f;
        }
        fraction = 1f;
        return out.toByteArray();
    }

    private static void store(String name, byte[] data) {
        store(name, data, null, null);
    }

    /** Validates on the main thread (texture upload) and writes the file. */
    private static void store(String name, byte[] data, String subfolder, CapeCompose.Crop crop) {
        message = "Checking texture...";
        if (GifCape.isGif(data)) {
            storeGif(name, data, subfolder, crop);
            return;
        }
        Minecraft.getInstance().execute(() -> {
            NativeImage image = null;
            try {
                image = toCapeLayout(decode(data));
                saveComposed(name, subfolder, image);
            } catch (IllegalArgumentException e) {
                if (String.valueOf(e.getMessage()).startsWith("Needs a 2:1")) {
                    // not a cape sheet - hand it to the crop editor instead of failing
                    reset();
                    com.teslamaps.screen.CapeCropScreen.open(name, data, subfolder);
                    return;
                }
                // say what arrived, otherwise "not readable" hides a downloaded html page
                fail(e.getMessage() + " (" + data.length + " bytes, " + magic(data) + ")");
            } catch (Throwable t) {
                TeslaMaps.LOGGER.warn("Cape import failed", t);
                fail("Import failed: " + t.getClass().getSimpleName());
            } finally {
                if (image != null) image.close();
            }
        });
    }

    /**
     * Gifs are stored as they came in - the frames are decoded when the cape is worn.
     * Only the layout of the canvas is checked here, so a broken gif fails at import
     * and not silently in the render path.
     */
    private static void storeGif(String name, byte[] data, String subfolder, CapeCompose.Crop crop) {
        try {
            int[] size = GifCape.canvasSize(data);
            int w = size[0], h = size[1];
            if (w > CapeManager.MAX_WIDTH || h > CapeManager.MAX_WIDTH) {
                fail("Larger than " + CapeManager.MAX_WIDTH + " px");
                return;
            }
            boolean layout = w == h * 2 || w * 11 == h * 23 || w * 17 == h * 22;
            if (!layout && crop == null) {
                // not a cape sheet - same deal as a png, pick the part that goes on the back
                reset();
                com.teslamaps.screen.CapeCropScreen.open(name, data, subfolder);
                return;
            }
            saveGif(name, subfolder, data, crop);
        } catch (Throwable t) {
            TeslaMaps.LOGGER.warn("Gif cape import failed", t);
            fail("Import failed: " + t.getClass().getSimpleName());
        }
    }

    /**
     * Writes the gif untouched, plus a ".crop" sidecar when it needs cropping - the frames
     * are composed while the cape is worn, so nothing is ever re-encoded.
     */
    public static void saveGif(String name, String subfolder, byte[] data, CapeCompose.Crop crop)
            throws Exception {
        String folder = subfolder == null || subfolder.isBlank()
                ? null : subfolder.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        Path dir = folder == null ? CapeManager.DIR : CapeManager.DIR.resolve(folder);
        Files.createDirectories(dir);
        Path target = freeName(dir, name, ".gif");
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temp, data);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        if (crop != null) Files.writeString(CapeManager.cropFile(target), crop.store());

        String id = "custom_" + (folder == null ? "" : sanitize(folder) + "_")
                + sanitize(stripExtension(target.getFileName().toString())) + "_gif";
        Minecraft.getInstance().execute(() -> {
            CapeManager.rescan();
            CapeManager.select(id);
            importedId = id;
            running = false;
            failed = false;
            fraction = 1f;
            message = "Added " + target.getFileName();
        });
    }

    /** stb first; anything stb refuses goes through ImageIO and gets re-encoded. */
    public static NativeImage decode(byte[] data) throws Exception {
        try {
            return NativeImage.read(data);
        } catch (Throwable stb) {
            java.awt.image.BufferedImage fallback =
                    javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data));
            if (fallback == null) throw new IllegalArgumentException("Not a readable image");
            java.awt.image.BufferedImage argb = new java.awt.image.BufferedImage(
                    fallback.getWidth(), fallback.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = argb.createGraphics();
            g.drawImage(fallback, 0, 0, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(argb, "png", out);
            return NativeImage.read(out.toByteArray());
        }
    }

    /**
     * Writes a finished cape sheet into the capes folder, rescans and wears it.
     * Shared by the normal import and the crop editor. Does NOT close the image.
     */
    public static void saveComposed(String name, String subfolder, NativeImage image) throws Exception {
        String folder = subfolder == null || subfolder.isBlank()
                ? null : subfolder.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        Path dir = folder == null ? CapeManager.DIR : CapeManager.DIR.resolve(folder);
        Files.createDirectories(dir);
        Path target = freeName(dir, name, ".png");
        // write the normalised image, so a padded legacy cape stays padded
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        image.writeToFile(temp);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);

        CapeManager.rescan();
        String id = "custom_" + (folder == null ? "" : sanitize(folder) + "_")
                + sanitize(stripExtension(target.getFileName().toString()));
        CapeManager.select(id);
        importedId = id;
        running = false;
        failed = false;
        fraction = 1f;
        message = "Added " + target.getFileName();
    }

    /**
     * Vanilla cape layout is a 2:1 texture. Anything already 2:1 passes straight through
     * at whatever resolution it has. OptiFine (46x22) and the old vanilla format (22x17)
     * put the cape at the same 1,1 offset but in a smaller sheet, so those get padded into
     * the 64x32 grid instead of rejected - capes.dev hands out 46x22 for every OptiFine cape.
     */
    static NativeImage toCapeLayout(NativeImage source) {
        int w = source.getWidth(), h = source.getHeight();
        if (w > CapeManager.MAX_WIDTH || h > CapeManager.MAX_WIDTH) {
            source.close();
            throw new IllegalArgumentException("Larger than " + CapeManager.MAX_WIDTH + " px");
        }
        if (w == h * 2) return source;

        // 46:22 is OptiFine, 22:17 is the pre-1.8 vanilla sheet; both scale up in whole steps
        int scale = w * 11 == h * 23 ? w / 46 : (w * 17 == h * 22 ? w / 22 : 0);
        if (scale > 0 && 64 * scale >= w && 32 * scale >= h) {
            NativeImage padded = new NativeImage(64 * scale, 32 * scale, true);
            source.copyRect(padded, 0, 0, 0, 0, w, h, false, false);
            source.close();
            return padded;
        }
        source.close();
        throw new IllegalArgumentException("Needs a 2:1 cape texture, got " + w + "x" + h);
    }

    private static Path freeName(Path dir, String base, String extension) {
        String clean = base.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (clean.isEmpty()) clean = "cape";
        if (clean.length() > 32) clean = clean.substring(0, 32);
        Path target = dir.resolve(clean + extension);
        for (int i = 2; Files.exists(target) && i < 100; i++) {
            target = dir.resolve(clean + " " + i + extension);
        }
        return target;
    }

    /** Must mirror CapeManager.sanitize, so the id built here matches the rescan. */
    private static String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private static String nameFromUrl(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        name = stripExtension(name);
        // tenor slugs read "six-seven-bird-aura-gif-17959378270241582062"
        name = name.replaceFirst("(?i)-gif-\\d+$", "").replace('-', ' ').trim();
        if (name.isEmpty()) return "downloaded cape";
        // texture cdns hand out 64 char hashes, which no ui can show
        return name.length() > 24 ? name.substring(0, 24) : name;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static void begin(String text) {
        running = true;
        failed = false;
        fraction = 0f;
        message = text;
        importedId = null;
    }

    /** Drops a finished job's message so a reopened screen starts clean. */
    public static void clear() {
        if (!running) reset();
    }

    private static void reset() {
        running = false;
        failed = false;
        fraction = 0f;
        message = "";
    }

    private static void fail(String text) {
        running = false;
        failed = true;
        fraction = 0f;
        message = text == null ? "Failed" : text;
    }
}
