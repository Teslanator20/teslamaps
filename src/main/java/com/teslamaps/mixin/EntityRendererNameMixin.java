package com.teslamaps.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererNameMixin {

    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void teslamaps$coloredName(Entity entity, CallbackInfoReturnable<Component> cir) {
        Component tag = cir.getReturnValue();
        Component out = com.teslamaps.features.DamageSplash.compact(entity, tag);
        if (out != tag) cir.setReturnValue(out);
    }
}
