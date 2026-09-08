package com.teslamaps.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.teslamaps.render.gui.ChromaHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public abstract class RenderSystemChromaMixin {

    @Inject(method = "flipFrame", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicUniforms;reset()V"))
    private static void teslamaps$clearChroma(CallbackInfo ci) {
        ChromaHook.clearUniforms();
    }
}
