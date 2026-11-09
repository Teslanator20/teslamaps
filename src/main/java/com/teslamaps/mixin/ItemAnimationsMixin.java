package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.teslamaps.features.ItemAnimations;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class ItemAnimationsMixin {

    @WrapOperation(method = "submitHandsWithItems",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;swingAnimation:F"))
    private float teslamaps$noSwing(AvatarRenderState state, Operation<Float> original) {
        return ItemAnimations.noSwing() ? 0f : original.call(state);
    }

    @Inject(method = "submitHandsWithItems", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            ordinal = 0))
    private void teslamaps$transform(float pt, PoseStack pose, SubmitNodeCollector collector, PlayerRenderState player,
                                     FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
        ItemAnimations.applyTransform(pose, state.mainHandItem);
    }

    @WrapWithCondition(method = "submitHandsWithItems",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V"))
    private boolean teslamaps$noSway(PoseStack pose, Axis axis, float degrees) {
        return !ItemAnimations.noSway();
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void teslamaps$scaleItem(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float pt, float attack,
                                     InteractionHand hand, float swing, ItemStack stack, float height, PoseStack pose,
                                     SubmitNodeCollector collector, int light, CallbackInfo ci) {
        ItemAnimations.applyScale(pose, false);
    }

    @Inject(method = "renderPlayerArm", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;renderPlayerHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;)V"))
    private void teslamaps$scaleArm(PoseStack pose, SubmitNodeCollector collector, int light, float a, float b, HumanoidArm arm,
                                    PlayerRenderState player, CallbackInfo ci) {
        ItemAnimations.applyScale(pose, true);
    }

    @WrapOperation(method = "swingArm",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void teslamaps$inPlaceSwing(PoseStack pose, float x, float y, float z, Operation<Void> original) {
        if (!ItemAnimations.inPlaceSwing()) original.call(pose, x, y, z);
    }
}
