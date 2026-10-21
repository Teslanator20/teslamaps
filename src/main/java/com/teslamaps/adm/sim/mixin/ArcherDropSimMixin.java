package com.teslamaps.adm.sim.mixin;

import com.teslamaps.adm.sim.SimItems;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class ArcherDropSimMixin {
    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void teslamaps$adsClassAbility(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        if (SimItems.clientDrop(fullStack)) cir.setReturnValue(false);
    }
}
