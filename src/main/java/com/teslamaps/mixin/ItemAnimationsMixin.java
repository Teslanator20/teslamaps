package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.features.ItemAnimations;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemInHandRenderer.class)
public class ItemAnimationsMixin {
    @Shadow private ItemStack mainHandItem;

    @WrapOperation(method = "renderHandsWithItems",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAttackAnim(F)F"))
    private float teslamaps$noSwing(LocalPlayer player, float pt, Operation<Float> original) {
        return ItemAnimations.noSwing() ? 0f : original.call(player, pt);
    }

    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
    private void teslamaps$noReequip(ItemStack from, ItemStack to, CallbackInfoReturnable<Boolean> cir) {
        if (ItemAnimations.noReequip()) cir.setReturnValue(true);
    }

    @WrapOperation(method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"))
    private float teslamaps$noSwapDip(LocalPlayer player, float pt, Operation<Float> original) {
        return ItemAnimations.noReequip() ? 1f : original.call(player, pt);
    }

    @Inject(method = "renderHandsWithItems", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            ordinal = 0))
    private void teslamaps$transform(float pt, PoseStack pose, SubmitNodeCollector collector, LocalPlayer player, int light, CallbackInfo ci) {
        ItemAnimations.applyTransform(pose, mainHandItem);
    }

    @WrapWithCondition(method = "renderHandsWithItems",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
    private boolean teslamaps$noSway(PoseStack pose, Quaternionfc q) {
        return !ItemAnimations.noSway();
    }

    @Inject(method = "renderItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void teslamaps$scaleItem(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                                     SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (ctx.firstPerson()) ItemAnimations.applyScale(pose, false);
    }

    @Inject(method = "renderPlayerArm", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;getPlayerRenderer(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;"))
    private void teslamaps$scaleArm(PoseStack pose, SubmitNodeCollector collector, int light, float a, float b, HumanoidArm arm, CallbackInfo ci) {
        ItemAnimations.applyScale(pose, true);
    }

    @WrapOperation(method = "swingArm",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void teslamaps$inPlaceSwing(PoseStack pose, float x, float y, float z, Operation<Void> original) {
        if (!ItemAnimations.inPlaceSwing()) original.call(pose, x, y, z);
    }
}
