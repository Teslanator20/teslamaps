package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.scanner.SecretTracker;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for HUD rendering and action bar message interception.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {
    /**
     * Intercept action bar (overlay) messages to track secrets.
     */
    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void onSetOverlayMessage(Text message, boolean tinted, CallbackInfo ci) {
        SecretTracker.onActionBarMessage(message);
    }

    /**
     * Hide status effect icons on HUD (top right corner).
     */
    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void hideStatusEffects(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (TeslaMapsConfig.get().noEffects) {
            ci.cancel();
        }
    }
}
