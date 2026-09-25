package com.teslamaps.features;

// Concept from Zen's CustomTint: rewrites the red rows of the entity overlay texture
import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.OverlayTextureAccessor;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

public final class HurtTint {

    private static final int VANILLA = 0xB2FF0000;
    private static int applied = VANILLA;

    private HurtTint() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("HurtTint")
                .tickAlways(HurtTint::tick)
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Hurt Tint");
                    b.toggle("Custom Hurt Tint", () -> c.customHurtTint, v -> c.customHurtTint = v)
                            .desc("Replaces the red flash of hit entities with your own color. Alpha = strength.");
                    b.color("  Tint Color", () -> c.customHurtTintColor, v -> c.customHurtTintColor = v);
                });
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        int want = VANILLA;
        if (c.customHurtTint && c.section("Render")) {
            int argb = TeslaMapsConfig.parseColor(c.customHurtTintColor);
            int strength = (argb >>> 24) & 0xFF;
            want = ((255 - strength) << 24) | (argb & 0xFFFFFF);
        }
        if (want == applied) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return;
        DynamicTexture tex = ((OverlayTextureAccessor) mc.gameRenderer.overlayTexture()).teslamaps$texture();
        NativeImage img = tex.getPixels();
        if (img == null) return;
        for (int v = 0; v < 8; v++) for (int u = 0; u < 16; u++) img.setPixel(u, v, want);
        tex.upload();
        applied = want;
    }
}
