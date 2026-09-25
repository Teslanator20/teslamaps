package com.teslamaps.mixin;

import com.teslamaps.features.croesus.CroesusProfit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class CroesusSlotMixin {
    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void teslamaps$croesusSlot(GuiGraphicsExtractor ctx, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (CroesusProfit.styleCroesusSlot(ctx, slot)) ci.cancel();
        else if (com.teslamaps.features.FancyCraftingTable.hideSlot(this, slot)) ci.cancel();
        else if (com.teslamaps.features.AbiphoneHelper.hideSlot(slot)) ci.cancel();
        else if (com.teslamaps.features.BetterContainers.hideSlot(this, slot)) ci.cancel();
    }
}
