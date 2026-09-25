package com.teslamaps.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.features.TransparentArmor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class TransparentArmorLayerMixin {
    private static final String SUBMIT = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V";

    @Inject(method = SUBMIT, at = @At("HEAD"), cancellable = true)
    private void teslamaps$armorAlpha(PoseStack pose, SubmitNodeCollector collector, int light, HumanoidRenderState state,
                                      float yRot, float xRot, CallbackInfo ci) {
        int a = TransparentArmor.alphaFor(state);
        if (a <= 0) {
            ci.cancel();
            return;
        }
        TransparentArmor.armor = a;
    }

    @Inject(method = SUBMIT, at = @At("RETURN"))
    private void teslamaps$armorAlphaReset(PoseStack pose, SubmitNodeCollector collector, int light, HumanoidRenderState state,
                                           float yRot, float xRot, CallbackInfo ci) {
        TransparentArmor.armor = 255;
    }
}
