package com.teslamaps.adm.misc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.adm.misc.RandomStuff;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Blur In GUI: the window the frame's GUI is built in, and the blur radius for those frames
@Mixin(GameRenderer.class)
public class AdmBlurMixin {
    @Inject(method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V", at = @At("HEAD"))
    private void teslamaps$admOpenBlurWindow(DeltaTracker deltaTracker, boolean renderLevel, boolean renderGui, CallbackInfo ci) {
        RandomStuff.buildingGui = true;
    }

    @Inject(method = "extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V", at = @At("RETURN"))
    private void teslamaps$admCloseBlurWindow(DeltaTracker deltaTracker, boolean renderLevel, boolean renderGui, CallbackInfo ci) {
        RandomStuff.buildingGui = false;
    }

    @WrapOperation(method = "extractOptions()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getMenuBackgroundBlurriness()I"))
    private int teslamaps$admBlurRadius(Options options, Operation<Integer> original) {
        if (RandomStuff.blursGui()) return RandomStuff.blurRadius();
        return original.call(options);
    }
}
