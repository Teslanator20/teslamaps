package com.teslamaps.adm.practice.mixin;

import com.teslamaps.adm.practice.BrBoxes;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class WandDropMixin {
    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void teslamaps$wandDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        if (BrBoxes.onDrop()) cir.setReturnValue(false);
    }
}
