package com.teslamaps.mixin;

import com.teslamaps.features.ContainerTweaks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public class InventoryLabelsMixin {

    @Inject(method = "extractLabels", at = @At("HEAD"), cancellable = true)
    private void teslamaps$hideCraftingText(GuiGraphicsExtractor g, int mx, int my, CallbackInfo ci) {
        if (ContainerTweaks.hideCraftingText()) ci.cancel();
    }
}
