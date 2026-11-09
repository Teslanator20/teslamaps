package com.teslamaps.adm.misc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.adm.misc.RandomStuff;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Blur In GUI: the blur radius for frames built in AdmBlurWindowMixin's window
@Mixin(GameRenderer.class)
public class AdmBlurMixin {
    @WrapOperation(method = "extractOptions()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getMenuBackgroundBlurriness()I"))
    private int teslamaps$admBlurRadius(Options options, Operation<Integer> original) {
        if (RandomStuff.blursGui()) return RandomStuff.blurRadius();
        return original.call(options);
    }
}
