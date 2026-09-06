package com.teslamaps.render.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Still images the player drops into {@code config/teslamaps/backgrounds}, offered
 * alongside the procedural presets.
 * <p>
 * Files are decoded off-thread and downscaled to roughly what the window can
 * actually show — an 8K render is 130 MB of texture at full size and looks the
 * same on screen at a quarter of that.
 */
public class ImageBackgrounds {

    private static final Path DIR = FabricLoader.getInstance()
            .getConfigDir().resolve("teslamaps").resolve("backgrounds");

    private static final Set<String> EXTENSIONS = Set.of("png", "jpg", "jpeg");

    /** Cap on the decoded height. Anything taller is scaled down before upload. */
    private static final int MAX_HEIGHT = 2160;

    /** How many decoded images stay on the GPU; cycling past that releases the oldest. */
    private static final int CACHE_SIZE = 3;

    private record Loaded(Identifier id, int width, int height) {}

    private static final Map<String, Loaded> READY = new LinkedHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

    private static List<String> names = List.of();
    private static long lastScan;

    public static void init() {
        try {
            Files.createDirectories(DIR);
            Path readme = DIR.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, """
                        Drop .png or .jpg images in here and they show up as menu backgrounds
                        in /tmap -> Menu & Skybox, and in the eye button on the main menu.

                        The file name is the name you will see. 16:9 works best; anything else
                        is cropped to fill the screen. Images are scaled down to 2160p on load,
                        so there is no point shipping anything larger than 4K.

                        These are menu backgrounds only — the in-world skybox needs a shader.
                        """);
            }
        } catch (IOException e) {
            TeslaMaps.LOGGER.warn("Could not create the backgrounds folder", e);
        }
        rescan();
    }

    /** Re-reads the folder listing, at most once a second. */
    public static void rescan() {
        long now = System.currentTimeMillis();
        if (now - lastScan < 1000) return;
        lastScan = now;

        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.list(DIR)) {
            files.filter(Files::isRegularFile).forEach(f -> {
                String file = f.getFileName().toString();
                int dot = file.lastIndexOf('.');
                if (dot <= 0) return;
                if (!EXTENSIONS.contains(file.substring(dot + 1).toLowerCase(Locale.ROOT))) return;
                found.add(file.substring(0, dot));
            });
        } catch (IOException ignored) {
            return;
        }
        found.sort(String::compareToIgnoreCase);
        names = List.copyOf(found);
    }

    public static List<String> names() { return names; }

    public static boolean has(String name) { return names.contains(name); }

    /**
     * Draws the image over the whole screen, cropped to fill rather than squashed.
     * Returns false while the file is still decoding, so the caller can fall back.
     */
    public static boolean draw(GuiGraphicsExtractor ctx, String name) {
        Loaded loaded = READY.get(name);
        if (loaded == null) {
            request(name);
            return false;
        }

        int screenW = ctx.guiWidth();
        int screenH = ctx.guiHeight();
        float screenAspect = screenW / (float) screenH;
        float imageAspect = loaded.width() / (float) loaded.height();

        // cover fit: keep the short side full and crop the long one
        float u = 1.0f, v = 1.0f;
        if (imageAspect > screenAspect) u = screenAspect / imageAspect;
        else v = imageAspect / screenAspect;

        // slow drift, so a still image is not completely dead
        float t = (Minecraft.getInstance().getWindow() == null ? 0 : (System.nanoTime() / 1_000_000_000.0f))
                * TeslaMapsConfig.get().titleBackgroundSpeed * 0.05f;
        float zoom = 0.94f + 0.03f * (float) Math.sin(t * 0.7);
        u *= zoom;
        v *= zoom;
        float panU = (1.0f - u) * (0.5f + 0.5f * (float) Math.sin(t * 0.4));
        float panV = (1.0f - v) * (0.5f + 0.5f * (float) Math.cos(t * 0.3));

        ctx.blit(loaded.id(), 0, 0, screenW, screenH, panU, panU + u, panV, panV + v);

        int dim = Math.round(Math.clamp(TeslaMapsConfig.get().titleImageDim, 0f, 0.9f) * 255);
        if (dim > 0) ctx.fill(0, 0, screenW, screenH, dim << 24);
        return true;
    }

    private static void request(String name) {
        if (!PENDING.add(name)) return;

        CompletableFuture.supplyAsync(() -> decode(name)).thenAcceptAsync(decoded -> {
            PENDING.remove(name);
            if (decoded == null) return;
            upload(name, decoded);
        }, Minecraft.getInstance());
    }

    private record Decoded(int[] pixels, int width, int height) {}

    private static Decoded decode(String name) {
        for (String ext : EXTENSIONS) {
            Path file = DIR.resolve(name + "." + ext);
            if (!Files.exists(file)) continue;
            try {
                BufferedImage source = ImageIO.read(file.toFile());
                if (source == null) continue;

                int width = source.getWidth();
                int height = source.getHeight();
                if (height > MAX_HEIGHT) {
                    width = Math.max(1, Math.round(width * (MAX_HEIGHT / (float) height)));
                    height = MAX_HEIGHT;
                }

                BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(source, 0, 0, width, height, null);
                g.dispose();

                int[] pixels = scaled.getRGB(0, 0, width, height, null, 0, width);
                return new Decoded(pixels, width, height);
            } catch (IOException | OutOfMemoryError e) {
                TeslaMaps.LOGGER.warn("Could not read background image {}", name, e);
                return null;
            }
        }
        return null;
    }

    private static void upload(String name, Decoded decoded) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, decoded.width(), decoded.height(), false);
        for (int y = 0; y < decoded.height(); y++) {
            int row = y * decoded.width();
            for (int x = 0; x < decoded.width(); x++) {
                int argb = decoded.pixels()[row + x];
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                image.setPixelABGR(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }

        Identifier id = Identifier.fromNamespaceAndPath("teslamaps", "background/" + sanitize(name));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "TeslaMaps " + name, image));
        READY.put(name, new Loaded(id, decoded.width(), decoded.height()));

        while (READY.size() > CACHE_SIZE) {
            String oldest = READY.keySet().iterator().next();
            Loaded gone = READY.remove(oldest);
            if (gone != null) Minecraft.getInstance().getTextureManager().release(gone.id());
        }
    }

    private static String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }
}
