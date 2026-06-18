package com.teslamaps.mixin;

import com.teslamaps.config.TeslaMapsConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.world.effect.MobEffectInstance;

@Mixin(EffectsInInventory.class)
public class InventoryEffectsMixin {

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void hideStatusEffects(GuiGraphicsExtractor context, Collection<MobEffectInstance> effects, int x, int y, int width, int height, int mouseX, CallbackInfo ci) {
        if (TeslaMapsConfig.get().hideInventoryEffects) {
            ci.cancel();
        }
    }
}
