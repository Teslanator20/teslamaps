package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.features.customize.GlintColors;
import com.teslamaps.injected.GlintColorHolder;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.class)
public class GlintFeatureRendererMixin {

    @Inject(method = "prepareMainSubmit", at = @At("HEAD"))
    private void teslamaps$glintStart(ItemFeatureRenderer.Submit submit, CallbackInfo ci) {
        GlintColors.rendering = ((GlintColorHolder) (Object) submit).teslamaps$glintColor();
    }

    @Inject(method = "prepareMainSubmit", at = @At("RETURN"))
    private void teslamaps$glintEnd(ItemFeatureRenderer.Submit submit, CallbackInfo ci) {
        GlintColors.rendering = 0;
    }

    @WrapOperation(method = "prepareMainSubmit", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/geometry/BakedQuad$MaterialInfo;itemGlintRenderType()Lnet/minecraft/client/renderer/rendertype/RenderType;"),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/geometry/BakedQuad$MaterialInfo;itemGlintSpecialRenderType()Lnet/minecraft/client/renderer/rendertype/RenderType;")
    })
    private RenderType teslamaps$glintColor(BakedQuad.MaterialInfo info, Operation<RenderType> original) {
        RenderType type = original.call(info);
        int c = GlintColors.rendering;
        return c == 0 ? type : GlintColors.colored(info, type, c);
    }
}
