package com.teslamaps.adm.sim.mixin;

import com.teslamaps.adm.sim.SimItems;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class ArcherDropSimMixin {
    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void teslamaps$adsClassAbility(LocalPlayer player, boolean fullStack, CallbackInfo ci) {
        if (SimItems.clientDrop(fullStack)) ci.cancel();
    }
}
