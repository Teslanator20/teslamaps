package com.teslamaps.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class SwapSoundMixin {
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void teslamaps$swapSound(CallbackInfoReturnable<Boolean> cir) {
        com.teslamaps.features.SwapSound.onLeftClick();
    }
}
