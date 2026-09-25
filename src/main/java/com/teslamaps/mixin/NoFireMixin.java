package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class NoFireMixin {
    @Inject(method = "isOnFire", at = @At("HEAD"), cancellable = true)
    private void onIsOnFire(CallbackInfoReturnable<Boolean> cir) {
        if (!TeslaMapsConfig.get().noFire) return;

        Entity self = (Entity) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || self != mc.player) return;

        cir.setReturnValue(false);
    }

    @Inject(method = "displayFireAnimation", at = @At("HEAD"), cancellable = true)
    private void teslamaps$hideEntityFire(CallbackInfoReturnable<Boolean> cir) {
        if (!TeslaMapsConfig.get().hideEntityFire) return;
        if ((Object) this != Minecraft.getInstance().player) cir.setReturnValue(false);
    }
}
