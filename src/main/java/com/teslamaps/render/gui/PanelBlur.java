package com.teslamaps.render.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;

/**
 * Frosted backdrop for HUD Style "Custom". The world frame is copied once per frame right
 * after the level is drawn (hud not on it yet), and the panel quad samples that copy.
 */
public final class PanelBlur {

    private static GpuTexture tex;
    private static GpuTextureView view;
    private static GpuTexture oldTex;
    private static GpuTextureView oldView;
    private static int width, height;
    private static long capturedAt;
    private static boolean broken;

    private PanelBlur() {}

    private static boolean wanted() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (broken || c.hudCustomBlur < 0.5f) return false;
        if (HudStyle.CUSTOM.equals(c.hudStyle)) return true;
        return c.hudPanelStyles != null && c.hudPanelStyles.containsValue(HudStyle.CUSTOM);
    }

    /** After renderLevel: keep this frame's world for the panels. */
    public static void capture() {
        if (oldView != null) { oldView.close(); oldView = null; }
        if (oldTex != null) { oldTex.close(); oldTex = null; }
        if (!wanted()) return;
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (main.width <= 0 || main.height <= 0) return;
        try {
            if (tex == null || tex.isClosed() || width != main.width || height != main.height) {
                oldTex = tex;
                oldView = view;
                tex = RenderSystem.getDevice().createTexture("teslamaps panel blur",
                        GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                        GpuFormat.RGBA8_UNORM, main.width, main.height, 1, 1);
                view = RenderSystem.getDevice().createTextureView(tex);
                width = main.width;
                height = main.height;
            }
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                    main.getColorTexture(), tex, 0, 0, 0, 0, 0, width, height);
            capturedAt = System.nanoTime();
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Panel blur disabled", e);
            broken = true;
            view = null;
            tex = null;
        }
    }

    /** Blurred world behind a rounded rect, drawn before the panel fill. */
    public static void draw(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float radius) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (broken || c.hudCustomBlur < 0.5f || view == null || w * h < 40) return;
        if (System.nanoTime() - capturedAt > 250_000_000L) return;
        float px = (float) (Math.min(20f, c.hudCustomBlur) * Minecraft.getInstance().getWindow().getGuiScale());
        if ("Box".equals(c.hudCustomBlurKernel)) px = -px;
        GpuTextureView v = view;
        ctx.guiRenderState.addGuiElement(new SdfRectRenderState(TeslaGuiPipelines.PANEL_BLUR, ctx.pose(),
                x, y, w, h, radius, px, 1f, 0xFFFFFFFF, 0xFFFFFFFF, null) {
            @Override
            public TextureSetup textureSetup() {
                return TextureSetup.singleTexture(v, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            }
        });
    }
}
