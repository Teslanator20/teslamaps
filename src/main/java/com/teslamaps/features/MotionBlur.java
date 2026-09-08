package com.teslamaps.features;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.TeslaRenderPipelines;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalInt;

/**
 * Frame accumulation blur. Runs once the world is on the render target but before
 * anything else is drawn, so it only ever touches the world:
 *
 *   world[n] = (1 - s) * world[n] + s * world[n-1]
 *
 * an exponential trail, the same thing the old 1.8 motion blur mods did with an
 * accumulation FBO. The hud, chat and any open screen are drawn afterwards and
 * stay sharp.
 */
public class MotionBlur {

    private static GpuTexture accum;
    private static GpuTextureView accumView;
    private static int width;
    private static int height;
    private static boolean primed;     // accum holds a usable frame
    private static boolean broken;     // gpu refused something, stay off until restart

    private static boolean active() {
        TeslaMapsConfig cfg = TeslaMapsConfig.get();
        if (broken || !cfg.section("Render") || !cfg.motionBlur) return false;
        if (cfg.motionBlurStrength < 0.001f) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return false;
        return !(cfg.motionBlurWorldOnly && mc.screen != null);
    }

    /** Blends the previous world frame in, then keeps the result for the next one. */
    public static void apply() {
        if (!active()) {
            primed = false;
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (!ensure(main.width, main.height)) return;

        try {
            if (primed) blend(main);
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                    main.getColorTexture(), accum, 0, 0, 0, 0, 0, width, height);
            primed = true;
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Motion blur disabled", e);
            broken = true;
            release();
        }
    }

    private static void blend(RenderTarget main) {
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(
                new Matrix4f(), new Vector4f(1f, 1f, 1f, retention()), new Vector3f(), new Matrix4f());

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "teslamaps motion blur", main.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(TeslaRenderPipelines.MOTION_BLUR);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("InSampler", accumView,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(0, 3);
        }
    }

    /**
     * The configured strength is what a frame keeps at 60 fps. Raising it with the
     * framerate keeps the trail the same length in seconds — otherwise the same
     * setting smears half the screen at 30 fps and does nothing at 240.
     */
    private static float retention() {
        float strength = Math.min(0.95f, Math.max(0f, TeslaMapsConfig.get().motionBlurStrength));
        long frameNs = Minecraft.getInstance().getFrameTimeNs();
        if (frameNs <= 0L) return strength;
        float frames60 = Math.min(4f, Math.max(0.25f, frameNs / (1_000_000_000f / 60f)));
        return (float) Math.pow(strength, frames60);
    }

    private static boolean ensure(int w, int h) {
        if (w <= 0 || h <= 0) return false;
        if (accum != null && width == w && height == h && !accum.isClosed()) return true;

        release();
        try {
            accum = RenderSystem.getDevice().createTexture("teslamaps motion blur",
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8, w, h, 1, 1);
            accumView = RenderSystem.getDevice().createTextureView(accum);
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Motion blur disabled, texture allocation failed", e);
            broken = true;
            release();
            return false;
        }
        width = w;
        height = h;
        primed = false;
        return true;
    }

    private static void release() {
        if (accumView != null) accumView.close();
        if (accum != null) accum.close();
        accumView = null;
        accum = null;
        width = 0;
        height = 0;
        primed = false;
    }
}
