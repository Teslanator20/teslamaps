package com.teslamaps.features.cosmetic;

// Ported from FloydAddons' features/impl/cosmetic/FloydSkin.kt + mixin/AvatarRendererMixin.java
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

/** Local skin replacement from config/teslamaps/skins. Only this client sees it. */
public final class CustomSkin {

    public static final ImageFolder FOLDER = new ImageFolder("skins", ".png");
    private static final Identifier ID = Identifier.fromNamespaceAndPath("teslamaps", "skin/custom");

    private static boolean loaded;
    private static Identifier texture;

    private CustomSkin() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CustomSkin").section("Render")
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Custom Skin");
                    b.toggle("Custom Skin", () -> c.customSkin, v -> c.customSkin = v)
                            .desc("Replaces the skin with a 64x64 PNG from config/teslamaps/skins. Only you see it.");
                    b.toggle("  Self", () -> c.customSkinSelf, v -> c.customSkinSelf = v);
                    b.toggle("  Other Players", () -> c.customSkinOthers, v -> c.customSkinOthers = v);
                    b.buttonRow(new String[]{"< Prev", "Next >", "Reload", "Folder"}, new Runnable[]{
                            () -> select(FOLDER.cycle(c.customSkinFile, -1)),
                            () -> select(FOLDER.cycle(c.customSkinFile, 1)),
                            () -> { reload(); ImageFolder.message("§7Skin reloaded"); },
                            FOLDER::open});
                });
    }

    private static void select(String name) {
        cfg().customSkinFile = name;
        TeslaMapsConfig.save();
        reload();
    }

    public static void reload() {
        loaded = false;
    }

    /** Render thread, from AvatarRendererSkinMixin. Null = keep the real skin. */
    public static Identifier textureFor(int entityId) {
        TeslaMapsConfig c = cfg();
        if (!c.customSkin || !c.section("Render")) return null;
        Minecraft mc = Minecraft.getInstance();
        boolean self = mc.player != null && mc.player.getId() == entityId;
        if (self ? !c.customSkinSelf : !c.customSkinOthers) return null;
        return texture();
    }

    public static Identifier selfHandTexture() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : textureFor(mc.player.getId());
    }

    private static Identifier texture() {
        if (loaded) return texture;
        loaded = true;
        texture = null;
        Path file = FOLDER.resolve(cfg().customSkinFile);
        if (file == null) return null;
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage img = NativeImage.read(in);
            if (img.getWidth() != img.getHeight() || img.getWidth() < 64) {
                img.close();
                TeslaMaps.LOGGER.warn("Custom skin {} is not a square 64x64 skin", file.getFileName());
                return null;
            }
            Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(() -> "TeslaMaps skin", img));
            texture = ID;
        } catch (Throwable t) {
            TeslaMaps.LOGGER.warn("Custom skin {} failed to load: {}", file.getFileName(), t.toString());
        }
        return texture;
    }
}
