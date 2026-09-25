package com.teslamaps.profileviewer.screen;

import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.TeslaMaps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

// Ported from NEU's profileviewer/Panorama.java, as a scrolling strip of the four side faces
public final class PvPanorama {
    private static final int TEX_W = 768;
    private static final int TEX_H = 192;
    private static final long SB_EPOCH_MS = 1560275700000L;

    private static final Set<String> FILES = Set.of(
            "combat_1_day", "combat_1_night", "combat_3_day", "combat_3_night", "crimson_isle",
            "crystal_hollows", "dark_auction", "dungeon", "dynamic_day", "dynamic_night",
            "farming_1_day", "farming_1_night", "farming_2_day", "farming_2_night",
            "foraging_1_day", "foraging_1_night", "hub_day", "hub_night",
            "mining_1_day", "mining_1_night", "mining_2", "mining_3", "unknown");

    private static final Map<String, Identifier> LOADED = new HashMap<>();
    private static final Set<String> PENDING = new java.util.HashSet<>();

    private PvPanorama() {}

    /** NEU's lookup: location_day/night, then the plain location, then unknown. */
    public static String fileFor(String mode) {
        if (mode == null) return "unknown";
        if (mode.equals("dungeon_hub")) mode = "dungeon";
        String time = night() ? "_night" : "_day";
        if (FILES.contains(mode + time)) return mode + time;
        if (FILES.contains(mode)) return mode;
        return "unknown";
    }

    /** SkyBlock hour from the epoch: a day is 20 minutes, night before 6 and from 20. */
    private static boolean night() {
        long hour = ((System.currentTimeMillis() - SB_EPOCH_MS) % 1_200_000L) / 50_000L;
        return hour <= 6 || hour >= 20;
    }

    public static void draw(GuiGraphicsExtractor ctx, String file, int x, int y, int w, int h, int mouseX) {
        Identifier id = texture(file);
        if (id == null) return;

        float s = h / (float) TEX_H;
        float regionW = w / s;
        double spin = (System.currentTimeMillis() % 120_000L) / 120_000.0 * TEX_W;
        double follow = Math.max(-1, Math.min(1, (mouseX - (x + w / 2.0)) / 200.0)) * TEX_W / 12.0;
        float u = (float) (((spin + follow) % TEX_W + TEX_W) % TEX_W);

        float first = Math.min(regionW, TEX_W - u);
        int firstW = Math.min(w, Math.round(first * s));
        ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, 0, firstW, h,
                Math.round(first), TEX_H, TEX_W, TEX_H);
        if (firstW < w) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, id, x + firstW, y, 0, 0, w - firstW, h,
                    Math.round(regionW - first), TEX_H, TEX_W, TEX_H);
        }
        ctx.fill(x, y, x + w, y + h, 0x50000000);
    }

    /** Decoded once per file off thread, null until ready. */
    private static Identifier texture(String file) {
        Identifier id = LOADED.get(file);
        if (id != null || PENDING.contains(file)) return id;
        PENDING.add(file);
        CompletableFuture.runAsync(() -> {
            NativeImage image;
            try (InputStream in = PvPanorama.class.getResourceAsStream("/assets/teslamaps/panoramas/" + file + ".jpg")) {
                if (in == null) return;
                image = com.teslamaps.features.capes.CapeImport.decode(in.readAllBytes());
            } catch (Throwable e) {
                TeslaMaps.LOGGER.warn("PV panorama {} failed: {}", file, e.toString());
                return;
            }
            NativeImage ready = image;
            Minecraft.getInstance().execute(() -> {
                try {
                    Identifier out = Identifier.fromNamespaceAndPath("teslamaps", "pv_panorama/" + file);
                    Minecraft.getInstance().getTextureManager()
                            .register(out, new DynamicTexture(() -> "TeslaMaps pv panorama", ready));
                    LOADED.put(file, out);
                } catch (Throwable e) {
                    ready.close();
                }
            });
        });
        return null;
    }
}
