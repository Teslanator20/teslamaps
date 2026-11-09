package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.teslamaps.render.gui.ChromaHook;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererChromaMixin {

    @Inject(method = "executeDrawRange", at = @At("HEAD"))
    private void teslamaps$computeChroma(CallbackInfo ci) {
        ChromaHook.computeBufferSlice();
    }

    @Inject(method = "executeDrawRange", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderPass;setUniform(Ljava/lang/String;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
            ordinal = 1))
    private void teslamaps$bindChroma(CallbackInfo ci, @Local RenderPass renderPass) {
        ChromaHook.insertUniform(renderPass);
    }

    @WrapOperation(method = "addElementToMesh", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/state/gui/GuiElementRenderState;pipeline()Lcom/mojang/blaze3d/pipeline/RenderPipeline;"))
    private RenderPipeline teslamaps$chromaPipeline(GuiElementRenderState state, Operation<RenderPipeline> original) {
        return ChromaHook.replacePipeline(state, original.call(state));
    }
}
