package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class NoVignetteMixin {
    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void teslamaps$noVignette(CallbackInfo ci) {
        if (TeslaMapsConfig.get().noVignette) ci.cancel();
    }
}
