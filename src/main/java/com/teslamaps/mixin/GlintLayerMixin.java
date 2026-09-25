package com.teslamaps.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.features.customize.GlintColors;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public class GlintLayerMixin {

    @Unique private int teslamaps$glint;

    @Inject(method = "setFoilType", at = @At("HEAD"))
    private void teslamaps$foil(ItemStackRenderState.FoilType type, CallbackInfo ci) {
        teslamaps$glint = GlintColors.extracting;
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void teslamaps$clear(CallbackInfo ci) {
        teslamaps$glint = 0;
    }

    @Inject(method = "submit", at = @At("HEAD"))
    private void teslamaps$submitStart(PoseStack pose, SubmitNodeCollector c, int light, int overlay, int outline, CallbackInfo ci) {
        GlintColors.submitting = teslamaps$glint;
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void teslamaps$submitEnd(PoseStack pose, SubmitNodeCollector c, int light, int overlay, int outline, CallbackInfo ci) {
        GlintColors.submitting = 0;
    }
}
