package com.teslamaps.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class QolGuiMixin {

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void teslamaps$qolScreenSet(Screen screen, CallbackInfo ci) {
        com.teslamaps.features.HarpHelper.onScreenSet(screen);
    }
}
