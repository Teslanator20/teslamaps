package com.teslamaps.mixin;

import com.teslamaps.render.EntityAlphaHolder;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// see-through players drop the cape, its layer has no translucent variant
@Mixin(AvatarRenderer.class)
public class GhostAvatarMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void teslamaps$ghostCape(Avatar entity, AvatarRenderState state, float pt, CallbackInfo ci) {
        if (((EntityAlphaHolder) state).teslamaps$alpha() < 255) state.showCape = false;
    }
}
