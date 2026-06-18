package com.teslamaps.mixin;

import com.teslamaps.scanner.SecretTracker;
import net.minecraft.client.gui.Gui;
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

    // NOTE: the "noEffects" HUD-effect hider (old Gui.renderEffects @Inject) was removed:
    // 26.1.2 no longer renders status effects in Gui. To restore it, hook the new status-effect
    // HudElement or remove it via HudElementRegistry instead.
}
