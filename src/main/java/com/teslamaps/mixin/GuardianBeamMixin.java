package com.teslamaps.mixin;

import net.minecraft.client.renderer.entity.GuardianRenderer;
import net.minecraft.client.renderer.entity.state.GuardianRenderState;
import net.minecraft.world.entity.monster.Guardian;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuardianRenderer.class)
public class GuardianBeamMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/monster/Guardian;Lnet/minecraft/client/renderer/entity/state/GuardianRenderState;F)V",
            at = @At("TAIL"))
    private void teslamaps$hideLaser(Guardian guardian, GuardianRenderState state, float partialTick, CallbackInfo ci) {
        if (state.attackTargetPosition != null && com.teslamaps.slayer.SeraphHelper.hideLaser(guardian)) {
            state.attackTargetPosition = null;
        }
    }
}
