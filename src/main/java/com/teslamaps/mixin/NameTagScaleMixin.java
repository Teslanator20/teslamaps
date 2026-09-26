package com.teslamaps.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.features.FishingHookDisplay;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class NameTagScaleMixin {
    private static final String NAME_DISPLAY = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V";

    @Inject(method = NAME_DISPLAY, at = @At("HEAD"))
    private void teslamaps$scaleStart(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                      CameraRenderState camera, int offset, CallbackInfo ci) {
        float s = FishingHookDisplay.labelScale(state);
        if (s == 1f) return;
        Vec3 at = state.nameTagAttachment;
        pose.pushPose();
        if (at != null) pose.translate(at.x, at.y, at.z);
        pose.scale(s, s, s);
        if (at != null) pose.translate(-at.x, -at.y, -at.z);
    }

    @Inject(method = NAME_DISPLAY, at = @At("RETURN"))
    private void teslamaps$scaleEnd(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                    CameraRenderState camera, int offset, CallbackInfo ci) {
        if (FishingHookDisplay.labelScale(state) != 1f) pose.popPose();
    }
}
