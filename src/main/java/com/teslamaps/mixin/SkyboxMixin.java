package com.teslamaps.mixin;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.gui.SkyboxRenderer;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the vanilla sky pass with one of the procedural skyboxes. */
@Mixin(LevelRenderer.class)
public abstract class SkyboxMixin {

    @Final @Shadow private Minecraft minecraft;
    @Final @Shadow private LevelTargetBundle targets;

    @Inject(method = "addSkyPass", at = @At("HEAD"), cancellable = true)
    private void teslamaps$customSkybox(FrameGraphBuilder builder, CameraRenderState camera,
                                         GpuBufferSlice fog, CallbackInfo ci) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.customSkybox) return;
        if (config.skyboxOnlySkyblock && !SkyblockUtils.isInSkyblock()) return;

        // Submerged in lava or powder snow the vanilla pass is what sells the
        // effect, so leave it alone there.
        if (camera.fogType == FogType.LAVA || camera.fogType == FogType.POWDER_SNOW) return;

        PostChain chain = minecraft.getShaderManager()
                .getPostChain(SkyboxRenderer.chainId(), LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) return;

        int width = minecraft.getMainRenderTarget().width;
        int height = minecraft.getMainRenderTarget().height;
        SkyboxRenderer.updateUniforms(chain, width, height);

        FramePass fogPass = builder.addPass("teslamaps_skybox_fog");
        targets.main = fogPass.readsAndWrites(targets.main);
        fogPass.executes(() -> RenderSystem.setShaderFog(fog));

        chain.addToFrame(builder, width, height, targets);
        ci.cancel();
    }
}
