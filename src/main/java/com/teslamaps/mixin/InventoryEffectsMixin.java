package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;

@Mixin(EffectsInInventory.class)
public class InventoryEffectsMixin {

    // 26.1.2: EffectsInInventory.renderEffects(...) -> extractRenderState(GuiGraphicsExtractor, int, int)
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void hideStatusEffects(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        if (TeslaMapsConfig.get().hideInventoryEffects) {
            ci.cancel();
        }
    }
}
