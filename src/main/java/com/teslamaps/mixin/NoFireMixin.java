package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for Entity to handle no fire overlay option.
 * Intercepts isOnFire() to return false for the local player when noFire is enabled.
 */
@Mixin(Entity.class)
public class NoFireMixin {
    @Inject(method = "isOnFire", at = @At("HEAD"), cancellable = true)
    private void onIsOnFire(CallbackInfoReturnable<Boolean> cir) {
        if (!TeslaMapsConfig.get().noFire) return;

        // Only apply to the local player
        Entity self = (Entity) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || self != mc.player) return;

        // Return false to hide fire overlay
        cir.setReturnValue(false);
    }
}
