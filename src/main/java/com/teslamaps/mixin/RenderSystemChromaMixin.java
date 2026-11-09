package com.teslamaps.mixin;

import com.teslamaps.render.gui.ChromaHook;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class RenderSystemChromaMixin {

    @Inject(method = "renderFrame", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DynamicGpuData;reset()V"))
    private void teslamaps$clearChroma(CallbackInfo ci) {
        ChromaHook.clearUniforms();
    }
}
