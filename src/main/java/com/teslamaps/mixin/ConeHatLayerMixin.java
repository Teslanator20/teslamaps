package com.teslamaps.mixin;

import com.teslamaps.features.cosmetic.ConeHatLayer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class ConeHatLayerMixin {

    @Shadow
    protected abstract boolean addLayer(RenderLayer layer);

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "<init>", at = @At("RETURN"))
    private void teslamaps$addConeHat(EntityRendererProvider.Context ctx, EntityModel model, float shadow, CallbackInfo ci) {
        if ((Object) this instanceof AvatarRenderer<?> renderer) addLayer(new ConeHatLayer((AvatarRenderer) renderer));
    }
}
