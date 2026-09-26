package com.teslamaps.mixin;

import com.teslamaps.features.LowerEndermanNametags;
import com.teslamaps.features.capes.Cosmetics;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityNametagMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
    private void teslamaps$nametagOffset(Entity entity, EntityRenderState state, float partial, CallbackInfo ci) {
        if (state.nameTagAttachment == null) return;
        LowerEndermanNametags.adjust(entity, state);
        Cosmetics.adjustNametag(entity, state);
        com.teslamaps.features.FishingHookDisplay.markState(entity, state);
    }
}
