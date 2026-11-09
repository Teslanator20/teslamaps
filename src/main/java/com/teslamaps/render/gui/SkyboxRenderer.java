package com.teslamaps.render.gui;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * In-world sky replacement. Unlike the menu background this genuinely wants to
 * be a post chain: the level frame graph hands us the targets and takes care of
 * ordering, which a gui element could not do.
 */
public class SkyboxRenderer {

    private static final String UNIFORM = "SkyboxConfig";
    private static final int UNIFORM_SIZE = 48;

    private static final ShaderClock CLOCK = new ShaderClock();

    private static final Map<PostPass, GpuBuffer> BUFFERS = new WeakHashMap<>();

    // Look-direction anchoring. Without this the pattern is pure screen space, so
    // it sits glued to the viewport and slides around as you turn.
    private static final float LOOK_SMOOTHING = 5.0f;
    private static float smoothedYaw;
    private static float smoothedPitch;
    private static float anchorYaw;
    private static float anchorPitch;
    private static boolean lookInitialized;

    public static void resetLook() {
        lookInitialized = false;
    }

    public static Identifier chainId() {
        String path = MenuBackgroundRenderer.chainFor(TeslaMapsConfig.get().skyboxPreset);
        return Identifier.fromNamespaceAndPath("teslamaps", path);
    }

    /**
     * Shader uv offset from the player's look direction, so the sky stays put in
     * the world while you turn. Yaw deltas take the shortest path so crossing
     * 359°/0° does not snap.
     */
    private static float[] lookOffset() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            lookInitialized = false;
            return new float[]{0.5f, 0.5f};
        }

        float yaw = player.getYRot();
        float pitch = player.getXRot();

        if (!lookInitialized) {
            smoothedYaw = yaw;
            smoothedPitch = pitch;
            anchorYaw = yaw;
            anchorPitch = pitch;
            lookInitialized = true;
        } else {
            float deltaYaw = yaw - smoothedYaw;
            deltaYaw -= 360.0f * Math.round(deltaYaw / 360.0f);
            smoothedYaw += deltaYaw / LOOK_SMOOTHING;
            smoothedPitch += (pitch - smoothedPitch) / LOOK_SMOOTHING;
        }

        return new float[]{
                1.0f + (smoothedYaw - anchorYaw) / 180.0f,
                1.0f - (smoothedPitch - anchorPitch) / 180.0f
        };
    }

    public static void updateUniforms(PostChain chain, int pixelWidth, int pixelHeight) {
        List<PostPass> passes = chain.passes;
        if (passes == null) return;

        int tint = TeslaMapsConfig.parseColor(TeslaMapsConfig.get().skyboxColor);
        float r = ((tint >> 16) & 0xFF) / 255f;
        float g = ((tint >> 8) & 0xFF) / 255f;
        float b = (tint & 0xFF) / 255f;
        float a = ((tint >>> 24) & 0xFF) / 255f;
        float time = CLOCK.advance(TeslaMapsConfig.get().skyboxSpeed);
        float scale = Math.max(TeslaMapsConfig.get().skyboxScale, 0.1f);

        float[] look = lookOffset();

        for (PostPass pass : passes) {
            Map<String, GpuBuffer> uniforms = pass.customUniforms;
            if (uniforms == null || !uniforms.containsKey(UNIFORM)) continue;

            try (MemoryStack stack = MemoryStack.stackPush()) {
                Std140Builder builder = Std140Builder.onStack(stack, UNIFORM_SIZE);
                builder.putVec4(r, g, b, a)
                        .putVec2(pixelWidth, pixelHeight)
                        .putFloat(time)
                        .putFloat(scale)
                        .putVec2(look[0], look[1]);
                ByteBuffer data = builder.get();

                GpuBuffer buffer = BUFFERS.get(pass);
                if (buffer == null || buffer.isClosed()) {
                    buffer = RenderSystem.getDevice().createBuffer(
                            () -> "TeslaMaps skybox uniform",
                            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                            UNIFORM_SIZE);
                    BUFFERS.put(pass, buffer);
                }
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
                uniforms.put(UNIFORM, buffer);
            }
        }
    }
}
