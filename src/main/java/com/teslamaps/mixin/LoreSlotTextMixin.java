package com.teslamaps.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class LoreSlotTextMixin {

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void teslamaps$loreSlotText(GuiGraphicsExtractor ctx, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        com.teslamaps.features.ItemLoreExtras.drawSlot(ctx, slot);
        com.teslamaps.features.CrownValueOverlay.drawSlot(ctx, slot);
    }
}
