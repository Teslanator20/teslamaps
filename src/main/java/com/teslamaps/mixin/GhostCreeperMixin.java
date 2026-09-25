package com.teslamaps.mixin;

import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreeperRenderer.class)
public class GhostCreeperMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/monster/Creeper;Lnet/minecraft/client/renderer/entity/state/CreeperRenderState;F)V",
            at = @At("TAIL"))
    private void teslamaps$ghost(Creeper creeper, CreeperRenderState state, float partialTick, CallbackInfo ci) {
        com.teslamaps.features.mining.GhostVisuals.onExtract(creeper, state);
    }
}
