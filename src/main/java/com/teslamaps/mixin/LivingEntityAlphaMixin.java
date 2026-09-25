package com.teslamaps.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.teslamaps.features.CommonSeaCreatureHider;
import com.teslamaps.render.EntityAlphaHolder;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityAlphaMixin {

    @Shadow
    public abstract Identifier getTextureLocation(LivingEntityRenderState state);

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void teslamaps$alpha(LivingEntity entity, LivingEntityRenderState state, float pt, CallbackInfo ci) {
        ((EntityAlphaHolder) state).teslamaps$setAlpha(CommonSeaCreatureHider.alpha(entity));
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void teslamaps$translucent(LivingEntityRenderState state, boolean showBody, boolean translucent, boolean outline,
                                       CallbackInfoReturnable<RenderType> cir) {
        if (showBody && ((EntityAlphaHolder) state).teslamaps$alpha() < 255)
            cir.setReturnValue(RenderTypes.entityTranslucentCullItemTarget(getTextureLocation(state)));
    }

    @ModifyArg(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"),
            index = 6)
    private int teslamaps$alphaColor(int argb, @Local(argsOnly = true) LivingEntityRenderState state) {
        int a = ((EntityAlphaHolder) state).teslamaps$alpha();
        if (a >= 255) return argb;
        return (argb & 0xFFFFFF) | (Math.min((argb >>> 24) & 0xFF, a) << 24);
    }
}
