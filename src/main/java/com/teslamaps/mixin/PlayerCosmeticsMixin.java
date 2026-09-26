package com.teslamaps.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.features.PlayerCosmetics;
import com.teslamaps.features.PlayerModels;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class PlayerCosmeticsMixin {

    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD"))
    private void teslamaps$playerSize(AvatarRenderState state, PoseStack pose, CallbackInfo ci) {
        PlayerCosmetics.scale(state.id, pose);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void teslamaps$playerModel(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        PlayerModels.extract(avatar, state, partialTick);
        com.teslamaps.features.rift.RiftTimer.nametag(state);
    }
}
