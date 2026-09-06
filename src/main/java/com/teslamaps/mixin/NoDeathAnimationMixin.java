package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class NoDeathAnimationMixin {
    @Inject(method = "getFlipDegrees", at = @At("HEAD"), cancellable = true)
    private void teslamaps$noDeathFlip(CallbackInfoReturnable<Float> cir) {
        if (TeslaMapsConfig.get().noDeathAnimation) cir.setReturnValue(0.0f);
    }
}
