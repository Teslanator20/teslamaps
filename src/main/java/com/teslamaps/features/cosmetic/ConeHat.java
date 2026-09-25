package com.teslamaps.features.cosmetic;

// Ported from FloydAddons' features/impl/cosmetic/FloydConeHat.kt
import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Textured cone on your own head, texture from config/teslamaps/conehat. Only this client sees it. */
public final class ConeHat {

    public static final ImageFolder FOLDER = new ImageFolder("conehat", ".png");
    private static final Identifier CUSTOM = Identifier.fromNamespaceAndPath("teslamaps", "conehat/custom");
    private static final Identifier DEFAULT = Identifier.fromNamespaceAndPath("teslamaps", "conehat/default");

    private static boolean loaded;
    private static Identifier texture;
    private static float spin;
    private static long lastSpin;

    private ConeHat() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ConeHat").section("Render")
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Cone Hat");
                    b.toggle("Cone Hat", () -> c.coneHat, v -> c.coneHat = v)
                            .desc("A cone hat on your head. Texture: any PNG in config/teslamaps/conehat, wrapped around the cone. Only you see it.");
                    b.slider("  Height", 0.1f, 1.5f, () -> c.coneHatHeight, v -> c.coneHatHeight = v, 2);
                    b.slider("  Radius", 0.05f, 0.8f, () -> c.coneHatRadius, v -> c.coneHatRadius = v, 2);
                    b.slider("  Y Offset", -1.5f, 0.5f, () -> c.coneHatYOffset, v -> c.coneHatYOffset = v, 2);
                    b.slider("  Spin Speed", 0f, 360f, () -> c.coneHatSpin, v -> c.coneHatSpin = v, 0);
                    b.buttonRow(new String[]{"< Prev", "Next >", "Reload", "Folder"}, new Runnable[]{
                            () -> select(FOLDER.cycle(c.coneHatFile, -1)),
                            () -> select(FOLDER.cycle(c.coneHatFile, 1)),
                            () -> { loaded = false; ImageFolder.message("§7Cone hat reloaded"); },
                            FOLDER::open});
                });
    }

    private static void select(String name) {
        cfg().coneHatFile = name;
        TeslaMapsConfig.save();
        loaded = false;
    }

    public static boolean activeFor(int entityId) {
        TeslaMapsConfig c = cfg();
        if (!c.coneHat || !c.section("Render")) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getId() == entityId;
    }

    public static float rotation() {
        long now = System.currentTimeMillis();
        if (lastSpin != 0) spin = (spin + (now - lastSpin) / 1000f * cfg().coneHatSpin) % 360f;
        lastSpin = now;
        return spin;
    }

    public static Identifier texture() {
        if (loaded) return texture;
        loaded = true;
        texture = null;
        Path file = FOLDER.resolve(cfg().coneHatFile);
        if (file != null) {
            try (InputStream in = Files.newInputStream(file)) {
                Minecraft.getInstance().getTextureManager()
                        .register(CUSTOM, new DynamicTexture(() -> "TeslaMaps cone hat", NativeImage.read(in)));
                texture = CUSTOM;
            } catch (Throwable t) {
                TeslaMaps.LOGGER.warn("Cone hat {} failed to load: {}", file.getFileName(), t.toString());
            }
        }
        if (texture == null) {
            NativeImage img = new NativeImage(64, 16, false);
            for (int x = 0; x < 64; x++) {
                int col = (x / 8) % 2 == 0 ? 0xFFE84545 : 0xFFF5F5F5;
                for (int y = 0; y < 16; y++) img.setPixel(x, y, y >= 13 ? 0xFFF2C94C : col);
            }
            Minecraft.getInstance().getTextureManager().register(DEFAULT, new DynamicTexture(() -> "TeslaMaps cone hat", img));
            texture = DEFAULT;
        }
        return texture;
    }
}
