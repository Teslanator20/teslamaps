package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.scanner.SecretTracker;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for HUD rendering and action bar message interception.
 */
@Mixin(Gui.class)
public class InGameHudMixin {
    /**
     * Intercept action bar (overlay) messages to track secrets.
     */
    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void onSetOverlayMessage(Component message, boolean tinted, CallbackInfo ci) {
        SecretTracker.onActionBarMessage(message);
    }

    /**
     * Hide status effect icons on HUD (top right corner).
     */
    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void hideStatusEffects(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (TeslaMapsConfig.get().noEffects) {
            ci.cancel();
        }
    }
}
