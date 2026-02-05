package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.StatusEffectsDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hide status effect icons in inventory screens.
 */
@Mixin(StatusEffectsDisplay.class)
public class InventoryEffectsMixin {

    @Inject(method = "drawStatusEffects", at = @At("HEAD"), cancellable = true)
    private void hideStatusEffects(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (TeslaMapsConfig.get().hideInventoryEffects) {
            ci.cancel();
        }
    }
}
