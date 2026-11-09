package com.teslamaps.mixin;

import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.world.entity.monster.Enderman;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndermanRenderer.class)
public class EndermanTintMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/monster/Enderman;Lnet/minecraft/client/renderer/entity/state/EndermanRenderState;F)V",
            at = @At("TAIL"))
    private void teslamaps$zealotTint(Enderman enderman, EndermanRenderState state, float pt, CallbackInfo ci) {
        if (com.teslamaps.features.theend.EndermanQol.hideHurtTint()) state.hasRedOverlay = false;
    }
}
