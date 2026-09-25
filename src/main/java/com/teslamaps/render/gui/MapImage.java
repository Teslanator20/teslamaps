package com.teslamaps.render.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.TeslaMaps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

// legitcatmod MapImageLoader: png/jpg from a folder as the dungeon map background
public class MapImage {

    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("map_backgrounds");
    private static final Identifier ID = Identifier.fromNamespaceAndPath("teslamaps", "map_background");
    private static final int MAX_SIZE = 1024;

    private static String loadedName;
    private static long loadedStamp;
    private static int texW, texH;
    private static boolean ready, pending;
    private static long lastCheck;

    public static String[] names() {
        List<String> out = new ArrayList<>();
        try {
            Files.createDirectories(DIR);
            try (Stream<Path> files = Files.list(DIR)) {
                files.filter(Files::isRegularFile).forEach(f -> {
                    String n = f.getFileName().toString();
                    String low = n.toLowerCase(Locale.ROOT);
                    if (low.endsWith(".png") || low.endsWith(".jpg") || low.endsWith(".jpeg")) out.add(n);
                });
            }
        } catch (IOException ignored) {}
        out.sort(String::compareToIgnoreCase);
        return out.isEmpty() ? new String[]{""} : out.toArray(new String[0]);
    }

    public static void openFolder() {
        try {
            Files.createDirectories(DIR);
        } catch (IOException ignored) {}
        net.minecraft.util.Util.getPlatform().openFile(DIR.toFile());
    }

    public static void draw(GuiGraphicsExtractor ctx, String name, int x, int y, int w, int h, float alpha) {
        if (name == null || name.isEmpty() || w <= 0 || h <= 0) return;
        long now = System.currentTimeMillis();
        if (!name.equals(loadedName) || now - lastCheck > 2000) {
            lastCheck = now;
            long stamp = stamp(name);
            if (!name.equals(loadedName) || stamp != loadedStamp) {
                loadedName = name;
                loadedStamp = stamp;
                request(name);
            }
        }
        if (!ready) return;

        float imgAspect = texW / (float) texH, boxAspect = w / (float) h;
        int uw = texW, vh = texH, u = 0, v = 0;
        if (imgAspect > boxAspect) { uw = Math.round(texH * boxAspect); u = (texW - uw) / 2; }
        else { vh = Math.round(texW / boxAspect); v = (texH - vh) / 2; }
        int a = Math.round(Math.clamp(alpha, 0f, 1f) * 255);
        ctx.blit(RenderPipelines.GUI_TEXTURED, ID, x, y, u, v, w, h, uw, vh, texW, texH, (a << 24) | 0xFFFFFF);
    }

    private static long stamp(String name) {
        try {
            return Files.getLastModifiedTime(DIR.resolve(name)).toMillis();
        } catch (IOException e) {
            return -1;
        }
    }

    private static void request(String name) {
        if (pending) return;
        pending = true;
        CompletableFuture.supplyAsync(() -> decode(name)).thenAcceptAsync(img -> {
            pending = false;
            if (!name.equals(loadedName)) { lastCheck = 0; loadedStamp = Long.MIN_VALUE; }
            if (img == null) {
                if (ready) Minecraft.getInstance().getTextureManager().release(ID);
                ready = false;
                return;
            }
            upload(img);
        }, Minecraft.getInstance());
    }

    private static BufferedImage decode(String name) {
        Path file = DIR.resolve(name);
        if (!Files.isRegularFile(file)) return null;
        try {
            BufferedImage src = ImageIO.read(file.toFile());
            if (src == null) return null;
            int w = src.getWidth(), h = src.getHeight();
            float f = Math.min(1f, MAX_SIZE / (float) Math.max(w, h));
            w = Math.max(1, Math.round(w * f));
            h = Math.max(1, Math.round(h * f));
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, w, h, null);
            g.dispose();
            return out;
        } catch (IOException | OutOfMemoryError e) {
            TeslaMaps.LOGGER.warn("Could not read map background {}", name, e);
            return null;
        }
    }

    private static void upload(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = px[y * w + x];
                image.setPixelABGR(x, y, (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF));
            }
        }
        if (ready) Minecraft.getInstance().getTextureManager().release(ID);
        Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(() -> "TeslaMaps map background", image));
        texW = w;
        texH = h;
        ready = true;
    }
}
