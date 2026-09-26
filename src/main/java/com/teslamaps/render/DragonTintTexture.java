package com.teslamaps.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Writes the dragon tints into the overlay texture only once a feature needs them. */
public final class DragonTintTexture {
    private static DynamicTexture texture;
    private static boolean written;

    private DragonTintTexture() {}

    public static void init(DynamicTexture tex) {
        texture = tex;
    }

    public static void ensure() {
        if (written || texture == null) return;
        NativeImage img = texture.getPixels();
        if (img == null) return;
        int[] tints = DragonTintHolder.TINTS;
        for (int i = 0; i < tints.length; i++) img.setPixel(DragonTintHolder.TINT_U + i, DragonTintHolder.TINT_V, tints[i]);
        texture.upload();
        written = true;
    }
}
