package com.teslamaps.features.capes;

import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Client-side cape. Bundled official capes plus anything the player drops into
 * {@code config/teslamaps/capes}, at any resolution as long as it keeps the 2:1
 * cape layout - a 512x256 texture renders exactly like the 64x32 one, just sharper.
 * Only the local player sees it; nothing is sent to the server.
 */
public class CapeManager {

    public static final Path DIR = FabricLoader.getInstance()
            .getConfigDir().resolve("teslamaps").resolve("capes");

    /** Bigger than this and the upload is not worth the VRAM. */
    public static final int MAX_WIDTH = 4096;

    public static final class Cape {
        public final String id, name, group;
        public final Path file;          // null for a bundled cape
        public int width = 64, height = 32;
        private Identifier texture;
        private boolean failed;

        Cape(String id, String name, String group, Identifier texture) {
            this.id = id; this.name = name; this.group = group;
            this.texture = texture; this.file = null;
        }

        Cape(String id, String name, String group, Path file) {
            this.id = id; this.name = name; this.group = group; this.file = file;
        }

        public boolean custom() { return file != null; }

        /** Decodes and uploads a custom cape the first time it is actually drawn. */
        public Identifier texture() {
            if (texture != null || failed) return texture;
            try (InputStream in = Files.newInputStream(file)) {
                NativeImage image = CapeImport.toCapeLayout(NativeImage.read(in));
                Identifier id = Identifier.fromNamespaceAndPath("teslamaps", "cape/" + this.id);
                Minecraft.getInstance().getTextureManager()
                        .register(id, new DynamicTexture(() -> "TeslaMaps cape", image));
                width = image.getWidth();
                height = image.getHeight();
                texture = id;
            } catch (Throwable t) {
                TeslaMaps.LOGGER.warn("Cape {} failed to load: {}", id, t.toString());
                failed = true;
            }
            return texture;
        }

        void release() {
            if (!custom()) return;   // a bundled cape's id is its resource path, never drop it
            if (texture != null) Minecraft.getInstance().getTextureManager().release(texture);
            texture = null;
            failed = false;
        }
    }

    private static final List<Cape> BUILTIN = List.of(
            builtin("minecon_2011", "MineCon 2011", "MineCon"),
            builtin("minecon_2012", "MineCon 2012", "MineCon"),
            builtin("minecon_2013", "MineCon 2013", "MineCon"),
            builtin("minecon_2015", "MineCon 2015", "MineCon"),
            builtin("minecon_2016", "MineCon 2016", "MineCon"),
            builtin("mojang", "Mojang", "Official"),
            builtin("mojang_studios", "Mojang Studios", "Official"),
            builtin("migrator", "Migrator", "Official"),
            builtin("translator", "Translator", "Official"),
            builtin("scrolls", "Scrolls", "Official"),
            builtin("realms_mapmaker", "Realms Mapmaker", "Official"),
            builtin("turtle", "Turtle", "Official"));

    private static final List<Cape> CUSTOM = new ArrayList<>();

    private static Cape resolved;        // cache for the per-frame lookup

    private static Cape builtin(String id, String name, String group) {
        return new Cape(id, name, group,
                Identifier.fromNamespaceAndPath("teslamaps", "textures/cape/" + id + ".png"));
    }

    public static void init() {
        try {
            Files.createDirectories(DIR);
            Path readme = DIR.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, """
                        Drop cape .png files in here and they show up in /tmap cape.

                        Layout is the vanilla one: a 2:1 texture with the cape at 1,1 to 11,17
                        of a 64x32 grid. Higher resolutions are supported and rendered sharper,
                        so 128x64, 256x128 and 512x256 all work. The old 22x17 format is padded
                        automatically. File name is the name you see in the menu.

                        Only you see the cape - nothing is sent to the server.
                        """);
            }
        } catch (Throwable t) {
            TeslaMaps.LOGGER.warn("Cape folder unavailable: {}", t.toString());
        }
        rescan();
    }

    /** Root pngs land in "Custom"; a subfolder is its own category named after the folder. */
    public static void rescan() {
        for (Cape cape : CUSTOM) cape.release();
        CUSTOM.clear();
        try (Stream<Path> entries = Files.list(DIR)) {
            entries.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(dir -> addFolder(dir, dir.getFileName().toString()));
        } catch (Throwable ignored) {
        }
        addFolder(DIR, "Custom");
        resolved = null;
    }

    private static void addFolder(Path dir, String group) {
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(p -> {
                        String file = p.getFileName().toString();
                        String name = file.substring(0, file.length() - 4);
                        // root capes keep their old id, so a saved selection survives the folders
                        String prefix = dir == DIR ? "custom_" : "custom_" + sanitize(group) + "_";
                        CUSTOM.add(new Cape(prefix + sanitize(name), name, group, p));
                    });
        } catch (Throwable ignored) {
        }
    }

    private static String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    public static List<Cape> builtins() { return BUILTIN; }

    public static List<Cape> customs() { return CUSTOM; }

    public static List<Cape> all() {
        List<Cape> all = new ArrayList<>(BUILTIN);
        all.addAll(CUSTOM);
        return all;
    }

    public static Cape byId(String id) {
        if (id == null || id.isEmpty()) return null;
        for (Cape cape : BUILTIN) if (cape.id.equals(id)) return cape;
        for (Cape cape : CUSTOM) if (cape.id.equals(id)) return cape;
        return null;
    }

    public static Cape selected() {
        return byId(TeslaMapsConfig.get().capeSelected);
    }

    public static void select(String id) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        cfg.capeSelected = id == null ? "" : id;
        if (!cfg.capeSelected.isEmpty()) cfg.capeEnabled = true;
        resolved = null;
        TeslaMapsConfig.save();
        // whatever is worn is what everyone sees - published on every change
        CapeSync.publishSelection(null);
    }

    public static boolean delete(Cape cape) {
        if (cape == null || !cape.custom()) return false;
        try {
            Files.deleteIfExists(cape.file);
        } catch (Throwable t) {
            return false;
        }
        if (cape.id.equals(TeslaMapsConfig.get().capeSelected)) select("");
        rescan();
        return true;
    }

    /** Called from the cape layer for every avatar rendered, so it stays a few field reads. */
    public static Identifier textureFor(AvatarRenderState state) {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (!cfg.section("Render")) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        if (state.id != mc.player.getId()) {
            // other players wear whatever the cape server says they picked
            return CapeSync.textureFor(state.id);
        }

        if (!cfg.capeEnabled || cfg.capeSelected.isEmpty()) return null;
        Cape cape = resolved;
        if (cape == null || !cape.id.equals(cfg.capeSelected)) {
            cape = byId(cfg.capeSelected);
            resolved = cape;
            if (cape == null) return null;
        }
        return cape.texture();
    }
}
