package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.StatusEffectsDisplay;
import net.minecraft.entity.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(StatusEffectsDisplay.class)
public class InventoryEffectsMixin {

    @Inject(method = "drawStatusEffects", at = @At("HEAD"), cancellable = true)
    private void hideStatusEffects(DrawContext context, Collection<StatusEffectInstance> effects, int x, int y, int width, int height, int mouseX, CallbackInfo ci) {
        if (TeslaMapsConfig.get().hideInventoryEffects) {
            ci.cancel();
        }
    }
}
