package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.render.GhostRender;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
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

    @Unique private boolean teslamaps$ghostItem;

    @WrapOperation(method = {"renderSolid", "renderTranslucent"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer;hasTranslucency(Lnet/minecraft/client/renderer/SubmitNodeStorage$ItemSubmit;)Z"))
    private boolean teslamaps$ghostTranslucent(SubmitNodeStorage.ItemSubmit submit, Operation<Boolean> original) {
        if (GhostRender.itemAlpha(submit.tintLayers()) < 255) return true;
        return original.call(submit);
    }

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void teslamaps$ghostTrack(MultiBufferSource.BufferSource buffers, OutlineBufferSource outlines, SubmitNodeStorage.ItemSubmit submit, CallbackInfo ci) {
        teslamaps$ghostItem = GhostRender.itemAlpha(submit.tintLayers()) < 255;
    }

    @WrapOperation(method = "renderItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/resources/model/geometry/BakedQuad$MaterialInfo;itemRenderType()Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType teslamaps$ghostRenderType(BakedQuad.MaterialInfo info, Operation<RenderType> original) {
        RenderType type = original.call(info);
        if (!teslamaps$ghostItem) return type;
        if (type == Sheets.cutoutItemSheet()) return Sheets.translucentItemSheet();
        if (type == Sheets.cutoutBlockItemSheet()) return Sheets.translucentBlockItemSheet();
        return type;
    }

    @WrapOperation(method = "renderItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer;getLayerColorSafe([ILnet/minecraft/client/resources/model/geometry/BakedQuad$MaterialInfo;)I"))
    private int teslamaps$ghostColor(int[] tints, BakedQuad.MaterialInfo info, Operation<Integer> original) {
        int color = original.call(tints, info);
        int a = GhostRender.itemAlpha(tints);
        return a >= 255 ? color : GhostRender.withAlpha(color, a);
    }

    @Inject(method = "renderTranslucent", at = @At("RETURN"))
    private void teslamaps$ghostClear(SubmitNodeCollection collection, MultiBufferSource.BufferSource buffers, OutlineBufferSource outlines, CallbackInfo ci) {
        GhostRender.clearItems();
    }
}
