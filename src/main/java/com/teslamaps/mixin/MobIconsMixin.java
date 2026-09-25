package com.teslamaps.mixin;

import com.teslamaps.features.MobIcons;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MobIconsMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void teslamaps$mobIcons(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        if (state.nameTag != null) state.nameTag = MobIcons.apply(entity, state.nameTag);
    }
}
