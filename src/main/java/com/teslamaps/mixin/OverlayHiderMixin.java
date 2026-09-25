package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class OverlayHiderMixin {
    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void teslamaps$noPortal(GuiGraphicsExtractor g, float alpha, CallbackInfo ci) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.moreOverlayHiders && c.hidePortalOverlay) ci.cancel();
    }

    @Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void teslamaps$noPumpkin(GuiGraphicsExtractor g, Identifier tex, float alpha, CallbackInfo ci) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.moreOverlayHiders && c.hidePumpkinOverlay && tex.getPath().contains("pumpkinblur")) ci.cancel();
    }
}
