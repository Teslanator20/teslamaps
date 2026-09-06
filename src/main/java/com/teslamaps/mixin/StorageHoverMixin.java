package com.teslamaps.mixin;

import com.teslamaps.features.StorageOverlay;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class StorageHoverMixin {
    @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", at = @At("HEAD"), cancellable = true)
    private void teslamaps$remapHover(Slot slot, double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (StorageOverlay.active()) cir.setReturnValue(StorageOverlay.isOverSlot(slot, mouseX, mouseY));
    }

    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void teslamaps$notOutside(double mouseX, double mouseY, int left, int top, CallbackInfoReturnable<Boolean> cir) {
        if (StorageOverlay.active()) cir.setReturnValue(false);
    }
}
