package com.teslamaps.adm.misc.mixin;

import com.teslamaps.adm.misc.RandomStuff;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// a frame may blur once: with Blur In GUI ours is already placed
@Mixin(Screen.class)
public class AdmScreenBlurMixin {
    @Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void teslamaps$admSkipVanillaBlur(GuiGraphicsExtractor extractor, CallbackInfo ci) {
        if (RandomStuff.blursGui()) ci.cancel();
    }
}
