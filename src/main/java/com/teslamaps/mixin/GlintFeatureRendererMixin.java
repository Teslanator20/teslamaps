package com.teslamaps.mixin;

import com.teslamaps.features.customize.GlintColors;
import com.teslamaps.injected.GlintColorHolder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemFeatureRenderer.class)
public class GlintFeatureRendererMixin {

    @Unique private SubmitNodeCollection teslamaps$collection;

    @Inject(method = "renderSolid", at = @At("HEAD"))
    private void teslamaps$solid(SubmitNodeCollection c, MultiBufferSource.BufferSource b, OutlineBufferSource o, CallbackInfo ci) {
        teslamaps$collection = c;
    }

    @Inject(method = "renderTranslucent", at = @At("HEAD"))
    private void teslamaps$translucent(SubmitNodeCollection c, MultiBufferSource.BufferSource b, OutlineBufferSource o, CallbackInfo ci) {
        teslamaps$collection = c;
    }

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void teslamaps$glintStart(MultiBufferSource.BufferSource b, OutlineBufferSource o, SubmitNodeStorage.ItemSubmit submit, CallbackInfo ci) {
        GlintColors.rendering = teslamaps$collection == null ? 0
                : ((GlintColorHolder) teslamaps$collection).teslamaps$glintColor(submit);
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void teslamaps$glintEnd(MultiBufferSource.BufferSource b, OutlineBufferSource o, SubmitNodeStorage.ItemSubmit submit, CallbackInfo ci) {
        GlintColors.rendering = 0;
    }

    @Inject(method = "getFoilRenderType", at = @At("RETURN"), cancellable = true)
    private static void teslamaps$glintColor(RenderType base, boolean sheeted, CallbackInfoReturnable<RenderType> cir) {
        int c = GlintColors.rendering;
        if (c != 0) cir.setReturnValue(GlintColors.colored(cir.getReturnValue(), c));
    }
}
