package com.teslamaps.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.features.DroppedItemScaling;
import com.teslamaps.render.ItemScaleHolder;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Ported from Skysoft's ItemEntityRendererMixin
@Mixin(ItemEntityRenderer.class)
public class ItemEntityScaleMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void teslamaps$extractScale(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
        ((ItemScaleHolder) state).teslamaps$setItemScale(DroppedItemScaling.scaleFor(entity.getItem()));
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;submitMultipleFromCount(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ItemClusterRenderState;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/phys/AABB;)V"))
    private void teslamaps$applyScale(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        float scale = ((ItemScaleHolder) state).teslamaps$itemScale();
        if (scale == 1f) return;
        pose.translate(0f, (float) state.item.getModelBoundingBox().minY * (1f - scale), 0f);
        pose.scale(scale, scale, scale);
    }
}
