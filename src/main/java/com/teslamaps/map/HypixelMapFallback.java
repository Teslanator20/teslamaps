package com.teslamaps.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.config.TeslaMapsConfig;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.MapColor;

/** Skyblocker DungeonMapTexture: Hypixel's map pixels as a texture, re-copied only when they change. */
public final class HypixelMapFallback {
    private static final int SIZE = 128;
    private static final Identifier ID = Identifier.fromNamespaceAndPath("teslamaps", "hypixel_map");
    private static DynamicTexture tex;
    private static final byte[] last = new byte[SIZE * SIZE];
    private static boolean has, dirty;

    private HypixelMapFallback() {}

    public static void update(byte[] colors) {
        if (!TeslaMapsConfig.get().mapHypixelFallback || colors == null || colors.length < SIZE * SIZE) return;
        if (has && Arrays.equals(colors, 0, SIZE * SIZE, last, 0, SIZE * SIZE)) return;
        if (tex == null) {
            tex = new DynamicTexture(() -> "TeslaMaps Hypixel Map", SIZE, SIZE, true);
            Minecraft.getInstance().getTextureManager().register(ID, tex);
        }
        NativeImage px = tex.getPixels();
        if (px == null) return;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                px.setPixel(x, y, MapColor.getColorFromPackedId(colors[x + y * SIZE] & 0xFF));
            }
        }
        System.arraycopy(colors, 0, last, 0, SIZE * SIZE);
        has = true;
        dirty = true;
    }

    public static void reset() {
        has = false;
    }

    public static boolean ready() {
        return has && tex != null;
    }

    public static Identifier texture() {
        if (dirty) {
            tex.upload();
            dirty = false;
        }
        return ID;
    }
}
