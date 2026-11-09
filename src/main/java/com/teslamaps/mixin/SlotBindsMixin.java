package com.teslamaps.mixin;

import com.teslamaps.features.SlotBinds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class SlotBindsMixin {

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"), cancellable = true)
    private void teslamaps$slotBindsClick(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (SlotBinds.onSlotClick()) ci.cancel();
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"))
    private void teslamaps$slotBindsLine(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        SlotBinds.renderLine(graphics, mouseX, mouseY);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void teslamaps$slotBindsClose(CallbackInfo ci) {
        SlotBinds.onScreenClose();
    }
}
