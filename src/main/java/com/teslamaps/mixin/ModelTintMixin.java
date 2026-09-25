package com.teslamaps.mixin;

import com.teslamaps.render.ModelTintHolder;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class ModelTintMixin {
    @Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
    private void teslamaps$modelTint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        if (!(state instanceof ModelTintHolder h)) return;
        int tint = h.teslamaps$tint();
        if (tint == 0) return;
        int base = cir.getReturnValueI();
        cir.setReturnValue((base & 0xFF000000) | (tint & 0xFFFFFF));
    }
}
