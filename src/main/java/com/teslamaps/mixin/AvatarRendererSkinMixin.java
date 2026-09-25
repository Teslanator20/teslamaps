package com.teslamaps.mixin;

import com.teslamaps.features.cosmetic.CustomSkin;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public class AvatarRendererSkinMixin {

    @Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/resources/Identifier;",
            at = @At("HEAD"), cancellable = true)
    private void teslamaps$customSkin(AvatarRenderState state, CallbackInfoReturnable<Identifier> cir) {
        Identifier custom = CustomSkin.textureFor(state.id);
        if (custom != null) cir.setReturnValue(custom);
    }

    @ModifyVariable(method = "renderHand", at = @At("HEAD"), argsOnly = true)
    private Identifier teslamaps$customHandSkin(Identifier skin) {
        Identifier custom = CustomSkin.selfHandTexture();
        return custom != null ? custom : skin;
    }
}
