package com.teslamaps.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.teslamaps.features.DragonRecolor;
import com.teslamaps.render.DragonTintHolder;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderDragonRenderer.class)
public class EnderDragonRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;Lnet/minecraft/client/renderer/entity/state/EnderDragonRenderState;F)V",
            at = @At("TAIL"))
    private void teslamaps$tint(EnderDragon dragon, EnderDragonRenderState state, float pt, CallbackInfo ci) {
        ((DragonTintHolder) state).teslamaps$setOverlay(DragonRecolor.overlayFor(dragon));
    }

    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/EnderDragonRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/OverlayTexture;pack(FZ)I"))
    private int teslamaps$overlay(float white, boolean hurt, Operation<Integer> original,
                                  EnderDragonRenderState state, com.mojang.blaze3d.vertex.PoseStack pose,
                                  net.minecraft.client.renderer.SubmitNodeCollector collector,
                                  net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        int o = ((DragonTintHolder) state).teslamaps$overlay();
        return o != -1 ? o : original.call(white, hurt);
    }
}
