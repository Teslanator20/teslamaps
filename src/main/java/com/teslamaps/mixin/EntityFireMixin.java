package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Ported from SkyOcean's HideFireMixin
@Mixin(EntityRenderDispatcher.class)
public class EntityFireMixin {

    @WrapOperation(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitFlame(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lorg/joml/Quaternionf;)V"))
    private void teslamaps$hideEntityFire(SubmitNodeCollector collector, PoseStack pose, EntityRenderState state, Quaternionf rot,
                                          Operation<Void> original) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.noFire && c.hideEntityFire) return;
        original.call(collector, pose, state, rot);
    }
}
