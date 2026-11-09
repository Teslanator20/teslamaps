package com.teslamaps.adm.practice.mixin;

import com.teslamaps.adm.practice.BrBoxes;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class WandDropMixin {
    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void teslamaps$wandDrop(LocalPlayer player, boolean fullStack, CallbackInfo ci) {
        if (BrBoxes.onDrop()) ci.cancel();
    }
}
