package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.injected.GlintColorHolder;
import com.teslamaps.render.GhostRender;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Ported from Bax's GhostItemRenderMixin
@Mixin(ItemFeatureRenderer.class)
public class GhostItemRenderMixin {

    @Unique private int teslamaps$ghostAlpha = 255;

    @Inject(method = "prepareMainSubmit", at = @At("HEAD"))
    private void teslamaps$ghostTrack(ItemFeatureRenderer.Submit submit, CallbackInfo ci) {
        teslamaps$ghostAlpha = ((GlintColorHolder) (Object) submit).teslamaps$ghostAlpha();
    }

    @WrapOperation(method = "prepareMainSubmit", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/geometry/BakedQuad$MaterialInfo;itemRenderType()Lnet/minecraft/client/renderer/rendertype/RenderType;"),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/geometry/BakedQuad$MaterialInfo;itemGlintRenderType()Lnet/minecraft/client/renderer/rendertype/RenderType;"),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/geometry/BakedQuad$MaterialInfo;itemGlintSpecialRenderType()Lnet/minecraft/client/renderer/rendertype/RenderType;")
    })
    private RenderType teslamaps$ghostRenderType(BakedQuad.MaterialInfo info, Operation<RenderType> original) {
        RenderType type = original.call(info);
        if (teslamaps$ghostAlpha >= 255) return type;
        if (type == Sheets.cutoutItemSheet()) return Sheets.translucentItemSheet();
        if (type == Sheets.cutoutBlockItemSheet()) return Sheets.translucentBlockItemSheet();
        if (type == Sheets.cutoutItemGlintSheet()) return Sheets.translucentItemGlintSheet();
        if (type == Sheets.cutoutBlockItemGlintSheet()) return Sheets.translucentBlockItemGlintSheet();
        if (type == Sheets.cutoutItemGlintSpecialSheet()) return Sheets.translucentItemGlintSpecialSheet();
        if (type == Sheets.cutoutBlockItemGlintSpecialSheet()) return Sheets.translucentBlockItemGlintSpecialSheet();
        return type;
    }

    @WrapOperation(method = "prepareMainSubmit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer;getLayerColorSafe([ILnet/minecraft/client/resources/model/geometry/BakedQuad$MaterialInfo;)I"))
    private int teslamaps$ghostColor(int[] tints, BakedQuad.MaterialInfo info, Operation<Integer> original) {
        int color = original.call(tints, info);
        int a = teslamaps$ghostAlpha;
        return a >= 255 ? color : GhostRender.withAlpha(color, a);
    }
}
