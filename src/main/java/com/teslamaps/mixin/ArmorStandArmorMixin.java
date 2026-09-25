package com.teslamaps.mixin;

import com.teslamaps.features.RenderHiders;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorStandRenderer.class)
public class ArmorStandArmorMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ArmorStand;Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;F)V",
            at = @At("TAIL"))
    private void teslamaps$hideArmor(ArmorStand stand, ArmorStandRenderState state, float partial, CallbackInfo ci) {
        RenderHiders.stripArmor(state);
    }
}
