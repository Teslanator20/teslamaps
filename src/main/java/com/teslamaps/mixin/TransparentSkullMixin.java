package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.features.TransparentArmor;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SkullBlockRenderer.class)
public class TransparentSkullMixin {
    @WrapOperation(method = "submitSkull", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;III)V"))
    private static <S> void teslamaps$skullAlpha(SubmitNodeCollector collector, Model<? super S> model, S state, PoseStack pose,
                                                 RenderType type, int light, int overlay, int outline, Operation<Void> original) {
        int a = TransparentArmor.head;
        if (a >= 255) {
            original.call(collector, model, state, pose, type, light, overlay, outline);
            return;
        }
        collector.submitModel(model, state, pose, type, light, overlay, (a << 24) | 0xFFFFFF, null, outline);
    }
}
