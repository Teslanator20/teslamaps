package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class OwnNameTagMixin {

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void teslamaps$ownNameTag(LivingEntity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (com.teslamaps.features.NoammTweaks.hideDinnerbone(entity)) { cir.setReturnValue(false); return; }
        if (!TeslaMapsConfig.get().ownNameTag) return;
        Minecraft mc = Minecraft.getInstance();
        // only force-show for your own player, and only in third person (not visible in first person anyway)
        if (entity == mc.player && !mc.options.getCameraType().isFirstPerson()) {
            cir.setReturnValue(true);
        }
    }
}
