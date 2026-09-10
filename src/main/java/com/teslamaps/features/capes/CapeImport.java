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
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * Brings a cape png in from disk or a url. One job at a time; the screen polls
 * {@link #fraction} and {@link #message} for the progress bar.
 */
public class CapeImport {

    private static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final int CHUNK = 16 * 1024;

    public static volatile boolean running;
    public static volatile boolean failed;
    /** 0..1 while running, negative when the total size is unknown. */
    public static volatile float fraction;
    public static volatile String message = "";

    /** Set when a job finished, so the screen can jump to the new cape. */
    public static volatile String importedId;

    /** Native file picker. Blocks, so it gets its own thread. */
    public static void pickFile() {
        if (running) return;
        begin("Waiting for the file picker...");
        new Thread(() -> {
            String picked;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.png"));
                filters.flip();
                picked = TinyFileDialogs.tinyfd_openFileDialog(
                        "Choose a cape texture", null, filters, "PNG image", false);
            } catch (Throwable t) {
                fail("File picker unavailable");
                return;
            }
            if (picked == null) {
                reset();
                return;
            }
            fromFile(Path.of(picked));
        }, "TeslaMaps cape picker").start();
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
                    fail("Larger than 8 MB");
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
                HttpResponse<InputStream> response = client.send(
                        HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    fail("HTTP " + response.statusCode());
                    return;
                }
                long size = response.headers().firstValueAsLong("content-length").orElse(-1L);
                if (size > MAX_BYTES) {
                    fail("Larger than 8 MB");
                    return;
                }
                byte[] data;
                try (InputStream in = response.body()) {
                    data = read(in, size);
                }
                store(name == null || name.isBlank() ? nameFromUrl(uri) : name, data, subfolder);
            } catch (Throwable t) {
                fail(t.getClass().getSimpleName());
            }
        }, "TeslaMaps cape download").start();
    }

    private static byte[] read(InputStream in, long expected) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(expected > 0 ? (int) expected : CHUNK);
        byte[] buffer = new byte[CHUNK];
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
            if (out.size() > MAX_BYTES) throw new IllegalArgumentException("Larger than 8 MB");
            fraction = expected > 0 ? Math.min(1f, (float) out.size() / expected) : -1f;
        }
        fraction = 1f;
        return out.toByteArray();
    }

    private static void store(String name, byte[] data) {
        store(name, data, null);
    }

    /** Validates on the main thread (texture upload) and writes the file. */
    private static void store(String name, byte[] data, String subfolder) {
        message = "Checking texture...";
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
                fail(e.getMessage());
            } catch (Throwable t) {
                TeslaMaps.LOGGER.warn("Cape import failed", t);
                fail("Import failed: " + t.getClass().getSimpleName());
            } finally {
                if (image != null) image.close();
            }
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
        Path target = freeName(dir, name);
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

    private static Path freeName(Path dir, String base) {
        String clean = base.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (clean.isEmpty()) clean = "cape";
        if (clean.length() > 32) clean = clean.substring(0, 32);
        Path target = dir.resolve(clean + ".png");
        for (int i = 2; Files.exists(target) && i < 100; i++) {
            target = dir.resolve(clean + " " + i + ".png");
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
