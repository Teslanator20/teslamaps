package com.teslamaps.mixin;

import com.teslamaps.features.ContainerTweaks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ContainerDimMixin {

    @Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
    private void teslamaps$noDim(GuiGraphicsExtractor g, CallbackInfo ci) {
        if ((Object) this instanceof AbstractContainerScreen<?> && ContainerTweaks.noDim()) ci.cancel();
    }
}
