package com.teslamaps.adm.misc.mixin;

import com.teslamaps.adm.misc.RandomStuff;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Blur In GUI: the window the frame's GUI is built in
@Mixin(Gui.class)
public class AdmBlurWindowMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V", at = @At("HEAD"))
    private void teslamaps$admOpenBlurWindow(DeltaTracker deltaTracker, boolean renderLevel, boolean renderGui, CallbackInfo ci) {
        RandomStuff.buildingGui = true;
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V", at = @At("RETURN"))
    private void teslamaps$admCloseBlurWindow(DeltaTracker deltaTracker, boolean renderLevel, boolean renderGui, CallbackInfo ci) {
        RandomStuff.buildingGui = false;
    }
}
